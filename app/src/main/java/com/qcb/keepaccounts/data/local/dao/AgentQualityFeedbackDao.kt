package com.qcb.keepaccounts.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qcb.keepaccounts.data.local.entity.AgentQualityFeedbackEntity

@Dao
interface AgentQualityFeedbackDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertFeedback(feedback: AgentQualityFeedbackEntity)

    @Query("SELECT * FROM agent_quality_feedback WHERE requestId = :requestId ORDER BY createdAt DESC")
    suspend fun listByRequestId(requestId: String): List<AgentQualityFeedbackEntity>

    @Query("SELECT * FROM agent_quality_feedback ORDER BY createdAt DESC LIMIT 1")
    suspend fun getLatestFeedback(): AgentQualityFeedbackEntity?

    @Query("SELECT * FROM agent_quality_feedback WHERE createdAt >= :sinceMillis")
    suspend fun listSince(sinceMillis: Long): List<AgentQualityFeedbackEntity>

    @Query(
        """
        UPDATE agent_quality_feedback
        SET isCorrectionSample = 1,
            correctedByRequestId = :correctedByRequestId,
            metadataJson = :metadataJson
        WHERE requestId = :requestId
        """,
    )
    suspend fun markCorrectionSample(
        requestId: String,
        correctedByRequestId: String,
        metadataJson: String?,
    )
}
