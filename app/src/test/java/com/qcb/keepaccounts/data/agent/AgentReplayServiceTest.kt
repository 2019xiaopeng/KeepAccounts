package com.qcb.keepaccounts.data.agent

import com.qcb.keepaccounts.data.local.dao.AgentRunDao
import com.qcb.keepaccounts.data.local.dao.AgentToolCallDao
import com.qcb.keepaccounts.data.local.entity.AgentRunEntity
import com.qcb.keepaccounts.data.local.entity.AgentToolCallEntity
import com.qcb.keepaccounts.domain.agent.AgentErrorCode
import com.qcb.keepaccounts.domain.agent.AgentRequestContext
import com.qcb.keepaccounts.domain.agent.AgentRunStatus
import com.qcb.keepaccounts.domain.agent.AgentToolCallRecord
import com.qcb.keepaccounts.domain.agent.AgentToolName
import com.qcb.keepaccounts.domain.agent.AgentToolStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class AgentReplayServiceTest {

    @Test
    fun replayFromDatabase_reconstructsOrderedCallSequence() {
        val runDao = FakeAgentRunDao()
        val callDao = FakeAgentToolCallDao()
        val mirrorFile = File.createTempFile("agent-replay", ".jsonl")
        mirrorFile.deleteOnExit()
        val mirrorStore = AgentJsonlMirrorStore(mirrorFile)
        val logger = RoomAgentRunLogger(runDao, callDao, mirrorStore)
        val replayService = AgentReplayService(runDao, callDao, mirrorStore)

        kotlinx.coroutines.runBlocking {
            logger.markRunStarted(
                AgentRequestContext(
                    requestId = "req-db-1",
                    idempotencyKey = "idem-db-1",
                    userInput = "把咖啡改成12",
                    startedAt = 100L,
                ),
            )
            logger.appendToolCall(
                AgentToolCallRecord(
                    requestId = "req-db-1",
                    runId = "req-db-1",
                    stepIndex = 1,
                    toolName = AgentToolName.PREVIEW_ACTIONS,
                    argsJson = "{\"action\":\"update\"}",
                    resultJson = "{\"matched\":1}",
                    status = AgentToolStatus.SUCCESS,
                    latencyMs = 12L,
                    timestamp = 111L,
                ),
            )
            logger.appendToolCall(
                AgentToolCallRecord(
                    requestId = "req-db-1",
                    runId = "req-db-1",
                    stepIndex = 2,
                    toolName = AgentToolName.UPDATE_TRANSACTIONS,
                    argsJson = "{\"amount\":12}",
                    resultJson = "{\"transactionId\":9}",
                    status = AgentToolStatus.SUCCESS,
                    latencyMs = 18L,
                    timestamp = 129L,
                ),
            )
            logger.markRunFinished(
                requestId = "req-db-1",
                status = AgentRunStatus.SUCCESS,
                finishedAt = 150L,
            )

            val trace = replayService.replayFromDatabase("req-db-1")
            assertNotNull(trace)
            assertEquals("req-db-1", trace?.requestId)
            assertEquals(2, trace?.calls?.size)
            assertEquals(AgentToolName.PREVIEW_ACTIONS, trace?.calls?.get(0)?.toolName)
            assertEquals(AgentToolName.UPDATE_TRANSACTIONS, trace?.calls?.get(1)?.toolName)
            assertEquals("{\"amount\":12}", trace?.calls?.get(1)?.argsJson)
        }
    }

    @Test
    fun replayFromMirrorOrDatabase_fallsBackToJsonlWhenDbCallsMissing() {
        val runDao = FakeAgentRunDao()
        val callDao = FakeAgentToolCallDao()
        val mirrorFile = File.createTempFile("agent-mirror", ".jsonl")
        mirrorFile.deleteOnExit()
        val mirrorStore = AgentJsonlMirrorStore(mirrorFile)
        val replayService = AgentReplayService(runDao, callDao, mirrorStore)

        kotlinx.coroutines.runBlocking {
            runDao.insertRun(
                AgentRunEntity(
                    requestId = "req-mirror-1",
                    idempotencyKey = "idem-mirror-1",
                    userInput = "最近一周统计",
                    status = AgentRunStatus.SUCCESS.name,
                    startedAt = 300L,
                    endedAt = 380L,
                ),
            )
            mirrorStore.append(
                AgentToolCallRecord(
                    requestId = "req-mirror-1",
                    runId = "req-mirror-1",
                    stepIndex = 1,
                    toolName = AgentToolName.QUERY_SPENDING_STATS,
                    argsJson = "{\"window\":\"last7days\"}",
                    resultJson = "{\"total\":88.0}",
                    status = AgentToolStatus.SUCCESS,
                    errorCode = AgentErrorCode.INVALID_TIME_WINDOW,
                    errorMessage = null,
                    latencyMs = 20L,
                    timestamp = 330L,
                ),
            )

            val trace = replayService.replayFromMirrorOrDatabase("req-mirror-1")
            assertNotNull(trace)
            assertEquals(1, trace?.calls?.size)
            assertEquals(AgentToolName.QUERY_SPENDING_STATS, trace?.calls?.first()?.toolName)
            assertTrue(trace?.calls?.first()?.argsJson?.contains("last7days") == true)
        }
    }
}

private class FakeAgentRunDao : AgentRunDao {
    private val runs = mutableMapOf<String, AgentRunEntity>()

    override suspend fun insertRun(run: AgentRunEntity) {
        runs[run.requestId] = run
    }

    override suspend fun getRunByRequestId(requestId: String): AgentRunEntity? = runs[requestId]

    override suspend fun finishRun(
        requestId: String,
        status: String,
        endedAt: Long,
        errorCode: String?,
        errorMessage: String?,
    ) {
        val current = runs[requestId] ?: return
        runs[requestId] = current.copy(
            status = status,
            endedAt = endedAt,
            errorCode = errorCode,
            errorMessage = errorMessage,
        )
    }
}

private class FakeAgentToolCallDao : AgentToolCallDao {
    private val calls = mutableListOf<AgentToolCallEntity>()

    override suspend fun insertToolCall(call: AgentToolCallEntity) {
        calls += call
    }

    override suspend fun getCallsByRequestId(requestId: String): List<AgentToolCallEntity> {
        return calls.filter { it.requestId == requestId }.sortedBy { it.stepIndex }
    }
}
