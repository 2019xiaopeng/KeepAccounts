package com.qcb.keepaccounts.domain.agent

import org.junit.Assert.assertTrue
import org.junit.Test

class PlannerOutputValidatorTest {

    private val validator = PlannerOutputValidator()

    @Test
    fun validate_reportsMissingQueryArgs() {
        val issues = validator.validate(
            IntentPlanV2(
                intent = PlannerIntentType.QUERY_TRANSACTIONS,
                confidence = 0.9,
            ),
        )

        assertTrue(issues.any { it.field == "queryArgs" })
    }

    @Test
    fun validate_reportsInvalidWindowAndTopN() {
        val issues = validator.validate(
            IntentPlanV2(
                intent = PlannerIntentType.QUERY_SPENDING_STATS,
                confidence = 0.9,
                statsArgs = AgentToolArgs.QuerySpendingStatsArgs(
                    window = "bad_window",
                    groupBy = "category",
                    metric = "total_amount",
                    topN = 99,
                ),
            ),
        )

        assertTrue(issues.any { it.field == "window" })
        assertTrue(issues.any { it.field == "topN" })
    }

    @Test
    fun validate_reportsCreateMissingAmountIssue() {
        val issues = validator.validate(
            IntentPlanV2(
                intent = PlannerIntentType.CREATE_TRANSACTIONS,
                confidence = 0.9,
                writeItems = listOf(
                    PreviewActionItem(
                        action = "create",
                        amount = null,
                        category = "餐饮美食",
                        recordTime = null,
                    ),
                ),
            ),
        )

        assertTrue(issues.any { it.field.contains("amount") })
    }

    @Test
    fun validate_reportsDeleteMissingTargetIssue() {
        val issues = validator.validate(
            IntentPlanV2(
                intent = PlannerIntentType.DELETE_TRANSACTIONS,
                confidence = 0.92,
                writeItems = listOf(
                    PreviewActionItem(
                        action = "delete",
                        amount = null,
                        category = null,
                        recordTime = null,
                        date = null,
                        desc = null,
                        transactionId = null,
                    ),
                ),
            ),
        )

        assertTrue(issues.any { it.field.contains("target") })
    }

    @Test
    fun validate_acceptsLegacyCreateItemsFallbackWhenWriteItemsEmpty() {
        val issues = validator.validate(
            IntentPlanV2(
                intent = PlannerIntentType.CREATE_TRANSACTIONS,
                confidence = 0.9,
                createItems = listOf(
                    PreviewActionItem(
                        action = "create",
                        amount = 35.0,
                        category = "餐饮美食",
                        recordTime = null,
                    ),
                ),
            ),
        )

        assertTrue(issues.isEmpty())
    }
}
