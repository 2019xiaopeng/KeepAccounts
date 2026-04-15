package com.qcb.keepaccounts.domain.agent

import com.qcb.keepaccounts.domain.contract.AiChatRequest
import java.util.Locale

class ModelRoutingPolicy(
    private val enabled: Boolean = false,
    liteRolloutPercent: Int = 0,
    liteMinConfidence: Double = 0.80,
    private val plannerLiteMaxInputLength: Int = 36,
    private val chatLiteMaxInputLength: Int = 48,
) {

    private val rolloutPercent = liteRolloutPercent.coerceIn(0, 100)
    private val minConfidence = liteMinConfidence.coerceIn(0.0, 1.0)

    fun decidePlannerTier(input: PlannerInputV2): ModelRouteDecision {
        if (!enabled) return ModelRouteDecision(ModelTier.PRO, "router_disabled")

        val text = input.userInput
        if (isHighRiskOrComplex(text)) {
            return ModelRouteDecision(ModelTier.PRO, "high_risk_or_complex")
        }

        if (!isLiteRolloutHit(input.requestId)) {
            return ModelRouteDecision(ModelTier.PRO, "rollout_bucket_excluded")
        }

        if (!isPlannerLiteCandidate(text)) {
            return ModelRouteDecision(ModelTier.PRO, "not_lite_candidate")
        }

        return ModelRouteDecision(ModelTier.LITE, "lite_candidate")
    }

    fun decideChatTier(request: AiChatRequest): ModelRouteDecision {
        if (!enabled) return ModelRouteDecision(ModelTier.PRO, "router_disabled")

        val userInput = request.messages
            .lastOrNull { it.role.equals("user", ignoreCase = true) }
            ?.content
            .orEmpty()

        if (isHighRiskOrComplex(userInput)) {
            return ModelRouteDecision(ModelTier.PRO, "high_risk_or_complex")
        }

        val bucketSeed = if (userInput.isBlank()) request.model else userInput
        if (!isLiteRolloutHit(bucketSeed)) {
            return ModelRouteDecision(ModelTier.PRO, "rollout_bucket_excluded")
        }

        if (userInput.length > chatLiteMaxInputLength || hasRetrySignal(userInput)) {
            return ModelRouteDecision(ModelTier.PRO, "not_lite_candidate")
        }

        return ModelRouteDecision(ModelTier.LITE, "lite_candidate")
    }

    fun shouldEscalateLitePlannerResult(
        plan: IntentPlanV2?,
        issues: List<AgentValidationIssue>,
    ): Boolean {
        return resolveLitePlannerEscalationReason(plan, issues) != null
    }

    fun resolveLitePlannerEscalationReason(
        plan: IntentPlanV2?,
        issues: List<AgentValidationIssue>,
    ): String? {
        if (plan == null) return "lite_plan_null"
        if (plan.confidence < minConfidence) return "lite_confidence_low"
        if (issues.isNotEmpty()) return "lite_validation_issues"

        if (plan.intent == PlannerIntentType.DELETE_TRANSACTIONS) return "delete_intent_high_risk"
        if (plan.intent == PlannerIntentType.UNKNOWN) return "unknown_intent"
        if (plan.targetMode != PlannerTargetMode.SINGLE) return "non_single_target_mode"

        return null
    }

    private fun isPlannerLiteCandidate(input: String): Boolean {
        if (input.isBlank()) return false
        if (input.length > plannerLiteMaxInputLength) return false
        if (hasMultiStepSignal(input)) return false
        if (hasRetrySignal(input)) return false
        return true
    }

    private fun isHighRiskOrComplex(input: String): Boolean {
        val normalized = normalize(input)
        if (normalized.isBlank()) return false

        if (deleteSignals.any { normalized.contains(it) }) return true
        if (batchRegex.containsMatchIn(normalized)) return true
        if (complexTimeSignals.any { normalized.contains(it) }) return true
        if (statsSignals.any { normalized.contains(it) }) return true
        return false
    }

    private fun hasMultiStepSignal(input: String): Boolean {
        val normalized = normalize(input)
        return multiStepSignals.any { normalized.contains(it) }
    }

    private fun hasRetrySignal(input: String): Boolean {
        val normalized = normalize(input)
        return retrySignals.any { normalized.contains(it) }
    }

    private fun isLiteRolloutHit(seed: String): Boolean {
        if (rolloutPercent <= 0) return false
        if (rolloutPercent >= 100) return true

        val bucket = ((seed.hashCode().toLong() and 0x7FFFFFFF) % 100L).toInt()
        return bucket < rolloutPercent
    }

    private fun normalize(input: String): String {
        return input.trim().lowercase(Locale.ROOT)
    }

    companion object {
        private val deleteSignals = listOf(
            "删除",
            "删掉",
            "删了",
            "清空",
            "移除",
            "抹掉",
            "作废",
            "撤销",
        )

        private val complexTimeSignals = listOf(
            "上周",
            "上个月",
            "上月",
            "大前天",
            "工作日",
            "周一",
            "周二",
            "周三",
            "周四",
            "周五",
            "周六",
            "周日",
        )

        private val statsSignals = listOf(
            "统计",
            "趋势",
            "占比",
            "分析",
            "总计",
            "图表",
        )

        private val multiStepSignals = listOf(
            "然后",
            "并且",
            "同时",
            "顺便",
            "再把",
            "另外",
        )

        private val retrySignals = listOf(
            "还是不对",
            "不对",
            "重试",
            "再来一次",
            "再改",
            "上次",
        )

        private val batchRegex = Regex(
            pattern = "(批量|全部|所有|前\\s*\\d+\\s*笔|\\d+\\s*笔|top\\s*\\d+|set|top_n)",
            options = setOf(RegexOption.IGNORE_CASE),
        )
    }
}
