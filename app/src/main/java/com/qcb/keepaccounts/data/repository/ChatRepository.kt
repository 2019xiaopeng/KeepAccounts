package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.local.dao.ChatMessageDao
import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiMessage
import com.qcb.keepaccounts.domain.contract.AiReceiptDraft
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiChatRecord
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val transactionDao: TransactionDao,
    private val aiChatGateway: AiChatGateway,
) {

    fun observeChatRecords(): Flow<List<AiChatRecord>> {
        return chatMessageDao.observeAllMessages().map { list ->
            list.map { entity ->
                AiChatRecord(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    role = entity.role,
                    content = entity.content,
                    isReceipt = entity.isReceipt,
                    transactionId = entity.transactionId,
                )
            }
        }
    }

    suspend fun deleteMessage(messageId: Long) {
        val message = chatMessageDao.getMessageById(messageId) ?: return
        chatMessageDao.deleteMessageById(messageId)
        message.transactionId?.let { transactionDao.deleteTransactionById(it) }
    }

    suspend fun sendMessage(
        userInput: String,
        aiConfig: AiAssistantConfig,
        userName: String,
    ) {
        val now = System.currentTimeMillis()
        chatMessageDao.insertMessage(
            ChatMessageEntity(
                role = "user",
                content = userInput,
                isReceipt = false,
                timestamp = now,
            ),
        )

        val recentMessages = chatMessageDao.getRecentMessages(limit = 24).asReversed()
        val systemPrompt = buildSystemPrompt(aiConfig, userName)
        val requestMessages = buildList {
            add(AiMessage(role = "system", content = systemPrompt))
            recentMessages.forEach { message ->
                val gatewayRole = if (message.role == "assistant" || message.role == "ai") "assistant" else "user"
                add(AiMessage(role = gatewayRole, content = message.content))
            }
        }

        val textBuffer = StringBuilder()
        var parsedReceipt: AiReceiptDraft? = null

        aiChatGateway.streamReply(
            AiChatRequest(
                model = "Pro/moonshotai/Kimi-K2.5",
                messages = requestMessages,
                temperature = 0.3,
                stream = true,
            ),
        ).collect { event ->
            when (event) {
                is AiStreamEvent.TextDelta -> textBuffer.append(event.text)
                is AiStreamEvent.ReceiptParsed -> parsedReceipt = event.draft
                is AiStreamEvent.Error -> {
                    if (textBuffer.isNotEmpty()) textBuffer.append("\n")
                    textBuffer.append("[AI错误] ${event.message}")
                }

                AiStreamEvent.Completed -> Unit
            }
        }

        var linkedTransactionId: Long? = null
        parsedReceipt?.let { draft ->
            linkedTransactionId = tryCreateTransaction(draft, textBuffer.toString())
        }

        val finalAssistantText = textBuffer.toString().trim().ifBlank {
            if (linkedTransactionId != null) "已为你记录这笔账单。" else "收到，我在。"
        }

        chatMessageDao.insertMessage(
            ChatMessageEntity(
                role = "assistant",
                content = finalAssistantText,
                isReceipt = linkedTransactionId != null,
                transactionId = linkedTransactionId,
                timestamp = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun tryCreateTransaction(
        draft: AiReceiptDraft,
        assistantText: String,
    ): Long? {
        if (!draft.isReceipt) return null

        val rawAmount = draft.amount ?: return null
        val amount = abs(rawAmount)
        if (amount <= 0.0) return null

        val type = resolveTransactionType(draft, rawAmount)
        val category = draft.category?.takeIf { it.isNotBlank() }
            ?: if (type == 1) "收入" else "其他"
        val remark = draft.desc?.takeIf { it.isNotBlank() }
            ?: assistantText.take(24)

        val recordTimestamp = parseReceiptDateOrNow(draft.date)
        val now = System.currentTimeMillis()

        return transactionDao.insertTransaction(
            TransactionEntity(
                type = type,
                amount = amount,
                categoryName = category,
                categoryIcon = "",
                remark = remark,
                recordTimestamp = recordTimestamp,
                createdTimestamp = now,
            ),
        )
    }

    private fun resolveTransactionType(draft: AiReceiptDraft, rawAmount: Double): Int {
        if (rawAmount < 0) return 0

        val text = listOfNotNull(draft.category, draft.desc).joinToString(" ")
        val incomeHints = listOf("工资", "收入", "奖金", "报销", "兼职", "红包", "收款")
        return if (incomeHints.any { text.contains(it) }) 1 else 0
    }

    private fun parseReceiptDateOrNow(rawDate: String?): Long {
        if (rawDate.isNullOrBlank()) return System.currentTimeMillis()

        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            sdf.parse(rawDate)?.time ?: System.currentTimeMillis()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun buildSystemPrompt(
        aiConfig: AiAssistantConfig,
        userName: String,
    ): String {
        val toneText = when (aiConfig.tone.name) {
            "TSUNDERE" -> "傲娇毒舌"
            "RATIONAL" -> "理智管家"
            else -> "贴心治愈"
        }

        return """
            你是一个名为${aiConfig.name}的AI记账管家，性格是${toneText}。用户的名字是${userName}。
            你的任务是陪用户聊天，并在用户提到消费或收入时，提取记账信息。
            如果用户的话包含记账信息，你必须在回复最末尾严格输出 <DATA>...</DATA> JSON：
            {
              "isReceipt": true,
              "action": "create",
              "amount": 30.0,
              "category": "交通",
              "desc": "打车",
              "date": "2026-03-27"
            }
            如果只是普通闲聊，不要输出 <DATA> 标签。
        """.trimIndent()
    }
}
