package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.domain.agent.AgentPlanner
import com.qcb.keepaccounts.domain.agent.IntentPlanV2
import com.qcb.keepaccounts.domain.agent.ModelRoutingPolicy
import com.qcb.keepaccounts.domain.agent.ModelTier
import com.qcb.keepaccounts.domain.agent.PlannerInputV2
import com.qcb.keepaccounts.domain.agent.PlannerOutputValidator

class TieredPlannerRouter(
    private val litePlanner: AgentPlanner,
    private val proPlanner: AgentPlanner,
    private val policy: ModelRoutingPolicy,
    private val validator: PlannerOutputValidator = PlannerOutputValidator(),
) : AgentPlanner {

    override suspend fun plan(input: PlannerInputV2): IntentPlanV2? {
        val decision = policy.decidePlannerTier(input)
        if (decision.tier == ModelTier.PRO) {
            return proPlanner.plan(input)
        }

        val litePlan = litePlanner.plan(input)
        val issues = litePlan?.let(validator::validate).orEmpty()
        if (policy.shouldEscalateLitePlannerResult(litePlan, issues)) {
            return proPlanner.plan(input)
        }

        return litePlan
    }
}
