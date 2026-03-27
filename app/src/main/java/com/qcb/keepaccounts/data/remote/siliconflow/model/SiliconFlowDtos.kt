package com.qcb.keepaccounts.data.remote.siliconflow.model

import com.qcb.keepaccounts.domain.contract.AiChatRequest

/**
 * Request DTO aligned with SiliconFlow /chat/completions.
 */
data class SiliconFlowChatRequestDto(
    val model: String,
    val messages: List<SiliconFlowMessageDto>,
    val temperature: Double,
    val stream: Boolean,
)

data class SiliconFlowMessageDto(
    val role: String,
    val content: String,
)

fun AiChatRequest.toSiliconFlowRequestDto(): SiliconFlowChatRequestDto {
    return SiliconFlowChatRequestDto(
        model = model,
        messages = messages.map { SiliconFlowMessageDto(role = it.role, content = it.content) },
        temperature = temperature,
        stream = stream,
    )
}
