package com.qcb.keepaccounts.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_tool_calls",
    indices = [
        Index(value = ["requestId", "stepIndex"], unique = true),
        Index(value = ["toolName"]),
        Index(value = ["timestamp"]),
    ],
)
data class AgentToolCallEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestId: String,
    val runId: String,
    val stepIndex: Int,
    val toolName: String,
    val argsJson: String,
    val resultJson: String,
    val status: String,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val latencyMs: Long,
    val timestamp: Long,
)
