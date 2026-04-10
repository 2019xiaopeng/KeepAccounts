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
    fun validate_reportsSingleCreateConstraintViolation() {
        val issues = validator.validate(
            IntentPlanV2(
                intent = PlannerIntentType.CREATE_TRANSACTIONS,
                confidence = 0.9,
                createItems = listOf(
                    PreviewActionItem(
                        action = "create",
                        amount = 20.0,
                        category = "餐饮美食",
                        recordTime = null,
                    ),
                    PreviewActionItem(
                        action = "create",
                        amount = 10.0,
                        category = "交通出行",
                        recordTime = null,
                    ),
                ),
            ),
        )

        assertTrue(issues.any { it.field == "createItems.size" })
    }
}
