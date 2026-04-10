package com.qcb.keepaccounts.data.agent

import com.qcb.keepaccounts.data.local.dao.AgentQualityFeedbackDao
import com.qcb.keepaccounts.data.local.entity.AgentQualityFeedbackEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentQualityFeedbackRepositoryTest {

    @Test
    fun computeMetrics_countsAccuracyFallbackMisjudgeAndCorrection() {
        runBlocking {
            val dao = InMemoryAgentQualityFeedbackDao()
            val repository = AgentQualityFeedbackRepository(dao)
            val base = 1_700_000_000_000L

            repository.record(
                AgentQualityFeedbackInput(
                    requestId = "req-1",
                    routePath = AgentRoutePath.AGENT_PRIMARY,
                    stage = AgentQualityStage.TOOL_EXECUTION,
                    userInput = "query latest",
                    expectedAction = "QUERY_TRANSACTIONS",
                    actualAction = "QUERY_TRANSACTIONS",
                    runStatus = "SUCCESS",
                    fallbackUsed = false,
                    isMisjudged = false,
                    createdAt = base,
                ),
            )
            repository.record(
                AgentQualityFeedbackInput(
                    requestId = "req-2",
                    routePath = AgentRoutePath.PROMPT_FALLBACK,
                    stage = AgentQualityStage.INTENT_ROUTING,
                    userInput = "fallback case",
                    expectedAction = null,
                    actualAction = null,
                    runStatus = "FALLBACK",
                    fallbackUsed = true,
                    isMisjudged = false,
                    createdAt = base + 1,
                ),
            )
            repository.record(
                AgentQualityFeedbackInput(
                    requestId = "req-3",
                    routePath = AgentRoutePath.AGENT_PRIMARY,
                    stage = AgentQualityStage.TOOL_EXECUTION,
                    userInput = "misjudge case",
                    expectedAction = "WRITE",
                    actualAction = "WRITE",
                    runStatus = "SUCCESS",
                    fallbackUsed = false,
                    isMisjudged = true,
                    createdAt = base + 2,
                ),
            )

            repository.markLatestAsCorrectionSample(
                correctedByRequestId = "req-4",
                correctionInput = "not what I meant",
            )

            val metrics = repository.computeMetrics(sinceMillis = base - 1)
            assertEquals(3, metrics.totalSamples)
            assertEquals(1.0 / 3.0, metrics.accuracyRate, 0.0001)
            assertEquals(1.0 / 3.0, metrics.fallbackRate, 0.0001)
            assertEquals(1.0 / 3.0, metrics.misjudgeRate, 0.0001)
            assertEquals(1.0 / 3.0, metrics.userCorrectionRate, 0.0001)

            val latest = dao.getLatestFeedback()
            assertNotNull(latest)
            assertEquals(true, latest?.isCorrectionSample)
            assertEquals("req-4", latest?.correctedByRequestId)
            assertTrue(latest?.metadataJson?.contains("user_correction") == true)
        }
    }

    @Test
    fun computeMetrics_returnsZeroRatesWhenNoSamples() {
        runBlocking {
            val repository = AgentQualityFeedbackRepository(InMemoryAgentQualityFeedbackDao())
            val metrics = repository.computeMetrics(sinceMillis = 0L)

            assertEquals(0, metrics.totalSamples)
            assertEquals(0.0, metrics.accuracyRate, 0.0001)
            assertEquals(0.0, metrics.fallbackRate, 0.0001)
            assertEquals(0.0, metrics.misjudgeRate, 0.0001)
            assertEquals(0.0, metrics.userCorrectionRate, 0.0001)
        }
    }
}

private class InMemoryAgentQualityFeedbackDao : AgentQualityFeedbackDao {
    private val items = mutableListOf<AgentQualityFeedbackEntity>()
    private var nextId = 1L

    override suspend fun insertFeedback(feedback: AgentQualityFeedbackEntity) {
        items += feedback.copy(id = nextId++)
    }

    override suspend fun listByRequestId(requestId: String): List<AgentQualityFeedbackEntity> {
        return items.filter { it.requestId == requestId }
            .sortedByDescending { it.createdAt }
    }

    override suspend fun getLatestFeedback(): AgentQualityFeedbackEntity? {
        return items.maxByOrNull { it.createdAt }
    }

    override suspend fun listSince(sinceMillis: Long): List<AgentQualityFeedbackEntity> {
        return items.filter { it.createdAt >= sinceMillis }
    }

    override suspend fun markCorrectionSample(
        requestId: String,
        correctedByRequestId: String,
        metadataJson: String?,
    ) {
        val index = items.indexOfLast { it.requestId == requestId }
        if (index < 0) return
        val current = items[index]
        items[index] = current.copy(
            isCorrectionSample = true,
            correctedByRequestId = correctedByRequestId,
            metadataJson = metadataJson,
        )
    }
}
