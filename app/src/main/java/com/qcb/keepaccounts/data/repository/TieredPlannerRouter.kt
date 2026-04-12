package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.domain.agent.AgentPlanner
import com.qcb.keepaccounts.domain.agent.AgentRoutingTraceStore
import com.qcb.keepaccounts.domain.agent.IntentPlanV2
import com.qcb.keepaccounts.domain.agent.ModelRoutingPolicy
import com.qcb.keepaccounts.domain.agent.ModelTier
import com.qcb.keepaccounts.domain.agent.PlannerRoutingTrace
import com.qcb.keepaccounts.domain.agent.PlannerInputV2
import com.qcb.keepaccounts.domain.agent.PlannerOutputValidator

class TieredPlannerRouter(
    private val litePlanner: AgentPlanner,
    private val proPlanner: AgentPlanner,
    private val policy: ModelRoutingPolicy,
    private val validator: PlannerOutputValidator = PlannerOutputValidator(),
    private val routingTraceStore: AgentRoutingTraceStore? = null,
) : AgentPlanner {

    override suspend fun plan(input: PlannerInputV2): IntentPlanV2? {
        val decision = policy.decidePlannerTier(input)
        if (decision.tier == ModelTier.PRO) {
            val proPlan = proPlanner.plan(input)
            routingTraceStore?.putPlannerTrace(
                PlannerRoutingTrace(
                    requestId = input.requestId,
                    initialTier = ModelTier.PRO,
                    finalTier = ModelTier.PRO,
                    routeReason = decision.reason,
                    escalatedToPro = false,
                    liteConfidence = null,
                    escalationReason = null,
                ),
            )
            return proPlan
        }

        val litePlan = litePlanner.plan(input)
        val issues = litePlan?.let(validator::validate).orEmpty()
        val escalationReason = policy.resolveLitePlannerEscalationReason(litePlan, issues)
        if (escalationReason != null) {
            val proPlan = proPlanner.plan(input)
            routingTraceStore?.putPlannerTrace(
                PlannerRoutingTrace(
                    requestId = input.requestId,
                    initialTier = ModelTier.LITE,
                    finalTier = ModelTier.PRO,
                    routeReason = decision.reason,
                    escalatedToPro = true,
                    liteConfidence = litePlan?.confidence,
                    escalationReason = escalationReason,
                ),
            )
            return proPlan
        }

        routingTraceStore?.putPlannerTrace(
            PlannerRoutingTrace(
                requestId = input.requestId,
                initialTier = ModelTier.LITE,
                finalTier = ModelTier.LITE,
                routeReason = decision.reason,
                escalatedToPro = false,
                liteConfidence = litePlan?.confidence,
                escalationReason = null,
            ),
        )
        return litePlan
    }
}
