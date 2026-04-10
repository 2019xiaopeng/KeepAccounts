package com.qcb.keepaccounts.data.agent

import com.qcb.keepaccounts.data.local.dao.AgentRunDao
import com.qcb.keepaccounts.data.local.dao.AgentToolCallDao
import com.qcb.keepaccounts.domain.agent.AgentReplayTrace
import com.qcb.keepaccounts.domain.agent.AgentRunStatus
import com.qcb.keepaccounts.domain.agent.AgentToolCallRecord
import com.qcb.keepaccounts.domain.agent.AgentToolName
import com.qcb.keepaccounts.domain.agent.AgentToolStatus

class AgentReplayService(
    private val runDao: AgentRunDao,
    private val toolCallDao: AgentToolCallDao,
    private val jsonlMirrorStore: AgentJsonlMirrorStore,
) {

    suspend fun replayFromDatabase(requestId: String): AgentReplayTrace? {
        val run = runDao.getRunByRequestId(requestId) ?: return null
        val calls = toolCallDao.getCallsByRequestId(requestId).map { entity ->
            AgentToolCallRecord(
                requestId = entity.requestId,
                runId = entity.runId,
                stepIndex = entity.stepIndex,
                toolName = AgentToolName.valueOf(entity.toolName),
                argsJson = entity.argsJson,
                resultJson = entity.resultJson,
                status = AgentToolStatus.valueOf(entity.status),
                errorCode = entity.errorCode?.let { enumValueOf<com.qcb.keepaccounts.domain.agent.AgentErrorCode>(it) },
                errorMessage = entity.errorMessage,
                latencyMs = entity.latencyMs,
                timestamp = entity.timestamp,
            )
        }

        return AgentReplayTrace(
            requestId = run.requestId,
            runStatus = AgentRunStatus.valueOf(run.status),
            userInput = run.userInput,
            startedAt = run.startedAt,
            endedAt = run.endedAt,
            calls = calls,
        )
    }

    suspend fun replayFromMirrorOrDatabase(requestId: String): AgentReplayTrace? {
        val dbTrace = replayFromDatabase(requestId)
        if (dbTrace != null && dbTrace.calls.isNotEmpty()) return dbTrace

        val run = runDao.getRunByRequestId(requestId) ?: return null
        val mirrorCalls = jsonlMirrorStore.readByRequestId(requestId)
        return AgentReplayTrace(
            requestId = run.requestId,
            runStatus = AgentRunStatus.valueOf(run.status),
            userInput = run.userInput,
            startedAt = run.startedAt,
            endedAt = run.endedAt,
            calls = mirrorCalls,
        )
    }
}
