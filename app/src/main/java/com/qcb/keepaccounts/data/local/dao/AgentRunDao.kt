package com.qcb.keepaccounts.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qcb.keepaccounts.data.local.entity.AgentRunEntity

@Dao
interface AgentRunDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRun(run: AgentRunEntity)

    @Query("SELECT * FROM agent_runs WHERE requestId = :requestId LIMIT 1")
    suspend fun getRunByRequestId(requestId: String): AgentRunEntity?

    @Query(
        """
        UPDATE agent_runs
        SET status = :status,
            endedAt = :endedAt,
            errorCode = :errorCode,
            errorMessage = :errorMessage
        WHERE requestId = :requestId
        """,
    )
    suspend fun finishRun(
        requestId: String,
        status: String,
        endedAt: Long,
        errorCode: String?,
        errorMessage: String?,
    )
}
