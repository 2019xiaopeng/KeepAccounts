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
import com.qcb.keepaccounts.ui.model.AiTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.map
import org.json.JSONObject
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
        var streamErrorMessage: String? = null
        val assistantMessageIds = mutableListOf<Long>()
        val streamBaseTimestamp = System.currentTimeMillis() + 1
        var lastRenderedChunks: List<String> = emptyList()
        var lastBubbleRevealAt = 0L

        aiChatGateway.streamReply(
            AiChatRequest(
                model = "Pro/moonshotai/Kimi-K2.5",
                messages = requestMessages,
                temperature = resolveTemperature(aiConfig.tone),
                stream = true,
            ),
        ).collect { event ->
            when (event) {
                is AiStreamEvent.TextDelta -> {
                    textBuffer.append(event.text)
                    val liveText = stripReceiptPayload(textBuffer.toString())
                    val liveChunks = splitAssistantReply(liveText)
                    val pacedChunks = paceStreamChunks(
                        chunks = liveChunks,
                        renderedCount = lastRenderedChunks.size,
                        lastRevealAt = lastBubbleRevealAt,
                    )
                    if (pacedChunks != lastRenderedChunks) {
                        syncAssistantReplyChunks(
                            messageIds = assistantMessageIds,
                            chunks = pacedChunks,
                            baseTimestamp = streamBaseTimestamp,
                            isReceiptLast = false,
                            receiptTransactionId = null,
                        )
                        if (pacedChunks.size > lastRenderedChunks.size) {
                            lastBubbleRevealAt = System.currentTimeMillis()
                        }
                        lastRenderedChunks = pacedChunks
                    }
                }

                is AiStreamEvent.ReceiptParsed -> parsedReceipt = event.draft
                is AiStreamEvent.Error -> {
                    streamErrorMessage = event.message.ifBlank { "AI 服务暂时不可用，请稍后重试。" }
                }

                AiStreamEvent.Completed -> Unit
            }
        }

        val rawAssistantText = textBuffer.toString().trim()
        val fallbackReceipt = extractReceiptDraftFromText(rawAssistantText)
        val resolvedReceipt = if (parsedReceipt?.isReceipt == true) {
            parsedReceipt
        } else {
            fallbackReceipt ?: parsedReceipt
        }
        val cleanedAssistantText = stripReceiptPayload(rawAssistantText)

        var linkedTransactionId: Long? = null
        resolvedReceipt?.let { draft ->
            linkedTransactionId = tryCreateTransaction(draft, cleanedAssistantText, userInput)
        }

        val finalAssistantText = if (linkedTransactionId != null) {
            cleanedAssistantText.ifBlank { "已为你记录这笔账单。" }
        } else {
            cleanedAssistantText.ifBlank {
                streamErrorMessage?.trim().takeUnless { it.isNullOrBlank() } ?: "收到，我在。"
            }
        }

        if (linkedTransactionId != null) {
            val replyChunks = ensureReceiptConversationChunks(
                chunks = splitAssistantReply(finalAssistantText),
                draft = resolvedReceipt,
            )

            val receiptMeta = resolvedReceipt?.let { buildReceiptMetaTag(it) }.orEmpty()
            val receiptText = replyChunks.lastOrNull()?.ifBlank { "已为你记录这笔账单。" } ?: "已为你记录这笔账单。"
            val receiptContent = if (receiptMeta.isBlank()) {
                receiptText
            } else {
                "$receiptText\n$receiptMeta"
            }

            val finalChunks = if (replyChunks.isEmpty()) {
                listOf(receiptContent)
            } else {
                replyChunks.dropLast(1) + receiptContent
            }

            lastRenderedChunks = revealRemainingChunksWithDelay(
                messageIds = assistantMessageIds,
                renderedChunks = lastRenderedChunks,
                targetChunks = finalChunks,
                baseTimestamp = streamBaseTimestamp,
            )

            syncAssistantReplyChunks(
                messageIds = assistantMessageIds,
                chunks = finalChunks,
                baseTimestamp = streamBaseTimestamp,
                isReceiptLast = true,
                receiptTransactionId = linkedTransactionId,
            )
            return
        }

        val finalChunks = splitAssistantReply(finalAssistantText)

        lastRenderedChunks = revealRemainingChunksWithDelay(
            messageIds = assistantMessageIds,
            renderedChunks = lastRenderedChunks,
            targetChunks = finalChunks,
            baseTimestamp = streamBaseTimestamp,
        )

        syncAssistantReplyChunks(
            messageIds = assistantMessageIds,
            chunks = finalChunks,
            baseTimestamp = streamBaseTimestamp,
            isReceiptLast = false,
            receiptTransactionId = null,
        )
    }

    private suspend fun revealRemainingChunksWithDelay(
        messageIds: MutableList<Long>,
        renderedChunks: List<String>,
        targetChunks: List<String>,
        baseTimestamp: Long,
    ): List<String> {
        if (targetChunks.size <= renderedChunks.size) return renderedChunks

        var current = renderedChunks
        for (nextCount in (renderedChunks.size + 1)..targetChunks.size) {
            if (current.isNotEmpty()) {
                delay(assistantBubbleRevealIntervalMs)
            }
            val partial = targetChunks.take(nextCount)
            syncAssistantReplyChunks(
                messageIds = messageIds,
                chunks = partial,
                baseTimestamp = baseTimestamp,
                isReceiptLast = false,
                receiptTransactionId = null,
            )
            current = partial
        }
        return current
    }

    private fun paceStreamChunks(
        chunks: List<String>,
        renderedCount: Int,
        lastRevealAt: Long,
    ): List<String> {
        if (chunks.size <= renderedCount) return chunks
        if (renderedCount == 0) return chunks.take(1)

        val elapsed = System.currentTimeMillis() - lastRevealAt
        return if (elapsed >= assistantBubbleRevealIntervalMs) {
            chunks.take(renderedCount + 1)
        } else {
            chunks.take(renderedCount)
        }
    }

    private suspend fun syncAssistantReplyChunks(
        messageIds: MutableList<Long>,
        chunks: List<String>,
        baseTimestamp: Long,
        isReceiptLast: Boolean,
        receiptTransactionId: Long?,
    ) {
        val normalizedChunks = chunks
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("收到，我在。") }

        normalizedChunks.forEachIndexed { index, chunk ->
            val isLast = index == normalizedChunks.lastIndex
            val markAsReceipt = isReceiptLast && isLast
            val transactionId = if (markAsReceipt) receiptTransactionId else null

            if (index < messageIds.size) {
                chatMessageDao.updateMessage(
                    id = messageIds[index],
                    content = chunk,
                    isReceipt = markAsReceipt,
                    transactionId = transactionId,
                )
            } else {
                val insertedId = chatMessageDao.insertMessage(
                    ChatMessageEntity(
                        role = "assistant",
                        content = chunk,
                        isReceipt = markAsReceipt,
                        transactionId = transactionId,
                        timestamp = baseTimestamp + index,
                    ),
                )
                messageIds += insertedId
            }
        }

        if (messageIds.size > normalizedChunks.size) {
            val staleIds = messageIds.subList(normalizedChunks.size, messageIds.size).toList()
            if (staleIds.isNotEmpty()) {
                chatMessageDao.deleteMessagesByIds(staleIds)
            }
            repeat(messageIds.size - normalizedChunks.size) {
                messageIds.removeAt(messageIds.lastIndex)
            }
        }
    }

    private suspend fun tryCreateTransaction(
        draft: AiReceiptDraft,
        assistantText: String,
        userInput: String,
    ): Long? {
        if (!draft.isReceipt) return null

        val rawAmount = draft.amount ?: return null
        val amount = abs(rawAmount)
        if (amount <= 0.0) return null

        val type = resolveTransactionType(draft, rawAmount)
        val category = normalizeCategory(draft.category, type)
        val remark = draft.desc?.takeIf { it.isNotBlank() }
            ?: assistantText.take(24)

        val recordTimestamp = parseReceiptDateOrNow(draft.date, userInput)
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

    private fun parseReceiptDateOrNow(rawDate: String?, userInput: String): Long {
        if (!containsExplicitDateHint(userInput)) return System.currentTimeMillis()
        if (rawDate.isNullOrBlank()) return System.currentTimeMillis()

        return runCatching {
            val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
            sdf.parse(rawDate)?.time ?: System.currentTimeMillis()
        }.getOrElse { System.currentTimeMillis() }
    }

    private fun containsExplicitDateHint(userInput: String): Boolean {
        val text = userInput.trim()
        if (text.isBlank()) return false

        if (Regex("\\d{4}[-/.]\\d{1,2}[-/.]\\d{1,2}").containsMatchIn(text)) return true
        if (Regex("\\d{1,2}月\\d{1,2}日").containsMatchIn(text)) return true

        val relativeDayHints = listOf("今天", "昨日", "昨天", "前天", "大前天")
        return relativeDayHints.any { text.contains(it) }
    }

    private fun normalizeCategory(rawCategory: String?, type: Int): String {
        val input = rawCategory.orEmpty().trim()
        if (input.isBlank()) return if (type == 1) "收入" else "其他"

        val normalized = input
            .replace("类别", "")
            .replace("分类", "")
            .replace("类", "")
            .trim()

        if (normalized in canonicalExpenseCategories) return normalized

        if (type == 1) {
            val incomeHints = listOf("工资", "薪资", "奖金", "报销", "收款", "收入", "兼职")
            if (incomeHints.any { normalized.contains(it) }) return "收入"
        }

        val mapped = when {
            normalized.contains("餐饮") || normalized.contains("美食") ||
                normalized.contains("早餐") || normalized.contains("午餐") || normalized.contains("晚餐") ||
                normalized.contains("饮品") || normalized.contains("奶茶") -> "餐饮美食"

            normalized.contains("交通") || normalized.contains("出行") || normalized.contains("打车") ||
                normalized.contains("地铁") || normalized.contains("公交") || normalized.contains("高铁") -> "交通出行"

            normalized.contains("购物") || normalized.contains("消费") || normalized.contains("网购") ||
                normalized.contains("服饰") || normalized.contains("数码") -> "购物消费"

            normalized.contains("居家") || normalized.contains("家居") || normalized.contains("房租") ||
                normalized.contains("水电") || normalized.contains("日用") -> "居家生活"

            normalized.contains("娱乐") || normalized.contains("休闲") || normalized.contains("电影") ||
                normalized.contains("游戏") || normalized.contains("旅游") -> "娱乐休闲"

            normalized.contains("医疗") || normalized.contains("健康") || normalized.contains("药") ||
                normalized.contains("医院") || normalized.contains("体检") -> "医疗健康"

            normalized.contains("人情") || normalized.contains("交际") || normalized.contains("礼") ||
                normalized.contains("社交") || normalized.contains("红包") -> "人情交际"

            else -> null
        }

        return mapped ?: if (type == 1) "收入" else "其他"
    }

    private fun buildSystemPrompt(
        aiConfig: AiAssistantConfig,
        userName: String,
    ): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val toneText = when (aiConfig.tone.name) {
            "TSUNDERE" -> "傲娇毒舌"
            "RATIONAL" -> "理智管家"
            else -> "贴心治愈"
        }

        val toneGuide = when (aiConfig.tone) {
            AiTone.HEALING -> "语气温柔、带一点关心和鼓励，像会倾听的朋友。"
            AiTone.TSUNDERE -> "语气嘴硬心软，允许轻微吐槽，但不能攻击用户，不使用脏话。"
            AiTone.RATIONAL -> "语气专业、简洁、逻辑清晰，给出明确建议。"
        }

        return """
            你是一个名为${aiConfig.name}的AI记账管家，性格是${toneText}。用户的名字是${userName}。
                        今天日期是${today}。
            ${toneGuide}
            你的任务是陪用户聊天，并在用户提到消费或收入时，提取记账信息。
            普通聊天时，尽量像真人发消息，可以分成1到3句短消息，每句不超过40字，避免长篇大论。
            当识别到记账信息时，先输出2到3句简短关怀/确认语气（分句自然），最后再输出一句已记账确认，然后再附上 <DATA> JSON。
                        如果用户没有明确提到具体日期，date 字段必须填写今天（${today}）。
            如果用户的话包含记账信息，你必须在回复最末尾严格输出 <DATA>...</DATA> JSON：
            {
              "isReceipt": true,
              "action": "create",
              "amount": 30.0,
              "category": "交通",
              "desc": "打车",
                            "date": "${today}"
            }
            如果只是普通闲聊，不要输出 <DATA> 标签。
        """.trimIndent()
    }

    private fun resolveTemperature(tone: AiTone): Double {
        return when (tone) {
            AiTone.HEALING -> 0.55
            AiTone.TSUNDERE -> 0.70
            AiTone.RATIONAL -> 0.25
        }
    }

    private fun splitAssistantReply(text: String): List<String> {
        val normalized = text
            .replace("\r", "")
            .trim()

        if (normalized.isBlank()) return listOf("收到，我在。")

        val lineParts = normalized
            .split("\n")
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val majorSentenceRegex = Regex("(?<=[。！？!?])")
        val sourceParts = if (lineParts.size > 1) lineParts else listOf(normalized)
        val splitParts = buildList {
            sourceParts.forEach { source ->
                val sentenceParts = source
                    .split(majorSentenceRegex)
                    .map { it.trim() }
                    .filter { it.isNotBlank() }
                    .ifEmpty { listOf(source) }

                sentenceParts.forEach { sentence ->
                    addAll(splitByMinorDelimiters(sentence))
                }
            }
        }

        return splitParts
            .ifEmpty { listOf(normalized) }
            .take(3)
    }

    private fun splitByMinorDelimiters(text: String, maxLen: Int = 18): List<String> {
        val sourceParts = text
            .split(Regex("(?<=[，,、；;：:])"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text) }

        val merged = mutableListOf<String>()
        var buffer = ""
        sourceParts.forEach { part ->
            if (buffer.isBlank()) {
                buffer = part
            } else if ((buffer.length + part.length) <= maxLen) {
                buffer += part
            } else {
                merged += buffer
                buffer = part
            }
        }
        if (buffer.isNotBlank()) {
            merged += buffer
        }

        return merged.flatMap { segment ->
            if (segment.length <= maxLen) {
                listOf(segment)
            } else {
                segment.chunked(maxLen)
            }
        }
    }

    private fun ensureReceiptConversationChunks(
        chunks: List<String>,
        draft: AiReceiptDraft?,
    ): List<String> {
        val normalized = chunks
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val isHealthRelated = draft?.category.orEmpty().let { category ->
            category.contains("医疗") || category.contains("健康") || category.contains("药")
        }

        val opening = if (isHealthRelated) "怎么啦？" else "收到啦。"
        val caring = if (isHealthRelated) {
            "是什么方面的药噢，要记得按时吃。"
        } else {
            "我来帮你把这笔账整理好。"
        }
        val ending = if (isHealthRelated) {
            "已经记好了，希望快点好起来。"
        } else {
            "已经记好了。"
        }

        return when {
            normalized.size >= 3 -> normalized.take(3)
            normalized.size == 2 -> listOf(normalized[0], caring, normalized[1])
            normalized.size == 1 -> listOf(opening, normalized[0], ending)
            else -> listOf(opening, caring, ending)
        }
    }

    private fun extractReceiptDraftFromText(text: String): AiReceiptDraft? {
        val payload = findPayload(text, "DATA") ?: findPayload(text, "RECEIPT") ?: return null
        return runCatching {
            val json = JSONObject(payload)
            AiReceiptDraft(
                isReceipt = json.optBoolean("isReceipt", true),
                action = json.optString("action", "create"),
                amount = if (json.has("amount") && !json.isNull("amount")) json.optDouble("amount") else null,
                category = json.optString("category").takeIf { it.isNotBlank() },
                desc = json.optString("desc").takeIf { it.isNotBlank() },
                date = json.optString("date").takeIf { it.isNotBlank() },
            )
        }.getOrNull()
    }

    private fun stripReceiptPayload(text: String): String {
        return text
            .replace(dataPayloadRegex, "")
            .replace(receiptPayloadRegex, "")
            .replace(notePayloadRegex, "")
            .replace(thinkPayloadRegex, "")
            .replace(leadingNullRegex, "")
            .replace(lineNullRegex, "")
            .replace(repeatedNullRegex, "")
            .trim()
    }

    private fun buildReceiptMetaTag(draft: AiReceiptDraft): String {
        val payload = JSONObject().apply {
            put("isReceipt", true)
            put("action", draft.action.ifBlank { "create" })
            draft.amount?.let { put("amount", abs(it)) }
            draft.category?.let { put("category", it) }
            draft.desc?.let { put("desc", it) }
            draft.date?.let { put("date", it) }
        }
        return "<RECEIPT>${payload}</RECEIPT>"
    }

    private fun findPayload(text: String, tag: String): String? {
        val regex = Regex("<$tag>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
    }

    private companion object {
        val canonicalExpenseCategories = setOf(
            "餐饮美食",
            "交通出行",
            "购物消费",
            "居家生活",
            "娱乐休闲",
            "医疗健康",
            "人情交际",
            "其他",
        )
        val dataPayloadRegex = Regex("<DATA>.*?</DATA>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val receiptPayloadRegex = Regex("<RECEIPT>.*?</RECEIPT>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val notePayloadRegex = Regex("<NOTE>.*?</NOTE>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val thinkPayloadRegex = Regex("<THINK>.*?</THINK>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val leadingNullRegex = Regex("(?is)^\\s*(?:null\\s*)+")
        val lineNullRegex = Regex("(?im)^\\s*null\\s*$")
        val repeatedNullRegex = Regex("(?i)(?:null\\s*){4,}")
        const val assistantBubbleRevealIntervalMs = 680L
    }
}
