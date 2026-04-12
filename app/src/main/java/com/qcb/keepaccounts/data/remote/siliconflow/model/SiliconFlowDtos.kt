package com.qcb.keepaccounts.data.remote.siliconflow.model

import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.squareup.moshi.Json

/**
 * Request DTO aligned with SiliconFlow /chat/completions.
 */
data class SiliconFlowChatRequestDto(
    val model: String,
    val messages: List<SiliconFlowMessageDto>,
    val temperature: Double,
    val stream: Boolean,
    val tools: List<SiliconFlowToolDto>? = null,
    @field:Json(name = "tool_choice")
    val toolChoice: SiliconFlowToolChoiceDto? = null,
    @field:Json(name = "max_tokens")
    val maxTokens: Int? = null,
)

data class SiliconFlowMessageDto(
    val role: String,
    val content: String,
)

data class SiliconFlowToolDto(
    val type: String = "function",
    val function: SiliconFlowToolSchemaDto,
)

data class SiliconFlowToolSchemaDto(
    val name: String,
    val description: String,
    val parameters: Map<String, Any?>,
)

data class SiliconFlowToolChoiceDto(
    val type: String = "function",
    val function: SiliconFlowToolChoiceFunctionDto,
)

data class SiliconFlowToolChoiceFunctionDto(
    val name: String,
)

data class SiliconFlowChatResponseDto(
    val choices: List<SiliconFlowChoiceDto> = emptyList(),
)

data class SiliconFlowChoiceDto(
    val message: SiliconFlowAssistantMessageDto? = null,
)

data class SiliconFlowAssistantMessageDto(
    val content: String? = null,
    @field:Json(name = "tool_calls")
    val toolCalls: List<SiliconFlowToolCallDto>? = null,
)

data class SiliconFlowToolCallDto(
    val id: String? = null,
    val type: String? = null,
    val function: SiliconFlowToolCallFunctionDto? = null,
)

data class SiliconFlowToolCallFunctionDto(
    val name: String? = null,
    val arguments: String? = null,
)

fun AiChatRequest.toSiliconFlowRequestDto(): SiliconFlowChatRequestDto {
    return SiliconFlowChatRequestDto(
        model = model,
        messages = messages.map { SiliconFlowMessageDto(role = it.role, content = it.content) },
        temperature = temperature,
        stream = stream,
    )
}
