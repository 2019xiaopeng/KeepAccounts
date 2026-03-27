package com.qcb.keepaccounts.data.remote.siliconflow

import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatRequestDto
import okhttp3.ResponseBody
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.Streaming

interface SiliconFlowApi {
    @Streaming
    @POST("chat/completions")
    suspend fun streamChatCompletions(
        @Body request: SiliconFlowChatRequestDto,
    ): ResponseBody
}
