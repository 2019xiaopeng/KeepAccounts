package com.qcb.keepaccounts.domain.agent

import kotlin.math.abs

class AgentToolValidator {

    private val allowedWindows = setOf("today", "yesterday", "last7days", "last30days", "last12months", "custom")
    private val allowedQuerySortKeys = setOf("record_time_desc", "amount_desc")
    private val allowedStatsSortKeys = setOf("value_desc", "frequency_desc")
    private val allowedStatsGroupBy = setOf("category", "timeslot", "day", "month")
    private val allowedStatsMetrics = setOf("total_amount", "frequency", "category_ratio")

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
        val issues = mutableListOf<AgentValidationIssue>()

        if (args.window !in allowedWindows) {
            issues += AgentValidationIssue(
                field = "window",
                code = AgentErrorCode.INVALID_TIME_WINDOW,
                message = "不支持的时间窗口: ${args.window}",
            )
        }

        if (args.window == "custom") {
            val start = args.startAtMillis
            val end = args.endAtMillis
            if (start == null || end == null || start > end) {
                issues += AgentValidationIssue(
                    field = "window.customRange",
                    code = AgentErrorCode.INVALID_TIME_WINDOW,
                    message = "custom 时间窗口需要合法的 startAtMillis/endAtMillis。",
                )
            }
        }

        if (args.groupBy !in allowedStatsGroupBy) {
            issues += AgentValidationIssue(
                field = "groupBy",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "不支持的 groupBy: ${args.groupBy}",
            )
        }

        if (args.metric !in allowedStatsMetrics) {
            issues += AgentValidationIssue(
                field = "metric",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "不支持的 metric: ${args.metric}",
            )
        }

        if (args.metric == "category_ratio" && args.groupBy != "category") {
            issues += AgentValidationIssue(
                field = "metric/groupBy",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "category_ratio 只支持按 category 分组。",
            )
        }

        if (args.sortKey !in allowedStatsSortKeys) {
            issues += AgentValidationIssue(
                field = "sortKey",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "不支持的排序方式: ${args.sortKey}",
            )
        }

        if (args.topN <= 0 || args.topN > 50) {
            issues += AgentValidationIssue(
                field = "topN",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "topN 必须在 1..50 之间。",
            )
        }

        return issues
    }

    fun validateQueryArgs(args: AgentToolArgs.QueryTransactionsArgs): List<AgentValidationIssue> {
        val issues = mutableListOf<AgentValidationIssue>()

        if (args.window !in allowedWindows) {
            issues += AgentValidationIssue(
                field = "window",
                code = AgentErrorCode.INVALID_TIME_WINDOW,
                message = "不支持的时间窗口: ${args.window}",
            )
        }

        if (args.window == "custom") {
            val start = args.startAtMillis
            val end = args.endAtMillis
            if (start == null || end == null || start > end) {
                issues += AgentValidationIssue(
                    field = "window.customRange",
                    code = AgentErrorCode.INVALID_TIME_WINDOW,
                    message = "custom 时间窗口需要合法的 startAtMillis/endAtMillis。",
                )
            }
        }

        if (args.sortKey !in allowedQuerySortKeys) {
            issues += AgentValidationIssue(
                field = "sortKey",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "不支持的排序方式: ${args.sortKey}",
            )
        }

        if (args.limit <= 0 || args.limit > 100) {
            issues += AgentValidationIssue(
                field = "limit",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "limit 必须在 1..100 之间。",
            )
        }

        val min = args.filters.amountMin
        val max = args.filters.amountMax
        if (min != null && max != null && min > max) {
            issues += AgentValidationIssue(
                field = "filters.amountRange",
                code = AgentErrorCode.VALIDATION_FAILED,
                message = "查询过滤金额范围非法：min 不可大于 max。",
            )
        }

        return issues
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
