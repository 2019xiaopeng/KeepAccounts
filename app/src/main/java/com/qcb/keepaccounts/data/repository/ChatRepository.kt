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
import com.qcb.keepaccounts.ui.model.AiRolePreset
import com.qcb.keepaccounts.ui.model.AiTone
import com.qcb.keepaccounts.ui.model.OocGuardLevel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val transactionDao: TransactionDao,
    private val aiChatGateway: AiChatGateway,
) {

    private data class CreatedTransaction(
        val transactionId: Long,
        val type: Int,
    )

    fun observeChatRecords(): Flow<List<AiChatRecord>> {
        return combine(
            chatMessageDao.observeAllMessages(),
            transactionDao.observeAllTransactions(),
        ) { messages, transactions ->
            val transactionById = transactions.associateBy { it.id }

            messages.map { entity ->
                val linkedTransaction = entity.transactionId?.let(transactionById::get)
                AiChatRecord(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    role = entity.role,
                    content = entity.content,
                    isReceipt = entity.isReceipt,
                    transactionId = entity.transactionId,
                    receiptRecordTimestamp = linkedTransaction?.recordTimestamp,
                    receiptType = linkedTransaction?.type,
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
                    val liveChunks = splitAssistantReply(liveText, aiConfig.tone)
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
        val guardedAssistantText = guardAssistantReply(cleanedAssistantText, aiConfig)

        var createdTransaction: CreatedTransaction? = null
        resolvedReceipt?.let { draft ->
            createdTransaction = tryCreateTransaction(draft, guardedAssistantText, userInput)
        }
        val linkedTransactionId = createdTransaction?.transactionId

        val finalAssistantText = if (linkedTransactionId != null) {
            guardedAssistantText.ifBlank { "已为你记录这笔账单。" }
        } else {
            guardedAssistantText.ifBlank {
                streamErrorMessage?.trim().takeUnless { it.isNullOrBlank() } ?: "收到，我在。"
            }
        }

        if (linkedTransactionId != null) {
            val replyChunks = ensureReceiptConversationChunks(
                chunks = splitAssistantReply(finalAssistantText, aiConfig.tone),
                draft = resolvedReceipt,
            )

            val receiptMeta = resolvedReceipt?.let { buildReceiptMetaTag(it, createdTransaction?.type) }.orEmpty()
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

        val finalChunks = splitAssistantReply(finalAssistantText, aiConfig.tone)

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
    ): CreatedTransaction? {
        if (!draft.isReceipt) return null

        val rawAmount = draft.amount ?: return null
        val amount = abs(rawAmount)
        if (amount <= 0.0) return null

        val type = resolveTransactionType(draft, rawAmount)
        val category = normalizeCategory(draft.category, type)
        val remark = draft.desc?.takeIf { it.isNotBlank() }
            ?: assistantText.take(24)

        val recordTimestamp = resolveRecordTimestamp(
            rawRecordTime = draft.recordTime,
            rawDate = draft.date,
            userInput = userInput,
        )
        val now = System.currentTimeMillis()

        val transactionId = transactionDao.insertTransaction(
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

        return CreatedTransaction(
            transactionId = transactionId,
            type = type,
        )
    }

    private fun resolveTransactionType(draft: AiReceiptDraft, rawAmount: Double): Int {
        if (rawAmount < 0) return 0

        val text = listOfNotNull(draft.category, draft.desc).joinToString(" ")
        val incomeHints = listOf("工资", "收入", "奖金", "报销", "兼职", "红包", "收款")
        return if (incomeHints.any { text.contains(it) }) 1 else 0
    }

    private data class ParsedDate(
        val year: Int,
        val month: Int,
        val day: Int,
    )

    private data class ParsedTime(
        val hour: Int,
        val minute: Int,
    )

    private fun resolveRecordTimestamp(
        rawRecordTime: String?,
        rawDate: String?,
        userInput: String,
    ): Long {
        val now = Calendar.getInstance()

        parseDateTimeFromUserInput(userInput, now)?.let { return it }
        parseRecordTimeFromDraft(rawRecordTime, now)?.let { return it }
        parseDateFromDraft(rawDate, now)?.let { return it }

        return now.timeInMillis
    }

    private fun parseDateTimeFromUserInput(text: String, now: Calendar): Long? {
        val input = text.trim()
        if (input.isBlank()) return null

        val parsedDate = parseDateFromText(input, now)
        val parsedTime = parseTimeFromText(input)
        if (parsedDate == null && parsedTime == null) return null

        val datePart = parsedDate ?: ParsedDate(
            year = now.get(Calendar.YEAR),
            month = now.get(Calendar.MONTH) + 1,
            day = now.get(Calendar.DAY_OF_MONTH),
        )
        val timePart = parsedTime ?: ParsedTime(
            hour = now.get(Calendar.HOUR_OF_DAY),
            minute = now.get(Calendar.MINUTE),
        )

        return buildTimestamp(datePart, timePart)
    }

    private fun parseRecordTimeFromDraft(rawRecordTime: String?, now: Calendar): Long? {
        val value = rawRecordTime?.trim().orEmpty()
        if (value.isBlank()) return null

        parseDateTimeByPatterns(value, draftDateTimePatterns)?.let { return it }
        parseDateTimeFromUserInput(value, now)?.let { return it }
        return parseDateFromDraft(value, now)
    }

    private fun parseDateFromDraft(rawDate: String?, now: Calendar): Long? {
        val value = rawDate?.trim().orEmpty()
        if (value.isBlank()) return null

        parseDateTimeByPatterns(value, draftDateTimePatterns)?.let { return it }

        val parsedDate = parseDateByPatterns(value, draftDatePatterns)
            ?: parseDateFromText(value, now)
            ?: return null

        val timePart = ParsedTime(
            hour = now.get(Calendar.HOUR_OF_DAY),
            minute = now.get(Calendar.MINUTE),
        )
        return buildTimestamp(parsedDate, timePart)
    }

    private fun parseDateFromText(text: String, now: Calendar): ParsedDate? {
        parseAbsoluteDateWithYear(text)?.let { return it }
        parseAbsoluteDateWithoutYear(text, now.get(Calendar.YEAR))?.let { return it }
        parseRelativeDate(text, now)?.let { return it }
        return null
    }

    private fun parseAbsoluteDateWithYear(text: String): ParsedDate? {
        val regex = Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?")
        val match = regex.find(text) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return ParsedDate(year = year, month = month, day = day)
    }

    private fun parseAbsoluteDateWithoutYear(text: String, currentYear: Int): ParsedDate? {
        val regex = Regex("(\\d{1,2})月(\\d{1,2})日?")
        val match = regex.find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedDate(year = currentYear, month = month, day = day)
    }

    private fun parseRelativeDate(text: String, now: Calendar): ParsedDate? {
        val offsetDays = when {
            text.contains("大前天") -> -3
            text.contains("前天") -> -2
            text.contains("昨天") || text.contains("昨日") || text.contains("昨晚") || text.contains("昨夜") -> -1
            text.contains("今天") || text.contains("今日") || text.contains("今晚") || text.contains("今早") || text.contains("今晨") -> 0
            text.contains("明天") || text.contains("明早") || text.contains("明晚") -> 1
            text.contains("后天") -> 2
            else -> return null
        }

        val resolved = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }
        return ParsedDate(
            year = resolved.get(Calendar.YEAR),
            month = resolved.get(Calendar.MONTH) + 1,
            day = resolved.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun parseTimeFromText(text: String): ParsedTime? {
        val normalized = text.replace("：", ":")

        val colonMatch = Regex("(?<!\\d)(\\d{1,2}):(\\d{1,2})(?!\\d)").find(normalized)
        if (colonMatch != null) {
            val rawHour = colonMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = colonMatch.groupValues[2].toIntOrNull() ?: return null
            if (minute !in 0..59 || rawHour !in 0..23) return null
            val hour = adjustHourByPeriodHint(rawHour, normalized)
            return ParsedTime(hour = hour, minute = minute)
        }

        val cnMatch = Regex("(?<!\\d)(\\d{1,2})\\s*(?:点|时)(?:\\s*(半|一刻|三刻|(\\d{1,2})\\s*分?))?").find(normalized)
        if (cnMatch != null) {
            val rawHour = cnMatch.groupValues[1].toIntOrNull() ?: return null
            if (rawHour !in 0..23) return null

            val minute = when {
                cnMatch.groupValues[2] == "半" -> 30
                cnMatch.groupValues[2] == "一刻" -> 15
                cnMatch.groupValues[2] == "三刻" -> 45
                cnMatch.groupValues[3].isNotBlank() -> cnMatch.groupValues[3].toIntOrNull() ?: return null
                else -> 0
            }
            if (minute !in 0..59) return null

            val hour = adjustHourByPeriodHint(rawHour, normalized)
            return ParsedTime(hour = hour, minute = minute)
        }

        resolveFuzzyTimeByHint(normalized)?.let { return it }

        val hasYesterdayHint = normalized.contains("昨天") || normalized.contains("昨日")
        if (hasYesterdayHint && !hasExplicitClockCue(normalized)) {
            return ParsedTime(hour = 12, minute = 0)
        }

        return null
    }

    private fun adjustHourByPeriodHint(hour: Int, text: String): Int {
        val morningHints = listOf("凌晨", "清晨", "早晨", "早上", "上午", "今早")
        val noonHints = listOf("中午", "午间")
        val afternoonHints = listOf("下午", "午后")
        val eveningHints = listOf("晚上", "傍晚", "夜里", "晚间", "今晚", "昨晚", "昨夜")

        val hasMorningHint = morningHints.any { text.contains(it) }
        val hasNoonHint = noonHints.any { text.contains(it) }
        val hasAfternoonHint = afternoonHints.any { text.contains(it) }
        val hasEveningHint = eveningHints.any { text.contains(it) }

        var normalizedHour = hour
        if (hasNoonHint && normalizedHour in 1..11) {
            normalizedHour = normalizedHour
        }
        if ((hasAfternoonHint || hasEveningHint) && normalizedHour in 1..11) {
            normalizedHour += 12
        }
        if (hasMorningHint && normalizedHour == 12) {
            normalizedHour = 0
        }
        if (hasNoonHint && normalizedHour == 12) {
            normalizedHour = 12
        }
        if (hasEveningHint && normalizedHour == 12) {
            normalizedHour = 0
        }

        return normalizedHour.coerceIn(0, 23)
    }

    private fun resolveFuzzyTimeByHint(text: String): ParsedTime? {
        val morningHints = listOf("早上", "早晨", "早餐", "早饭")
        if (morningHints.any { text.contains(it) }) return ParsedTime(hour = 8, minute = 0)

        val noonHints = listOf("中午", "午餐", "午饭")
        if (noonHints.any { text.contains(it) }) return ParsedTime(hour = 12, minute = 0)

        val afternoonHints = listOf("下午", "下午茶")
        if (afternoonHints.any { text.contains(it) }) return ParsedTime(hour = 15, minute = 0)

        val eveningHints = listOf("晚上", "傍晚", "晚餐", "晚饭", "昨晚", "今晚")
        if (eveningHints.any { text.contains(it) }) return ParsedTime(hour = 19, minute = 0)

        val lateNightHints = listOf("夜宵", "深夜", "半夜")
        if (lateNightHints.any { text.contains(it) }) return ParsedTime(hour = 23, minute = 0)

        return null
    }

    private fun hasExplicitClockCue(text: String): Boolean {
        if (Regex("(?<!\\d)\\d{1,2}:\\d{1,2}(?!\\d)").containsMatchIn(text)) return true
        if (Regex("(?<!\\d)\\d{1,2}\\s*(点|时)").containsMatchIn(text)) return true
        return false
    }

    private fun parseDateTimeByPatterns(value: String, patterns: List<String>): Long? {
        val normalized = value.trim()
        patterns.forEach { pattern ->
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
                sdf.parse(normalized)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseDateByPatterns(value: String, patterns: List<String>): ParsedDate? {
        val normalized = value.trim()
        patterns.forEach { pattern ->
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
                sdf.parse(normalized)
            }.getOrNull() ?: return@forEach

            val calendar = Calendar.getInstance().apply { time = parsed }
            return ParsedDate(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH),
            )
        }
        return null
    }

    private fun buildTimestamp(date: ParsedDate, time: ParsedTime): Long? {
        if (date.month !in 1..12 || date.day !in 1..31) return null
        if (time.hour !in 0..23 || time.minute !in 0..59) return null

        return runCatching {
            Calendar.getInstance().apply {
                isLenient = false
                set(Calendar.YEAR, date.year)
                set(Calendar.MONTH, date.month - 1)
                set(Calendar.DAY_OF_MONTH, date.day)
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
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
        val nowDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val toneText = when (aiConfig.tone.name) {
            "TSUNDERE" -> "傲娇毒舌"
            "RATIONAL" -> "理智管家"
            else -> "贴心治愈"
        }

        val roleProfile = buildRoleProfile(aiConfig.rolePreset)
        val toneGuide = when (aiConfig.tone) {
            AiTone.HEALING -> "语气温柔、带一点关心和鼓励，像会倾听的朋友。"
            AiTone.TSUNDERE -> "语气嘴硬心软，允许轻微吐槽，但不能攻击用户，不使用脏话。"
            AiTone.RATIONAL -> "语气专业、简洁、逻辑清晰，给出明确建议。"
        }

        val oocRuleBlock = if (aiConfig.oocGuardEnabled) {
            """
            【防 OOC 工程（已开启，等级：${aiConfig.oocGuardLevel.name}）】
            - 先做角色三问自检：我是谁、我与用户关系、此轮主要情绪目标。
            - 禁止出现模型/系统/提示词/开发者等元叙事词。
            - 禁止串角色口癖，不得切换到其他角色人设。
            - 若内容偏离角色，立刻在同一回复内自我纠偏，不解释技术原因。
            - 记账能力优先服务用户，不要因角色扮演影响记账准确性。
            """.trimIndent()
        } else {
            "【防 OOC 工程】当前关闭，仅保留基础角色一致性。"
        }

        return """
            你是一个名为${aiConfig.name}的AI记账管家，当前角色预设是${roleProfile.roleName}，语气风格是${toneText}。用户名字是${userName}。
            今天日期是${today}，当前系统准确时间是${nowDateTime}。

            【角色设定边界】
            ${roleProfile.identityAnchors.joinToString(separator = "\n") { "- $it" }}

            【语气与风格（高优先级）】
            - ${toneGuide}
            - 语气档位优先于角色设定；角色预设只用于关系与行为边界，不叠加额外语气模板。

            ${oocRuleBlock}

            【AI_Humanized_Design V1 对话体验】
            - 最小打扰：只有关键字段缺失时才追问，每轮最多1个问题，优先给候选答案。
            - 一次说清优先：信息足够时直接给结论，不把用户拉入多轮盘问。
            - 可解释建议：建议场景仅给“1个事实 + 1个可执行动作”，避免说教。
            - 纠错低成本：遇到“改成/不对/修正”优先识别为最近一笔修正意图，先确认再执行。
            - 建议有边界：用户未主动询问时，预算提醒低频且简短。

            你的任务是陪用户聊天，并在用户提到消费或收入时，提取记账信息。
            普通聊天时，尽量像真人发消息，默认输出3到4句短消息，每句不超过40字，避免长篇大论。
            如果要连发多条独立消息，请使用双换行分隔（\n\n），不要用单换行硬拆句。
            当识别到记账信息时，先输出3句简短关怀/确认语气（分句自然），最后再输出一句已记账确认，然后再附上 <DATA> JSON。
            如果识别到修正意图但缺少关键字段，不要直接记新账，先做单问题确认。

            【高级时间感知与推算规则】
            当前系统准确时间是：${nowDateTime}。在提取记账记录时，请严格按照以下优先级推算具体的 yyyy-MM-dd HH:mm：
            优先级 1：明确的相对/绝对时间（带修饰词）
            - 遇到中文语境的 12 小时制，必须正确转换为 24 小时制。
            - 例外常识：“中午11点”或“上午11点”都是 11:00，绝不能解析为 23:00；“中午12点”是 12:00。
            - “下午X点” = X + 12（如下午2点是14:00）；“晚上X点” = X + 12（如晚上8点是20:00）。
            优先级 2：模糊时间段（包含特定餐段时间）
            - 如果用户只提到了时间段或事件，没有提及具体数字，请使用以下人类生活常识默认映射，绝对不要直接使用当前系统时间：
            - 包含“早上/早晨/早餐/早饭” -> 默认 08:00
            - 包含“中午/午餐/午饭” -> 默认 12:00
            - 包含“下午/下午茶” -> 默认 15:00
            - 包含“晚上/傍晚/晚餐/晚饭” -> 默认 19:00
            - 包含“夜宵/深夜/半夜” -> 默认 23:00
            - 包含“昨天”但不指明任何时段 -> 默认昨天的 12:00
            优先级 3：完全没有时间线索
            - 只有当用户句子中完全不包含上述任何时间词或餐段词（如“刚买了一瓶水”“打车花了20”），才允许使用当前系统时间。
            额外约束：
            - 如果用户提到相对日期（如昨天/前天/大前天/今天/明天），你必须基于“今天日期”换算 date。
            - 如果用户没有明确提到具体日期，date 字段必须填写今天（${today}）。
            - date 必须与 recordTime 的日期部分保持一致。

            如果用户的话包含记账信息，你必须在回复最末尾严格输出 <DATA>...</DATA> JSON：
            {
              "isReceipt": true,
              "action": "create",
              "amount": 30.0,
              "category": "交通",
              "desc": "打车",
              "recordTime": "${nowDateTime}",
              "date": "${today}"
            }
            注意：recordTime 字段必须始终存在，格式固定为 yyyy-MM-dd HH:mm。
            如果只是普通闲聊，不要输出 <DATA> 标签。
        """.trimIndent()
    }

    private data class RoleProfile(
        val roleName: String,
        val identityAnchors: List<String>,
    )

    private fun buildRoleProfile(preset: AiRolePreset): RoleProfile {
        return when (preset) {
            AiRolePreset.XAVIER -> RoleProfile(
                roleName = "沈星回 Xavier",
                identityAnchors = listOf(
                    "核心气质是克制、安静、守护优先。",
                    "先确认用户状态与安全感，再提供建议。",
                    "承诺要少而稳，行动感强于口号。",
                ),
            )

            AiRolePreset.ZAYNE -> RoleProfile(
                roleName = "黎深 Zayne",
                identityAnchors = listOf(
                    "核心气质是理性、稳重、专业可靠。",
                    "重视事实和节奏，不制造戏剧化冲突。",
                    "关心通过精准方案表达，不是空泛安慰。",
                ),
            )

            AiRolePreset.RAFAYEL -> RoleProfile(
                roleName = "祁煜 Rafayel",
                identityAnchors = listOf(
                    "核心气质是艺术感、敏锐、浪漫。",
                    "可有灵动比喻，但必须落地到真实关心。",
                    "外层可调侃，内层真诚并重情义。",
                ),
            )

            AiRolePreset.SYLUS -> RoleProfile(
                roleName = "秦彻 Sylus",
                identityAnchors = listOf(
                    "核心气质是强势、冷静、执行导向。",
                    "给结论与路径，但尊重用户选择权。",
                    "保护感通过行动体现，不靠威吓。",
                ),
            )

            AiRolePreset.CALEB -> RoleProfile(
                roleName = "夏以昼 Caleb",
                identityAnchors = listOf(
                    "核心气质是行动派、可靠、并肩感。",
                    "先做清单化拆解，再带用户执行。",
                    "坚定但不居高临下。",
                ),
            )
        }
    }

    private fun guardAssistantReply(
        text: String,
        aiConfig: AiAssistantConfig,
    ): String {
        val raw = text.trim()
        if (raw.isBlank()) return raw
        if (!aiConfig.oocGuardEnabled) return raw

        val filtered = raw
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filterNot { line -> oocMetaLeakRegex.containsMatchIn(line) }
            .filterNot { line ->
                aiConfig.oocGuardLevel == OocGuardLevel.STRICT && strictMetaLeakRegex.containsMatchIn(line)
            }
            .joinToString("\n")
            .trim()

        val strictIdentityMismatch = aiConfig.oocGuardLevel == OocGuardLevel.STRICT &&
            looksLikeWrongIdentity(filtered, aiConfig.rolePreset)

        if (filtered.isBlank() || strictIdentityMismatch) {
            return roleFallbackReply(aiConfig.rolePreset, aiConfig.tone)
        }

        return filtered
    }

    private fun looksLikeWrongIdentity(
        text: String,
        preset: AiRolePreset,
    ): Boolean {
        val intro = text.take(60)
        if (!intro.contains("我是")) return false
        val otherRoleNames = allRoleNameAliases
            .filterKeys { it != preset }
            .values
            .flatten()
        return otherRoleNames.any { alias -> intro.contains(alias, ignoreCase = true) }
    }

    private fun roleFallbackReply(
        preset: AiRolePreset,
        tone: AiTone,
    ): String {
        val roleFallback = when (preset) {
            AiRolePreset.XAVIER -> "我在，先别慌。你把现在最棘手的一点告诉我，我们一步步处理。"
            AiRolePreset.ZAYNE -> "我在听。先稳定下来，我们先处理最关键的一步。"
            AiRolePreset.RAFAYEL -> "我在这。先别让情绪把你推着走，我们先抓住最重要的那一件。"
            AiRolePreset.SYLUS -> "先冷静。我在，你现在按我说的先把风险降下来。"
            AiRolePreset.CALEB -> "别急，我跟你一起。先做第一步，后面的我陪你推进。"
        }

        return if (tone == AiTone.RATIONAL) {
            roleFallback.replace("别慌", "先保持稳定")
        } else {
            roleFallback
        }
    }

    private fun resolveTemperature(tone: AiTone): Double {
        return when (tone) {
            AiTone.HEALING -> 0.55
            AiTone.TSUNDERE -> 0.70
            AiTone.RATIONAL -> 0.25
        }
    }

    private fun splitAssistantReply(text: String, tone: AiTone): List<String> {
        val normalized = text
            .replace("\r", "")
            .trim()

        if (normalized.isBlank()) return ensureMinimumConversationChunks(listOf("收到，我在。"), tone)

        val explicitParts = normalized
            .split(conversationDelimiterRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val sourceParts = if (explicitParts.size > 1) explicitParts else listOf(normalized)
        val splitParts = buildList {
            sourceParts.forEach { source ->
                addAll(splitByMajorSentences(source))
            }
        }

        val capped = splitParts
            .ifEmpty { listOf(normalized) }
            .take(4)

        return ensureMinimumConversationChunks(capped, tone)
    }

    private fun ensureMinimumConversationChunks(
        chunks: List<String>,
        tone: AiTone,
    ): List<String> {
        val result = chunks
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .toMutableList()

        val supplements = when (tone) {
            AiTone.HEALING -> listOf(
                "我先陪你把重点捋清。",
                "你只要说一声，我们就开始。",
            )

            AiTone.TSUNDERE -> listOf(
                "先别乱想，我在这盯着。",
                "把最关键的一条告诉我就行。",
            )

            AiTone.RATIONAL -> listOf(
                "我们按步骤处理会更稳。",
                "先确认第一优先级，再推进。",
            )
        }

        supplements.forEach { sentence ->
            if (result.size < 3) result += sentence
        }

        while (result.size < 3) {
            result += "先给我一个最关键的信息。"
        }

        return result.take(4)
    }

    private fun splitByMajorSentences(text: String, maxLen: Int = 42): List<String> {
        val sentenceParts = text
            .split(majorSentenceRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text.trim()) }

        if (sentenceParts.size == 1 && sentenceParts.first().length <= maxLen) {
            return sentenceParts
        }

        val merged = mutableListOf<String>()
        var buffer = ""
        sentenceParts.forEach { sentence ->
            if (buffer.isBlank()) {
                buffer = sentence
            } else if (buffer.length + sentence.length <= maxLen) {
                buffer += sentence
            } else {
                merged += buffer
                buffer = sentence
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
                recordTime = json.optString("recordTime").takeIf { it.isNotBlank() },
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

    private fun buildReceiptMetaTag(draft: AiReceiptDraft, resolvedType: Int?): String {
        val typeValue = resolvedType?.let { if (it == 1) "income" else "expense" }
            ?: draft.amount?.let { if (it < 0) "expense" else "income" }
        val payload = JSONObject().apply {
            put("isReceipt", true)
            put("action", draft.action.ifBlank { "create" })
            draft.amount?.let { put("amount", abs(it)) }
            draft.category?.let { put("category", it) }
            draft.desc?.let { put("desc", it) }
            draft.recordTime?.let { put("recordTime", it) }
            draft.date?.let { put("date", it) }
            typeValue?.let { put("type", it) }
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
        val oocMetaLeakRegex = Regex(
            "(?i)(作为\\s*ai|作为\\s*一个\\s*ai|作为\\s*语言模型|我是\\s*ai|我是\\s*人工智能|system prompt|developer message|提示词|系统提示|越狱|jailbreak|模型身份|chatgpt|llm)",
        )
        val strictMetaLeakRegex = Regex(
            "(?i)(instructions?|prompt engineering|思维链|chain of thought|cot|roleplay规则|你让我扮演)",
        )
        val conversationDelimiterRegex = Regex("\\n\\s*\\n+|<MSG>|\\|\\|\\|", setOf(RegexOption.IGNORE_CASE))
        val majorSentenceRegex = Regex("(?<=[。！？!?])")
        val draftDateTimePatterns = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy.MM.dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy年M月d日 HH:mm",
            "yyyy年M月d日 H:mm",
        )
        val draftDatePatterns = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "yyyy年M月d日",
        )
        val allRoleNameAliases = mapOf(
            AiRolePreset.XAVIER to listOf("沈星回", "xavier"),
            AiRolePreset.ZAYNE to listOf("黎深", "zayne"),
            AiRolePreset.RAFAYEL to listOf("祁煜", "rafayel"),
            AiRolePreset.SYLUS to listOf("秦彻", "sylus"),
            AiRolePreset.CALEB to listOf("夏以昼", "caleb"),
        )
        const val assistantBubbleRevealIntervalMs = 520L
    }
}
