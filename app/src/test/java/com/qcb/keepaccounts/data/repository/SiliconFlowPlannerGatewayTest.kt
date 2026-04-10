package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowAssistantMessageDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatRequestDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatResponseDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChoiceDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowToolCallDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowToolCallFunctionDto
import com.qcb.keepaccounts.domain.agent.PlannerInputV2
import com.qcb.keepaccounts.domain.agent.PlannerIntentType
import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class SiliconFlowPlannerGatewayTest {

    @Test
    fun plan_parsesToolCallArgumentsToIntentPlan() {
        runBlocking {
            val api = FakePlannerApi(
                response = SiliconFlowChatResponseDto(
                    choices = listOf(
                        SiliconFlowChoiceDto(
                            message = SiliconFlowAssistantMessageDto(
                                toolCalls = listOf(
                                    SiliconFlowToolCallDto(
                                        function = SiliconFlowToolCallFunctionDto(
                                            name = "submit_intent_plan_v2",
                                            arguments =
                                                """
                                                {
                                                  "intent": "query_transactions",
                                                  "confidence": 0.91,
                                                  "queryArgs": {
                                                    "window": "last7days",
                                                    "sortKey": "record_time_desc",
                                                    "limit": 2,
                                                    "filters": {"keyword": "打车"}
                                                  }
                                                }
                                                """.trimIndent(),
                                        ),
                                    ),
                                ),
                            ),
                        ),
                    ),
                ),
            )
            val gateway = SiliconFlowPlannerGateway(api)

            val plan = gateway.plan(
                PlannerInputV2(
                    requestId = "req-1",
                    userInput = "帮我查下最近打车",
                    nowMillis = 1_700_000_000_000,
                    timezoneId = "Asia/Shanghai",
                ),
            )

            assertNotNull(plan)
            assertEquals(PlannerIntentType.QUERY_TRANSACTIONS, plan?.intent)
            assertEquals(0.91, plan?.confidence ?: 0.0, 0.0001)
            assertEquals("last7days", plan?.queryArgs?.window)
            assertEquals(2, plan?.queryArgs?.limit)
            assertEquals("打车", plan?.queryArgs?.filters?.keyword)
        }
    }

    @Test
    fun plan_fallbacksToContentJsonWhenNoToolCall() {
        runBlocking {
            val api = FakePlannerApi(
                response = SiliconFlowChatResponseDto(
                    choices = listOf(
                        SiliconFlowChoiceDto(
                            message = SiliconFlowAssistantMessageDto(
                                content = """
                                {
                                  "intent": "query_spending_stats",
                                  "confidence": 0.86,
                                  "statsArgs": {
                                    "window": "last30days",
                                    "groupBy": "category",
                                    "metric": "total_amount",
                                    "topN": 3
                                  }
                                }
                                """.trimIndent(),
                            ),
                        ),
                    ),
                ),
            )
            val gateway = SiliconFlowPlannerGateway(api)

            val plan = gateway.plan(
                PlannerInputV2(
                    requestId = "req-2",
                    userInput = "统计最近一个月消费",
                    nowMillis = 1_700_000_000_000,
                    timezoneId = "Asia/Shanghai",
                ),
            )

            assertNotNull(plan)
            assertEquals(PlannerIntentType.QUERY_SPENDING_STATS, plan?.intent)
            assertEquals("category", plan?.statsArgs?.groupBy)
            assertEquals(3, plan?.statsArgs?.topN)
        }
    }
}

private class FakePlannerApi(
    private val response: SiliconFlowChatResponseDto,
) : SiliconFlowApi {
    override suspend fun chatCompletions(request: SiliconFlowChatRequestDto): SiliconFlowChatResponseDto {
        return response
    }

    override suspend fun streamChatCompletions(request: SiliconFlowChatRequestDto): ResponseBody {
        return "data: [DONE]\n".toResponseBody("text/event-stream".toMediaType())
    }
}
