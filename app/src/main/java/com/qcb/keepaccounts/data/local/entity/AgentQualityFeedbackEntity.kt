package com.qcb.keepaccounts.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "agent_quality_feedback",
    indices = [
        Index(value = ["requestId"]),
        Index(value = ["createdAt"]),
        Index(value = ["routePath"]),
        Index(value = ["isMisjudged"]),
        Index(value = ["isCorrectionSample"]),
    ],
)
data class AgentQualityFeedbackEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val requestId: String,
    val routePath: String,
    val stage: String,
    val userInput: String,
    val expectedAction: String? = null,
    val actualAction: String? = null,
    val runStatus: String,
    val fallbackUsed: Boolean,
    val isMisjudged: Boolean,
    val isCorrectionSample: Boolean,
    val correctedByRequestId: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
    val metadataJson: String? = null,
    val createdAt: Long,
)
