package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SiliconFlowAiGatewayTest {

    @Test
    fun streamReply_parsesMultipleDataBlocksInOrder() {
        runBlocking {
            val gateway = SiliconFlowAiGateway(
                api = FakeSiliconFlowApi(
                    sseBody = sseResponse(
                        """先帮你整理。""",
                        """<DATA>{"isReceipt":true,"action":"create","amount":3.5,"category":"购物消费","desc":"杨桃"}</DATA>""",
                        """<DATA>{"isReceipt":true,"action":"create","amount":18,"category":"餐饮美食","desc":"中餐"}</DATA>""",
                    ),
                ),
            )

            val events = gateway.streamReply(fakeRequest()).toList()
            val receiptEvents = events.filterIsInstance<AiStreamEvent.ReceiptParsed>()

            assertEquals(2, receiptEvents.size)
            assertEquals(1, receiptEvents[0].drafts.size)
            assertEquals("杨桃", receiptEvents[0].drafts[0].desc)
            assertEquals(1, receiptEvents[1].drafts.size)
            assertEquals("中餐", receiptEvents[1].drafts[0].desc)
            assertTrue(events.last() is AiStreamEvent.Completed)
        }
    }

    @Test
    fun streamReply_parsesItemsPayloadIntoSingleReceiptEventWithOrderedDrafts() {
        runBlocking {
            val gateway = SiliconFlowAiGateway(
                api = FakeSiliconFlowApi(
                    sseBody = sseResponse(
                        """
                        <DATA>{
                          "isReceipt": true,
                          "action": "create",
                          "items": [
                            {"amount": 3.5, "category": "购物消费", "desc": "杨桃"},
                            {"amount": 18, "category": "餐饮美食", "desc": "中餐"},
                            {"amount": 16.9, "category": "餐饮美食", "desc": "奶茶加蛋糕"}
                          ]
                        }</DATA>
                        """.trimIndent(),
                    ),
                ),
            )

            val events = gateway.streamReply(fakeRequest()).toList()
            val receiptEvents = events.filterIsInstance<AiStreamEvent.ReceiptParsed>()

            assertEquals(1, receiptEvents.size)
            assertEquals(3, receiptEvents[0].drafts.size)
            assertEquals(listOf("杨桃", "中餐", "奶茶加蛋糕"), receiptEvents[0].drafts.map { it.desc })
            assertTrue(events.last() is AiStreamEvent.Completed)
        }
    }

    @Test
    fun streamReply_ignoresNoteThinkAndKeepsTextAndReceiptOrder() {
        runBlocking {
            val gateway = SiliconFlowAiGateway(
                api = FakeSiliconFlowApi(
                    sseBody = sseResponse(
                        """收到啦。<note>内部注释</note>我来整理。""",
                        """<DATA>{"isReceipt":true,"action":"create","amount":22,"category":"餐饮美食","desc":"牛肉粉丝汤"}</DATA>""",
                        """<think>隐藏思考</think>已经记好啦。""",
                    ),
                ),
            )

            val events = gateway.streamReply(fakeRequest()).toList()
            val textEvents = events.filterIsInstance<AiStreamEvent.TextDelta>()
            val receiptEvents = events.filterIsInstance<AiStreamEvent.ReceiptParsed>()

            assertEquals(1, receiptEvents.size)
            assertEquals(1, receiptEvents[0].drafts.size)
            assertEquals("牛肉粉丝汤", receiptEvents[0].drafts[0].desc)
            assertEquals("收到啦。我来整理。已经记好啦。", textEvents.joinToString("") { it.text })
            assertTrue(events.last() is AiStreamEvent.Completed)
        }
    }

    private fun fakeRequest(): AiChatRequest {
        return AiChatRequest(model = "test", messages = emptyList())
    }

    private fun sseResponse(vararg deltas: String): String {
        return buildString {
            deltas.forEach { delta ->
                append("""data: {"choices":[{"delta":{"content":${delta.toJsonString()}}}]}""")
                append('\n')
            }
            append("data: [DONE]\n")
        }
    }
}

private class FakeSiliconFlowApi(
    private val sseBody: String,
) : SiliconFlowApi {
    override suspend fun streamChatCompletions(request: com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatRequestDto): ResponseBody {
        return sseBody.toResponseBody("text/event-stream".toMediaType())
    }
}

private fun String.toJsonString(): String {
    return "\"" + replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n") + "\""
}
