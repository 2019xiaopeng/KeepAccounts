package com.qcb.keepaccounts.data.agent

import com.qcb.keepaccounts.domain.agent.AgentErrorCode
import com.qcb.keepaccounts.domain.agent.AgentToolCallRecord
import com.qcb.keepaccounts.domain.agent.AgentToolName
import com.qcb.keepaccounts.domain.agent.AgentToolStatus
import org.json.JSONObject
import java.io.File

class AgentJsonlMirrorStore(
    private val jsonlFile: File,
) {

    init {
        val parent = jsonlFile.parentFile
        if (parent != null && !parent.exists()) {
            parent.mkdirs()
        }
        if (!jsonlFile.exists()) {
            jsonlFile.createNewFile()
        }
    }

    @Synchronized
    fun append(record: AgentToolCallRecord) {
        val line = JSONObject().apply {
            put("requestId", record.requestId)
            put("runId", record.runId)
            put("stepIndex", record.stepIndex)
            put("toolName", record.toolName.name)
            put("argsJson", record.argsJson)
            put("resultJson", record.resultJson)
            put("status", record.status.name)
            put("errorCode", record.errorCode?.name)
            put("errorMessage", record.errorMessage)
            put("latencyMs", record.latencyMs)
            put("timestamp", record.timestamp)
        }.toString()
        jsonlFile.appendText(line + "\n")
    }

    fun readByRequestId(requestId: String): List<AgentToolCallRecord> {
        if (!jsonlFile.exists()) return emptyList()

        return jsonlFile.useLines { lines ->
            lines.mapNotNull { line ->
                runCatching {
                    val json = JSONObject(line)
                    if (json.optString("requestId") != requestId) {
                        return@runCatching null
                    }
                    AgentToolCallRecord(
                        requestId = json.getString("requestId"),
                        runId = json.optString("runId", requestId),
                        stepIndex = json.getInt("stepIndex"),
                        toolName = AgentToolName.valueOf(json.getString("toolName")),
                        argsJson = json.optString("argsJson", "{}"),
                        resultJson = json.optString("resultJson", "{}"),
                        status = AgentToolStatus.valueOf(json.getString("status")),
                        errorCode = json.optString("errorCode").takeIf { it.isNotBlank() }?.let(AgentErrorCode::valueOf),
                        errorMessage = json.optString("errorMessage").takeIf { it.isNotBlank() },
                        latencyMs = json.optLong("latencyMs", 0L),
                        timestamp = json.optLong("timestamp", 0L),
                    )
                }.getOrNull()
            }.filterNotNull().toList()
        }.sortedBy { it.stepIndex }
    }
}
