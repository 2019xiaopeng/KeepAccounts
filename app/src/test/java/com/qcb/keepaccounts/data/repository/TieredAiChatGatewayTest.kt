package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.domain.agent.ModelRoutingPolicy
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiMessage
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TieredAiChatGatewayTest {

    @Test
    fun streamReply_lowRiskMessage_routesToLiteGateway() {
        runBlocking {
            val liteGateway = RecordingAiGateway("lite")
            val proGateway = RecordingAiGateway("pro")
            val gateway = TieredAiChatGateway(
                liteGateway = liteGateway,
                proGateway = proGateway,
                policy = ModelRoutingPolicy(
                    enabled = true,
                    liteRolloutPercent = 100,
                    liteMinConfidence = 0.80,
                ),
                liteModel = "Qwen/Qwen2.5-7B-Instruct",
                proModel = "deepseek-ai/DeepSeek-V3",
            )

            val events = gateway.streamReply(
                request = requestOf("帮我记一笔午饭 20 元"),
            ).toList()

            assertEquals(1, liteGateway.requests.size)
            assertEquals(0, proGateway.requests.size)
            assertEquals("Qwen/Qwen2.5-7B-Instruct", liteGateway.requests.first().model)
            assertTrue(events.last() is AiStreamEvent.Completed)
        }
    }

    @Test
    fun streamReply_deleteMessage_routesToProGateway() {
        runBlocking {
            val liteGateway = RecordingAiGateway("lite")
            val proGateway = RecordingAiGateway("pro")
            val gateway = TieredAiChatGateway(
                liteGateway = liteGateway,
                proGateway = proGateway,
                policy = ModelRoutingPolicy(
                    enabled = true,
                    liteRolloutPercent = 100,
                    liteMinConfidence = 0.80,
                ),
                liteModel = "Qwen/Qwen2.5-7B-Instruct",
                proModel = "deepseek-ai/DeepSeek-V3",
            )

            val events = gateway.streamReply(
                request = requestOf("删除昨天前两笔打车"),
            ).toList()

            assertEquals(0, liteGateway.requests.size)
            assertEquals(1, proGateway.requests.size)
            assertEquals("deepseek-ai/DeepSeek-V3", proGateway.requests.first().model)
            assertTrue(events.last() is AiStreamEvent.Completed)
        }
    }

    @Test
    fun streamReply_routerDisabled_alwaysUsesProGateway() {
        runBlocking {
            val liteGateway = RecordingAiGateway("lite")
            val proGateway = RecordingAiGateway("pro")
            val gateway = TieredAiChatGateway(
                liteGateway = liteGateway,
                proGateway = proGateway,
                policy = ModelRoutingPolicy(
                    enabled = false,
                    liteRolloutPercent = 100,
                    liteMinConfidence = 0.80,
                ),
                liteModel = "Qwen/Qwen2.5-7B-Instruct",
                proModel = "deepseek-ai/DeepSeek-V3",
            )

            gateway.streamReply(requestOf("随便聊聊今天心情")).toList()

            assertEquals(0, liteGateway.requests.size)
            assertEquals(1, proGateway.requests.size)
            assertEquals("deepseek-ai/DeepSeek-V3", proGateway.requests.first().model)
        }
    }

    private fun requestOf(userInput: String): AiChatRequest {
        return AiChatRequest(
            model = "deepseek-ai/DeepSeek-V3",
            messages = listOf(AiMessage(role = "user", content = userInput)),
            stream = true,
        )
    }
}

private class RecordingAiGateway(
    private val marker: String,
) : AiChatGateway {
    val requests = mutableListOf<AiChatRequest>()

    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        requests += request
        emit(AiStreamEvent.TextDelta(marker))
        emit(AiStreamEvent.Completed)
    }
}
