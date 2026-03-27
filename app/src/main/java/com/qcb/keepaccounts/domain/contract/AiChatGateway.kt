package com.qcb.keepaccounts.domain.contract

import kotlinx.coroutines.flow.Flow

data class AiMessage(
    val role: String,
    val content: String,
)

data class AiChatRequest(
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.3,
    val stream: Boolean = true,
)

sealed interface AiStreamEvent {
    data class TextDelta(val content: String) : AiStreamEvent
    data class ReceiptPayload(val json: String) : AiStreamEvent
    data class SystemNotice(val message: String) : AiStreamEvent
    data class Failed(val message: String) : AiStreamEvent
    data object Completed : AiStreamEvent
}

interface AiChatGateway {
    fun streamChat(request: AiChatRequest): Flow<AiStreamEvent>
}
