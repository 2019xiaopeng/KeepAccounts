package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatRequestDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowMessageDto
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

class SiliconFlowAiGatewayStub(
    private val api: SiliconFlowApi,
    private val apiKeyProvider: () -> String,
) : AiChatGateway {

    override fun streamChat(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        val key = apiKeyProvider()
        if (key.isBlank()) {
            emit(AiStreamEvent.Failed("SiliconFlow API Key 未配置，当前为接口预留阶段"))
            emit(AiStreamEvent.Completed)
            return@flow
        }

        // Sprint 当前阶段先固定契约并预留映射，后续会接入 SSE 实时流解析。
        val dto = SiliconFlowChatRequestDto(
            model = request.model,
            messages = request.messages.map { SiliconFlowMessageDto(role = it.role, content = it.content) },
            temperature = request.temperature,
            stream = request.stream,
        )
        api.streamChatCompletion(
            authorization = "Bearer $key",
            request = dto,
        )
        emit(AiStreamEvent.SystemNotice("SiliconFlow 接口已预留，待接入流式解析与DATA拦截"))
        emit(AiStreamEvent.Completed)
    }
}
