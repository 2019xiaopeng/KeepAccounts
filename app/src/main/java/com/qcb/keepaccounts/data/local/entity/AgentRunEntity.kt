package com.qcb.keepaccounts.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_runs",
    indices = [
        Index(value = ["requestId"], unique = true),
        Index(value = ["startedAt"]),
        Index(value = ["status"]),
    ],
)
data class AgentRunEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestId: String,
    val idempotencyKey: String,
    val userInput: String,
    val status: String,
    val startedAt: Long,
    val endedAt: Long? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)
