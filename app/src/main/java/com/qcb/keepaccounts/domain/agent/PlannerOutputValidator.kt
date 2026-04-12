package com.qcb.keepaccounts.domain.agent

class PlannerOutputValidator(
    private val toolValidator: AgentToolValidator = AgentToolValidator(),
) {

    fun validate(plan: IntentPlanV2): List<AgentValidationIssue> {
        val issues = mutableListOf<AgentValidationIssue>()

        val confidence = plan.confidence
        if (confidence < 0.0 || confidence > 1.0) {
            issues += AgentValidationIssue(
                field = "confidence",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "planner confidence 必须在 0..1 之间。",
            )
        }

        when (plan.intent) {
            PlannerIntentType.QUERY_TRANSACTIONS -> {
                val queryArgs = plan.queryArgs
                    ?: return issues + AgentValidationIssue(
                        field = "queryArgs",
                        code = AgentErrorCode.VALIDATION_FAILED,
                        message = "query_transactions 缺少 queryArgs。",
                    )
                issues += toolValidator.validateQueryArgs(queryArgs)
            }

            PlannerIntentType.QUERY_SPENDING_STATS -> {
                val statsArgs = plan.statsArgs
                    ?: return issues + AgentValidationIssue(
                        field = "statsArgs",
                        code = AgentErrorCode.VALIDATION_FAILED,
                        message = "query_spending_stats 缺少 statsArgs。",
                    )
                issues += toolValidator.validateStatsArgs(statsArgs)
            }

            PlannerIntentType.CREATE_TRANSACTIONS -> {
                val writeItems = resolveWriteItems(plan)
                if (writeItems.isEmpty()) {
                    issues += AgentValidationIssue(
                        field = "writeItems",
                        code = AgentErrorCode.VALIDATION_FAILED,
                        message = "create_transactions 缺少 writeItems。",
                    )
                }

                val createItems = writeItems.map { item ->
                    if (item.action.equals("create", ignoreCase = true)) item else item.copy(action = "create")
                }
                issues += toolValidator.validateCreateArgs(AgentToolArgs.CreateTransactionsArgs(items = createItems))
            }

            PlannerIntentType.UPDATE_TRANSACTIONS -> {
                val writeItems = resolveWriteItems(plan)
                if (writeItems.isEmpty()) {
                    issues += AgentValidationIssue(
                        field = "writeItems",
                        code = AgentErrorCode.VALIDATION_FAILED,
                        message = "update_transactions 缺少 writeItems。",
                    )
                }

                writeItems.forEachIndexed { index, item ->
                    val hasPatch = item.amount != null || !item.category.isNullOrBlank() || !item.recordTime.isNullOrBlank() || !item.date.isNullOrBlank() || !item.desc.isNullOrBlank()
                    val hasTarget = item.transactionId != null || !item.category.isNullOrBlank() || !item.date.isNullOrBlank() || !item.desc.isNullOrBlank() || item.amount != null
                    if (!hasPatch) {
                        issues += AgentValidationIssue(
                            field = "writeItems[$index].patch",
                            code = AgentErrorCode.VALIDATION_FAILED,
                            message = "update_transactions 缺少可更新字段。",
                        )
                    }
                    if (!hasTarget) {
                        issues += AgentValidationIssue(
                            field = "writeItems[$index].target",
                            code = AgentErrorCode.VALIDATION_FAILED,
                            message = "update_transactions 缺少目标定位信息。",
                        )
                    }
                }
            }

            PlannerIntentType.DELETE_TRANSACTIONS -> {
                val writeItems = resolveWriteItems(plan)
                if (writeItems.isEmpty()) {
                    issues += AgentValidationIssue(
                        field = "writeItems",
                        code = AgentErrorCode.VALIDATION_FAILED,
                        message = "delete_transactions 缺少 writeItems。",
                    )
                }

                writeItems.forEachIndexed { index, item ->
                    val hasTarget = item.transactionId != null || !item.category.isNullOrBlank() || !item.date.isNullOrBlank() || !item.desc.isNullOrBlank() || item.amount != null
                    if (!hasTarget) {
                        issues += AgentValidationIssue(
                            field = "writeItems[$index].target",
                            code = AgentErrorCode.VALIDATION_FAILED,
                            message = "delete_transactions 缺少目标定位信息。",
                        )
                    }
                }
            }

            else -> Unit
        }

        return issues
    }

    private fun resolveWriteItems(plan: IntentPlanV2): List<PreviewActionItem> {
        return if (plan.writeItems.isNotEmpty()) {
            plan.writeItems
        } else {
            plan.createItems
        }
    }
}
