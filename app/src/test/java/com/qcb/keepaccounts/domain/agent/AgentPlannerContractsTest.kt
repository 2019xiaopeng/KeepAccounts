package com.qcb.keepaccounts.domain.agent

import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentPlannerContractsTest {

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    @Test
    fun intentPlanV2_queryArgs_roundTripSerialization() {
        val adapter = moshi.adapter(IntentPlanV2::class.java)
        val source = IntentPlanV2(
            intent = PlannerIntentType.QUERY_TRANSACTIONS,
            confidence = 0.91,
            targetMode = PlannerTargetMode.SET,
            riskLevel = PlannerRiskLevel.MEDIUM,
            needsConfirmation = false,
            missingSlots = listOf("time"),
            queryArgs = AgentToolArgs.QueryTransactionsArgs(
                filters = TransactionFilter(
                    keyword = "打车",
                    amountMin = 10.0,
                ),
                window = "last7days",
                sortKey = "amount_desc",
                limit = 3,
                startAtMillis = 1000L,
                endAtMillis = 2000L,
            ),
        )

        val json = adapter.toJson(source)
        val restored = adapter.fromJson(json)

        assertTrue(json.contains("QUERY_TRANSACTIONS"))
        assertNotNull(restored)
        assertEquals(PlannerIntentType.QUERY_TRANSACTIONS, restored?.intent)
        assertEquals(0.91, restored?.confidence ?: 0.0, 0.0001)
        assertEquals(PlannerTargetMode.SET, restored?.targetMode)
        assertEquals(PlannerRiskLevel.MEDIUM, restored?.riskLevel)
        assertEquals("打车", restored?.queryArgs?.filters?.keyword)
        assertEquals(10.0, restored?.queryArgs?.filters?.amountMin ?: 0.0, 0.0001)
        assertEquals("last7days", restored?.queryArgs?.window)
        assertEquals(3, restored?.queryArgs?.limit)
        assertEquals(1000L, restored?.queryArgs?.startAtMillis)
        assertEquals(2000L, restored?.queryArgs?.endAtMillis)
    }

    @Test
    fun intentPlanV2_statsArgs_roundTripSerialization() {
        val adapter = moshi.adapter(IntentPlanV2::class.java)
        val source = IntentPlanV2(
            intent = PlannerIntentType.QUERY_SPENDING_STATS,
            confidence = 0.87,
            targetMode = PlannerTargetMode.TOP_N,
            riskLevel = PlannerRiskLevel.LOW,
            needsConfirmation = false,
            missingSlots = emptyList(),
            statsArgs = AgentToolArgs.QuerySpendingStatsArgs(
                window = "last30days",
                groupBy = "category",
                metric = "total_amount",
                sortKey = "value_desc",
                topN = 5,
                startAtMillis = 3000L,
                endAtMillis = 9000L,
            ),
        )

        val json = adapter.toJson(source)
        val restored = adapter.fromJson(json)

        assertTrue(json.contains("QUERY_SPENDING_STATS"))
        assertNotNull(restored)
        assertEquals(PlannerIntentType.QUERY_SPENDING_STATS, restored?.intent)
        assertEquals(PlannerTargetMode.TOP_N, restored?.targetMode)
        assertEquals("category", restored?.statsArgs?.groupBy)
        assertEquals("total_amount", restored?.statsArgs?.metric)
        assertEquals(5, restored?.statsArgs?.topN)
        assertEquals(3000L, restored?.statsArgs?.startAtMillis)
        assertEquals(9000L, restored?.statsArgs?.endAtMillis)
    }

    @Test
    fun envelope_roundTripSerialization() {
        val callAdapter = moshi.adapter(ToolCallEnvelope::class.java)
        val observationAdapter = moshi.adapter(ObservationEnvelope::class.java)

        val call = ToolCallEnvelope(
            requestId = "req-1",
            stepIndex = 1,
            toolName = AgentToolName.QUERY_TRANSACTIONS,
            argsJson = "{\"limit\":1}",
            plannedAt = 123L,
        )
        val observation = ObservationEnvelope(
            requestId = "req-1",
            toolName = AgentToolName.QUERY_TRANSACTIONS,
            status = AgentToolStatus.SUCCESS,
            resultJson = "{\"count\":1}",
            observedAt = 456L,
        )

        val callRestored = callAdapter.fromJson(callAdapter.toJson(call))
        val observationRestored = observationAdapter.fromJson(observationAdapter.toJson(observation))

        assertEquals(AgentToolName.QUERY_TRANSACTIONS, callRestored?.toolName)
        assertEquals(1, callRestored?.stepIndex)
        assertEquals(AgentToolStatus.SUCCESS, observationRestored?.status)
        assertEquals("{\"count\":1}", observationRestored?.resultJson)
    }

    @Test
    fun noOpPlanner_returnsNull() {
        val result = runBlocking {
            NoOpAgentPlanner.plan(
                PlannerInputV2(
                    requestId = "req-2",
                    userInput = "查询最近一笔",
                    nowMillis = 123456L,
                    timezoneId = "Asia/Shanghai",
                ),
            )
        }

        assertNull(result)
    }
}
