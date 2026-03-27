package com.qcb.keepaccounts.data.remote.siliconflow

import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatRequestDto
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.Header
import retrofit2.http.POST

interface SiliconFlowApi {
    @POST("chat/completions")
    suspend fun streamChatCompletion(
        @Header("Authorization") authorization: String,
        @Body request: SiliconFlowChatRequestDto,
    ): Response<ResponseBody>
}
