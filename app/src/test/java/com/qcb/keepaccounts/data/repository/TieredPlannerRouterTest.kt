package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.domain.agent.AgentPlanner
import com.qcb.keepaccounts.domain.agent.AgentRoutingTraceStore
import com.qcb.keepaccounts.domain.agent.IntentPlanV2
import com.qcb.keepaccounts.domain.agent.ModelRoutingPolicy
import com.qcb.keepaccounts.domain.agent.ModelTier
import com.qcb.keepaccounts.domain.agent.PlannerInputV2
import com.qcb.keepaccounts.domain.agent.PlannerIntentType
import com.qcb.keepaccounts.domain.agent.PreviewActionItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class TieredPlannerRouterTest {

    @Test
    fun plan_highRiskInput_routesDirectlyToProPlanner() {
        runBlocking {
            val litePlanner = RecordingPlanner { validCreatePlan(confidence = 0.95) }
            val proPlan = validDeletePlan(confidence = 0.92)
            val proPlanner = RecordingPlanner { proPlan }
            val router = TieredPlannerRouter(
                litePlanner = litePlanner,
                proPlanner = proPlanner,
                policy = ModelRoutingPolicy(
                    enabled = true,
                    liteRolloutPercent = 100,
                    liteMinConfidence = 0.80,
                ),
            )

            val result = router.plan(
                PlannerInputV2(
                    requestId = "req-high-risk",
                    userInput = "删除昨天前两笔打车",
                    nowMillis = 1_700_000_000_000,
                    timezoneId = "Asia/Shanghai",
                ),
            )

            assertEquals(0, litePlanner.callCount)
            assertEquals(1, proPlanner.callCount)
            assertSame(proPlan, result)
        }
    }

    @Test
    fun plan_liteLowConfidence_escalatesToProPlanner() {
        runBlocking {
            val litePlanner = RecordingPlanner { validCreatePlan(confidence = 0.65) }
            val proPlan = validCreatePlan(confidence = 0.93)
            val proPlanner = RecordingPlanner { proPlan }
            val router = TieredPlannerRouter(
                litePlanner = litePlanner,
                proPlanner = proPlanner,
                policy = ModelRoutingPolicy(
                    enabled = true,
                    liteRolloutPercent = 100,
                    liteMinConfidence = 0.80,
                ),
            )

            val result = router.plan(
                PlannerInputV2(
                    requestId = "req-lite-escalate",
                    userInput = "午饭 20 元",
                    nowMillis = 1_700_000_000_000,
                    timezoneId = "Asia/Shanghai",
                ),
            )

            assertEquals(1, litePlanner.callCount)
            assertEquals(1, proPlanner.callCount)
            assertSame(proPlan, result)
        }
    }

    @Test
    fun plan_liteValidResult_keepsLiteResult() {
        runBlocking {
            val litePlan = validCreatePlan(confidence = 0.91)
            val litePlanner = RecordingPlanner { litePlan }
            val proPlanner = RecordingPlanner { validCreatePlan(confidence = 0.97) }
            val router = TieredPlannerRouter(
                litePlanner = litePlanner,
                proPlanner = proPlanner,
                policy = ModelRoutingPolicy(
                    enabled = true,
                    liteRolloutPercent = 100,
                    liteMinConfidence = 0.80,
                ),
            )

            val result = router.plan(
                PlannerInputV2(
                    requestId = "req-lite-pass",
                    userInput = "早餐 18",
                    nowMillis = 1_700_000_000_000,
                    timezoneId = "Asia/Shanghai",
                ),
            )

            assertEquals(1, litePlanner.callCount)
            assertEquals(0, proPlanner.callCount)
            assertSame(litePlan, result)
        }
    }

    @Test
    fun plan_liteEscalation_writesRoutingTrace() {
        runBlocking {
            val traceStore = AgentRoutingTraceStore()
            val litePlanner = RecordingPlanner { validCreatePlan(confidence = 0.64) }
            val proPlan = validCreatePlan(confidence = 0.92)
            val proPlanner = RecordingPlanner { proPlan }
            val router = TieredPlannerRouter(
                litePlanner = litePlanner,
                proPlanner = proPlanner,
                policy = ModelRoutingPolicy(
                    enabled = true,
                    liteRolloutPercent = 100,
                    liteMinConfidence = 0.80,
                ),
                routingTraceStore = traceStore,
            )

            router.plan(
                PlannerInputV2(
                    requestId = "req-route-trace",
                    userInput = "早餐 18 元",
                    nowMillis = 1_700_000_000_000,
                    timezoneId = "Asia/Shanghai",
                ),
            )

            val trace = traceStore.peekPlannerTrace("req-route-trace")
            assertNotNull(trace)
            assertEquals(ModelTier.LITE, trace?.initialTier)
            assertEquals(ModelTier.PRO, trace?.finalTier)
            assertTrue(trace?.escalatedToPro == true)
            assertEquals("lite_confidence_low", trace?.escalationReason)
        }
    }

    private fun validCreatePlan(confidence: Double): IntentPlanV2 {
        return IntentPlanV2(
            intent = PlannerIntentType.CREATE_TRANSACTIONS,
            confidence = confidence,
            writeItems = listOf(
                PreviewActionItem(
                    action = "create",
                    amount = 20.0,
                    category = "餐饮美食",
                    recordTime = null,
                    date = "today",
                    desc = "午饭",
                ),
            ),
        )
    }

    private fun validDeletePlan(confidence: Double): IntentPlanV2 {
        return IntentPlanV2(
            intent = PlannerIntentType.DELETE_TRANSACTIONS,
            confidence = confidence,
            writeItems = listOf(
                PreviewActionItem(
                    action = "delete",
                    amount = null,
                    category = "交通出行",
                    recordTime = null,
                    date = "yesterday",
                    desc = "打车",
                ),
            ),
        )
    }
}

private class RecordingPlanner(
    private val block: suspend (PlannerInputV2) -> IntentPlanV2?,
) : AgentPlanner {
    var callCount: Int = 0

    override suspend fun plan(input: PlannerInputV2): IntentPlanV2? {
        callCount += 1
        return block(input)
    }
}
