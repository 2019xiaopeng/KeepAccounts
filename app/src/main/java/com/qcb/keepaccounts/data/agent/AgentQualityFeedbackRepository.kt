package com.qcb.keepaccounts.data.agent

import com.qcb.keepaccounts.data.local.dao.AgentQualityFeedbackDao
import com.qcb.keepaccounts.data.local.entity.AgentQualityFeedbackEntity
import org.json.JSONObject

enum class AgentRoutePath {
    AGENT_PRIMARY,
    PLANNER_PRIMARY,
    PROMPT_FALLBACK,
    FALLBACK_BLOCKED,
}

enum class AgentQualityStage {
    INTENT_ROUTING,
    PLANNER_SHADOW,
    TOOL_EXECUTION,
    STYLE_RENDERING,
}

data class AgentQualityFeedbackInput(
    val requestId: String,
    val routePath: AgentRoutePath,
    val stage: AgentQualityStage,
    val userInput: String,
    val expectedAction: String? = null,
    val actualAction: String? = null,
    val runStatus: String,
    val fallbackUsed: Boolean,
    val isMisjudged: Boolean,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val metadataJson: String? = null,
    val createdAt: Long,
)

data class AgentQualityMetrics(
    val totalSamples: Int,
    val accuracyRate: Double,
    val fallbackRate: Double,
    val misjudgeRate: Double,
    val userCorrectionRate: Double,
)

class AgentQualityFeedbackRepository(
    private val dao: AgentQualityFeedbackDao,
) {

    suspend fun record(input: AgentQualityFeedbackInput) {
        dao.insertFeedback(
            AgentQualityFeedbackEntity(
                requestId = input.requestId,
                routePath = input.routePath.name,
                stage = input.stage.name,
                userInput = input.userInput,
                expectedAction = input.expectedAction,
                actualAction = input.actualAction,
                runStatus = input.runStatus,
                fallbackUsed = input.fallbackUsed,
                isMisjudged = input.isMisjudged,
                isCorrectionSample = false,
                errorCode = input.errorCode,
                errorMessage = input.errorMessage,
                metadataJson = input.metadataJson,
                createdAt = input.createdAt,
            ),
        )
    }

    suspend fun markLatestAsCorrectionSample(
        correctedByRequestId: String,
        correctionInput: String,
    ) {
        val latest = dao.getLatestFeedback() ?: return
        val metadata = JSONObject().apply {
            put("markedReason", "user_correction")
            put("correctionInput", correctionInput)
            put("markedAt", System.currentTimeMillis())
        }.toString()
        dao.markCorrectionSample(
            requestId = latest.requestId,
            correctedByRequestId = correctedByRequestId,
            metadataJson = metadata,
        )
    }

    suspend fun computeMetrics(sinceMillis: Long): AgentQualityMetrics {
        val entries = dao.listSince(sinceMillis)
        if (entries.isEmpty()) {
            return AgentQualityMetrics(
                totalSamples = 0,
                accuracyRate = 0.0,
                fallbackRate = 0.0,
                misjudgeRate = 0.0,
                userCorrectionRate = 0.0,
            )
        }

        val total = entries.size.toDouble()
        val fallbackCount = entries.count { it.fallbackUsed }
        val misjudgedCount = entries.count { it.isMisjudged }
        val correctionCount = entries.count { it.isCorrectionSample }
        val accurateCount = entries.count { !it.fallbackUsed && !it.isMisjudged && it.runStatus == "SUCCESS" }

        return AgentQualityMetrics(
            totalSamples = entries.size,
            accuracyRate = accurateCount / total,
            fallbackRate = fallbackCount / total,
            misjudgeRate = misjudgedCount / total,
            userCorrectionRate = correctionCount / total,
        )
    }
}
