package com.qcb.keepaccounts.domain.agent

import java.util.concurrent.ConcurrentHashMap

data class PlannerRoutingTrace(
    val requestId: String,
    val initialTier: ModelTier,
    val finalTier: ModelTier,
    val routeReason: String,
    val escalatedToPro: Boolean,
    val liteConfidence: Double? = null,
    val escalationReason: String? = null,
)

data class ChatRoutingTrace(
    val requestId: String,
    val tier: ModelTier,
    val routeReason: String,
    val modelUsed: String,
)

class AgentRoutingTraceStore {
    private val plannerTraces = ConcurrentHashMap<String, PlannerRoutingTrace>()
    private val chatTraces = ConcurrentHashMap<String, ChatRoutingTrace>()

    fun putPlannerTrace(trace: PlannerRoutingTrace) {
        plannerTraces[trace.requestId] = trace
    }

    fun putChatTrace(trace: ChatRoutingTrace) {
        chatTraces[trace.requestId] = trace
    }

    fun peekPlannerTrace(requestId: String): PlannerRoutingTrace? {
        return plannerTraces[requestId]
    }

    fun peekChatTrace(requestId: String): ChatRoutingTrace? {
        return chatTraces[requestId]
    }

    fun clear(requestId: String) {
        plannerTraces.remove(requestId)
        chatTraces.remove(requestId)
    }
}
