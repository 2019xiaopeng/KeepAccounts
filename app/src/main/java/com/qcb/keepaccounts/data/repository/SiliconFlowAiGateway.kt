package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.data.remote.siliconflow.model.toSiliconFlowRequestDto
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiReceiptDraft
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.flow
import org.json.JSONArray
import org.json.JSONObject
import retrofit2.HttpException
import java.net.SocketTimeoutException
import java.net.UnknownHostException

class SiliconFlowAiGateway(
    private val api: SiliconFlowApi,
) : AiChatGateway {

    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        val body = api.streamChatCompletions(request.toSiliconFlowRequestDto())

        body.use { response ->
            val source = response.source()
            var inDataSection = false
            val dataBuffer = StringBuilder()

            while (!source.exhausted()) {
                val line = source.readUtf8Line() ?: continue
                if (!line.startsWith("data:")) continue

                val payload = line.removePrefix("data:").trim()
                if (payload.isBlank()) continue
                if (payload == "[DONE]") break

                val delta = extractDelta(payload) ?: continue
                var remaining = delta

                while (remaining.isNotEmpty()) {
                    if (!inDataSection) {
                        val start = remaining.indexOf("<DATA>")
                        if (start < 0) {
                            emit(AiStreamEvent.TextDelta(remaining))
                            remaining = ""
                        } else {
                            val plainText = remaining.substring(0, start)
                            if (plainText.isNotEmpty()) {
                                emit(AiStreamEvent.TextDelta(plainText))
                            }
                            inDataSection = true
                            remaining = remaining.substring(start + "<DATA>".length)
                        }
                    } else {
                        val end = remaining.indexOf("</DATA>")
                        if (end < 0) {
                            dataBuffer.append(remaining)
                            remaining = ""
                        } else {
                            dataBuffer.append(remaining.substring(0, end))
                            parseReceiptDraft(dataBuffer.toString())?.let { draft ->
                                emit(AiStreamEvent.ReceiptParsed(draft))
                            }
                            dataBuffer.clear()
                            inDataSection = false
                            remaining = remaining.substring(end + "</DATA>".length)
                        }
                    }
                }
            }
        }

        emit(AiStreamEvent.Completed)
    }.catch { throwable ->
        val message = when (throwable) {
            is HttpException -> throwable.toUserReadableMessage()
            is UnknownHostException -> "网络不可用，请检查网络后重试。"
            is SocketTimeoutException -> "AI 服务响应超时，请稍后重试。"
            else -> throwable.message ?: "AI 请求失败"
        }
        emit(AiStreamEvent.Error(message))
    }

    private fun extractDelta(payload: String): String? {
        return runCatching {
            val root = JSONObject(payload)
            val choices = root.optJSONArray("choices") ?: return null
            if (choices.length() == 0) return null

            val choice = choices.optJSONObject(0) ?: return null
            val delta = choice.optJSONObject("delta") ?: return null
            val content = delta.opt("content") ?: return null
            if (content == JSONObject.NULL) return null

            when (content) {
                is String -> content.takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
                is JSONArray -> content.toDeltaText().takeIf { it.isNotBlank() }
                else -> content.toString().takeIf { it.isNotBlank() && !it.equals("null", ignoreCase = true) }
            }
        }.getOrNull()
    }

    private fun parseReceiptDraft(raw: String): AiReceiptDraft? {
        if (raw.isBlank()) return null

        return runCatching {
            val json = JSONObject(raw.trim())
            AiReceiptDraft(
                isReceipt = json.optBoolean("isReceipt", false),
                action = json.optString("action", "create"),
                amount = json.optDoubleOrNull("amount"),
                category = json.optStringOrNull("category"),
                desc = json.optStringOrNull("desc"),
                date = json.optStringOrNull("date"),
            )
        }.getOrNull()
    }
}

private fun JSONObject.optDoubleOrNull(key: String): Double? {
    if (!has(key) || isNull(key)) return null
    return optDouble(key)
}

private fun JSONObject.optStringOrNull(key: String): String? {
    if (!has(key) || isNull(key)) return null
    return optString(key).takeIf { it.isNotBlank() }
}

private fun JSONArray.toDeltaText(): String {
    val text = StringBuilder()
    for (index in 0 until length()) {
        val item = opt(index)
        when (item) {
            is String -> text.append(item)
            is JSONObject -> {
                val value = item.optString("text")
                if (value.isNotBlank()) {
                    text.append(value)
                }
            }
        }
    }
    return text.toString()
}

private fun HttpException.toUserReadableMessage(): String {
    val code = code()
    val detail = runCatching { response()?.errorBody()?.string().orEmpty() }
        .getOrDefault("")
        .replace("\n", " ")
        .trim()

    return when (code) {
        401 -> "AI 鉴权失败，请检查 local.properties 的 SILICONFLOW_API_KEY，并确认 SILICONFLOW_API_URL 使用 https://api.siliconflow.cn/v1。"
        403 -> "AI 服务拒绝访问，请确认 API Key 权限。"
        429 -> "AI 请求过于频繁，请稍后再试。"
        in 500..599 -> "AI 服务暂时不可用，请稍后重试。"
        else -> {
            if (detail.isNotBlank()) {
                "AI 请求失败：${detail.take(120)}"
            } else {
                "AI 请求失败，请稍后重试。"
            }
        }
    }
}
