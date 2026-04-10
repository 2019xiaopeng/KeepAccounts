package com.qcb.keepaccounts.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentToolValidatorTest {

    private val validator = AgentToolValidator()

    @Test
    fun validateCreateArgs_reportsInvalidAmountAndEmptyCategory() {
        val issues = validator.validateCreateArgs(
            AgentToolArgs.CreateTransactionsArgs(
                items = listOf(
                    PreviewActionItem(
                        action = "create",
                        amount = 0.0,
                        category = "",
                        recordTime = null,
                    ),
                ),
            ),
        )

        val codes = issues.map { it.code }.toSet()
        assertTrue(codes.contains(AgentErrorCode.INVALID_AMOUNT))
        assertTrue(codes.contains(AgentErrorCode.EMPTY_CATEGORY))
    }

    @Test
    fun validateStatsArgs_rejectsUnsupportedWindow() {
        val issues = validator.validateStatsArgs(
            AgentToolArgs.QuerySpendingStatsArgs(
                window = "last2years",
                groupBy = "category",
                metric = "total_amount",
            ),
        )

        assertEquals(1, issues.size)
        assertEquals(AgentErrorCode.INVALID_TIME_WINDOW, issues.first().code)
    }

    @Test
    fun validateQueryArgs_rejectsInvalidSortAndLimit() {
        val issues = validator.validateQueryArgs(
            AgentToolArgs.QueryTransactionsArgs(
                filters = TransactionFilter(),
                window = "last7days",
                sortKey = "unknown_sort",
                limit = 0,
            ),
        )

        val fields = issues.map { it.field }.toSet()
        assertTrue(fields.contains("sortKey"))
        assertTrue(fields.contains("limit"))
    }

    @Test
    fun validateStatsArgs_rejectsCategoryRatioWithNonCategoryGroup() {
        val issues = validator.validateStatsArgs(
            AgentToolArgs.QuerySpendingStatsArgs(
                window = "last30days",
                groupBy = "month",
                metric = "category_ratio",
            ),
        )

        assertTrue(issues.any { it.field == "metric/groupBy" })
    }
}
