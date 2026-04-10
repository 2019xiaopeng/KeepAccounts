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
                if (plan.createItems.isEmpty()) {
                    issues += AgentValidationIssue(
                        field = "createItems",
                        code = AgentErrorCode.VALIDATION_FAILED,
                        message = "create_transactions 缺少 createItems。",
                    )
                } else if (plan.createItems.size != 1) {
                    issues += AgentValidationIssue(
                        field = "createItems.size",
                        code = AgentErrorCode.VALIDATION_FAILED,
                        message = "PhaseB 仅支持 single create，createItems 必须为 1 条。",
                    )
                }

                issues += toolValidator.validateCreateArgs(
                    AgentToolArgs.CreateTransactionsArgs(items = plan.createItems),
                )
            }

            else -> Unit
        }

        return issues
    }
}
