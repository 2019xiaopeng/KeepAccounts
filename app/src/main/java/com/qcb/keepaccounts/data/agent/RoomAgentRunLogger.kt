package com.qcb.keepaccounts.data.agent

import com.qcb.keepaccounts.data.local.dao.AgentRunDao
import com.qcb.keepaccounts.data.local.dao.AgentToolCallDao
import com.qcb.keepaccounts.data.local.entity.AgentRunEntity
import com.qcb.keepaccounts.data.local.entity.AgentToolCallEntity
import com.qcb.keepaccounts.domain.agent.AgentRequestContext
import com.qcb.keepaccounts.domain.agent.AgentRunLogger
import com.qcb.keepaccounts.domain.agent.AgentRunStatus
import com.qcb.keepaccounts.domain.agent.AgentToolCallRecord

class RoomAgentRunLogger(
    private val runDao: AgentRunDao,
    private val toolCallDao: AgentToolCallDao,
    private val jsonlMirrorStore: AgentJsonlMirrorStore,
) : AgentRunLogger {

    override suspend fun markRunStarted(context: AgentRequestContext) {
        runDao.insertRun(
            AgentRunEntity(
                requestId = context.requestId,
                idempotencyKey = context.idempotencyKey,
                userInput = context.userInput,
                status = AgentRunStatus.PROCESSING.name,
                startedAt = context.startedAt,
            ),
        )
    }

    override suspend fun appendToolCall(record: AgentToolCallRecord) {
        toolCallDao.insertToolCall(
            AgentToolCallEntity(
                requestId = record.requestId,
                runId = record.runId,
                stepIndex = record.stepIndex,
                toolName = record.toolName.name,
                argsJson = record.argsJson,
                resultJson = record.resultJson,
                status = record.status.name,
                errorCode = record.errorCode?.name,
                errorMessage = record.errorMessage,
                latencyMs = record.latencyMs,
                timestamp = record.timestamp,
            ),
        )
        jsonlMirrorStore.append(record)
    }

    override suspend fun markRunFinished(
        requestId: String,
        status: AgentRunStatus,
        finishedAt: Long,
        errorCode: com.qcb.keepaccounts.domain.agent.AgentErrorCode?,
        errorMessage: String?,
    ) {
        runDao.finishRun(
            requestId = requestId,
            status = status.name,
            endedAt = finishedAt,
            errorCode = errorCode?.name,
            errorMessage = errorMessage,
        )
    }
}
