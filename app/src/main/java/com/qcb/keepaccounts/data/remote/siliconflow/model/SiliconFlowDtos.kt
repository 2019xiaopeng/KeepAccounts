package com.qcb.keepaccounts.data.remote.siliconflow.model

data class SiliconFlowMessageDto(
    val role: String,
    val content: String,
)

data class SiliconFlowChatRequestDto(
    val model: String,
    val messages: List<SiliconFlowMessageDto>,
    val temperature: Double,
    val stream: Boolean,
)
