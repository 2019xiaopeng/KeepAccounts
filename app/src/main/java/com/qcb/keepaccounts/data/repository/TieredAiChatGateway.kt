package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.domain.agent.AgentRoutingTraceStore
import com.qcb.keepaccounts.domain.agent.ChatRoutingTrace
import com.qcb.keepaccounts.domain.agent.ModelRoutingPolicy
import com.qcb.keepaccounts.domain.agent.ModelTier
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import kotlinx.coroutines.flow.Flow

class TieredAiChatGateway(
    private val liteGateway: AiChatGateway,
    private val proGateway: AiChatGateway,
    private val policy: ModelRoutingPolicy,
    private val liteModel: String,
    private val proModel: String,
    private val routingTraceStore: AgentRoutingTraceStore? = null,
) : AiChatGateway {

    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> {
        val decision = policy.decideChatTier(request)
        val selectedModel = when (decision.tier) {
            ModelTier.LITE -> resolvedLiteModel()
            ModelTier.PRO -> resolvedProModel()
        }

        request.requestId
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.let { reqId ->
                routingTraceStore?.putChatTrace(
                    ChatRoutingTrace(
                        requestId = reqId,
                        tier = decision.tier,
                        routeReason = decision.reason,
                        modelUsed = selectedModel,
                    ),
                )
            }

        return when (decision.tier) {
            ModelTier.LITE -> liteGateway.streamReply(request.copy(model = selectedModel))
            ModelTier.PRO -> proGateway.streamReply(request.copy(model = selectedModel))
        }
    }

    private fun resolvedLiteModel(): String {
        return liteModel.trim().ifBlank { resolvedProModel() }
    }

    private fun resolvedProModel(): String {
        return proModel.trim().ifBlank { DEFAULT_PRO_MODEL }
    }

    companion object {
        private const val DEFAULT_PRO_MODEL = "deepseek-ai/DeepSeek-V3"
    }
}
