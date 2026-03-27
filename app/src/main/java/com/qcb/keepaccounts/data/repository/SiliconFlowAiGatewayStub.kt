package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * Development stub for AI streaming.
 * Replace with a SiliconFlow-backed implementation in future sprint.
 */
class SiliconFlowAiGatewayStub : AiChatGateway {

    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        emit(AiStreamEvent.TextDelta("[stub] ${request.model} 已连接，等待正式 SiliconFlow 实现"))
        emit(AiStreamEvent.Completed)
    }
}
