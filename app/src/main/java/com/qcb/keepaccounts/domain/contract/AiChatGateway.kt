package com.qcb.keepaccounts.domain.contract

import kotlinx.coroutines.flow.Flow

/**
 * Interface-first contract for AI chat streaming.
 *
 * We keep UI/business code dependent on this abstraction so the underlying
 * provider (SiliconFlow, other LLM vendor, or offline model) can be swapped
 * without changing screen or ViewModel contracts.
 */
interface AiChatGateway {
    fun streamReply(request: AiChatRequest): Flow<AiStreamEvent>
}

data class AiChatRequest(
    val model: String,
    val messages: List<AiMessage>,
    val temperature: Double = 0.3,
    val stream: Boolean = true,
    val requestId: String? = null,
)

data class AiMessage(
    val role: String,
    val content: String,
)

data class AiReceiptDraft(
    val isReceipt: Boolean,
    val action: String,
    val amount: Double?,
    val category: String?,
    val desc: String?,
    val recordTime: String?,
    val date: String?,
    val transactionId: Long? = null,
)

sealed interface AiStreamEvent {
    data class TextDelta(val text: String) : AiStreamEvent

    data class ReceiptParsed(val drafts: List<AiReceiptDraft>) : AiStreamEvent

    data class Error(val message: String) : AiStreamEvent

    data object Completed : AiStreamEvent
}
