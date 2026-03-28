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
import org.json.JSONObject

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
        emit(AiStreamEvent.Error(throwable.message ?: "AI 请求失败"))
    }

    private fun extractDelta(payload: String): String? {
        val root = JSONObject(payload)
        val choices = root.optJSONArray("choices") ?: return null
        if (choices.length() == 0) return null

        val choice = choices.optJSONObject(0) ?: return null
        val delta = choice.optJSONObject("delta") ?: return null
        return delta.optString("content").takeIf { it.isNotEmpty() }
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
