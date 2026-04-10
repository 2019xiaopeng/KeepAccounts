package com.qcb.keepaccounts.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.qcb.keepaccounts.data.local.entity.AgentToolCallEntity

@Dao
interface AgentToolCallDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertToolCall(call: AgentToolCallEntity)

    @Query("SELECT * FROM agent_tool_calls WHERE requestId = :requestId ORDER BY stepIndex ASC")
    suspend fun getCallsByRequestId(requestId: String): List<AgentToolCallEntity>
}
