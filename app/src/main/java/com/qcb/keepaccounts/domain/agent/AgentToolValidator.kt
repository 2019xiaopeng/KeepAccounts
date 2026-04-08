package com.qcb.keepaccounts.domain.agent

import kotlin.math.abs

class AgentToolValidator {

    fun validateCreateArgs(args: AgentToolArgs.CreateTransactionsArgs): List<AgentValidationIssue> {
        if (args.items.isEmpty()) {
            return listOf(
                AgentValidationIssue(
                    field = "items",
                    code = AgentErrorCode.VALIDATION_FAILED,
                    message = "create_transactions 至少需要一条 item。",
                ),
            )
        }

        val issues = mutableListOf<AgentValidationIssue>()
        args.items.forEachIndexed { index, item ->
            val amount = item.amount
            if (amount == null || abs(amount) <= 0.0) {
                issues += AgentValidationIssue(
                    field = "items[$index].amount",
                    code = AgentErrorCode.INVALID_AMOUNT,
                    message = "金额必须是非零数字。",
                )
            }
            if (item.category.isNullOrBlank()) {
                issues += AgentValidationIssue(
                    field = "items[$index].category",
                    code = AgentErrorCode.EMPTY_CATEGORY,
                    message = "这笔账单缺少分类，补一句分类后我再试一次。",
                )
            }
        }
        return issues
    }

    fun validateUpdateArgs(args: AgentToolArgs.UpdateTransactionsArgs): List<AgentValidationIssue> {
        val issues = mutableListOf<AgentValidationIssue>()
        args.patch.amount?.let { amount ->
            if (abs(amount) <= 0.0) {
                issues += AgentValidationIssue(
                    field = "patch.amount",
                    code = AgentErrorCode.INVALID_AMOUNT,
                    message = "修改金额必须是非零数字。",
                )
            }
        }

        if (args.patch.category != null && args.patch.category.isBlank()) {
            issues += AgentValidationIssue(
                field = "patch.category",
                code = AgentErrorCode.EMPTY_CATEGORY,
                message = "修改分类不能为空。",
            )
        }

        return issues
    }

    fun validateStatsArgs(args: AgentToolArgs.QuerySpendingStatsArgs): List<AgentValidationIssue> {
        val allowedWindows = setOf("today", "yesterday", "last7days", "last30days", "last12months", "custom")
        if (args.window !in allowedWindows) {
            return listOf(
                AgentValidationIssue(
                    field = "window",
                    code = AgentErrorCode.INVALID_TIME_WINDOW,
                    message = "不支持的时间窗口: ${args.window}",
                ),
            )
        }
        return emptyList()
    }

    fun validateDeleteArgs(args: AgentToolArgs.DeleteTransactionsArgs): List<AgentValidationIssue> {
        val min = args.filters.amountMin
        val max = args.filters.amountMax
        if (min != null && max != null && min > max) {
            return listOf(
                AgentValidationIssue(
                    field = "filters.amountRange",
                    code = AgentErrorCode.VALIDATION_FAILED,
                    message = "删除过滤金额范围非法：min 不可大于 max。",
                ),
            )
        }
        return emptyList()
    }
}
