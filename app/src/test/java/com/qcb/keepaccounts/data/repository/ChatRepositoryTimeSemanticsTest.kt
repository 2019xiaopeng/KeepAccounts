package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.local.dao.ChatMessageDao
import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiReceiptDraft
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class ChatRepositoryTimeSemanticsTest {

    @Test
    fun sendMessage_gatewayHistoryIncludesTimestampAndUsesHiddenPayloadContext() {
        runBlocking {
            val chatMessageDao = TimeSemanticsFakeChatMessageDao()
            val transactionDao = TimeSemanticsFakeTransactionDao()
            val now = System.currentTimeMillis()

            chatMessageDao.insertMessage(
                ChatMessageEntity(
                    role = "assistant",
                    content = "已经记好了。<DATA>{\"isReceipt\":true,\"action\":\"update\",\"amount\":15.0,\"category\":\"餐饮美食\"}</DATA>",
                    isReceipt = true,
                    timestamp = now - 14 * 60 * 60 * 1000,
                ),
            )

            val gateway = TimeSemanticsFakeAiChatGateway(reply = "收到啦。")
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = transactionDao,
                aiChatGateway = gateway,
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "你好啊",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val request = gateway.lastRequest
            assertNotNull(request)

            val messages = request?.messages.orEmpty()
            val systemMessages = messages.filter { it.role == "system" }
            val dialogueMessages = messages.filter { it.role != "system" }
            val userDialogueMessages = dialogueMessages.filter { it.role == "user" }
            val assistantDialogueMessages = dialogueMessages.filter { it.role == "assistant" }

            assertTrue(userDialogueMessages.any { it.content.contains("消息时间：") })
            assertFalse(assistantDialogueMessages.any { it.content.contains("消息时间：") })
            assertTrue(
                systemMessages.any {
                    it.content.contains("内部上下文") &&
                        it.content.contains("上一轮账单结果")
                },
            )
            assertFalse(dialogueMessages.any { it.content.contains("上轮记账上下文") })
            assertFalse(dialogueMessages.any { it.content.contains("action=") })
        }
    }

    @Test
    fun sendMessage_splitsLiteralEscapedNewlinesIntoMultipleBubbles() {
        runBlocking {
            val gateway = TimeSemanticsFakeAiChatGateway(
                reply = "第一句\\n\\n第二句\\n\\n第三句\\n\\n第四句",
            )
            val repository = ChatRepository(
                chatMessageDao = TimeSemanticsFakeChatMessageDao(),
                transactionDao = TimeSemanticsFakeTransactionDao(),
                aiChatGateway = gateway,
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "测试切分",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantChunks = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" && !it.isReceipt }
                .map { it.content }

            assertEquals(listOf("第一句", "第二句", "第三句", "第四句"), assistantChunks)
        }
    }

    @Test
    fun sendMessage_removesLeakedInternalContextLinesFromVisibleReply() {
        runBlocking {
            val gateway = TimeSemanticsFakeAiChatGateway(
                reply = "好的好的，改成15块。\\n\\n现在是更正好的记录了。\\n消息时间：2026-04-12 14:19（时区 Asia/Shanghai / UTC+08:00）\\n上轮记账上下文：action=update, amount=15.0",
            )
            val repository = ChatRepository(
                chatMessageDao = TimeSemanticsFakeChatMessageDao(),
                transactionDao = TimeSemanticsFakeTransactionDao(),
                aiChatGateway = gateway,
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "稍等，帮我改一下，是15块",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" && !it.isReceipt }
                .joinToString("\n") { it.content }

            assertTrue(assistantText.contains("改成15块"))
            assertFalse(assistantText.contains("上轮记账上下文"))
            assertFalse(assistantText.contains("action=update"))
            assertFalse(assistantText.contains("消息时间："))
        }
    }

    @Test
    fun sendMessage_updatesYesterdayLunchAmountAndKeepsSemanticTimestamp() {
        runBlocking {
            val now = Calendar.getInstance()
            val originalRecord = calendarAt(now, dayOffset = -1, hour = 12, minute = 30)
            val expectedRecord = calendarAt(now, dayOffset = -1, hour = 12, minute = 0)
            val transactionDao = TimeSemanticsFakeTransactionDao(
                listOf(
                    TransactionEntity(
                        id = 1L,
                        type = 0,
                        amount = 18.0,
                        categoryName = "餐饮美食",
                        categoryIcon = "",
                        remark = "午餐",
                        recordTimestamp = originalRecord.timeInMillis,
                        createdTimestamp = originalRecord.timeInMillis,
                    ),
                ),
            )
            val repository = ChatRepository(
                chatMessageDao = TimeSemanticsFakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = TimeSemanticsFakeAiChatGateway(
                    reply = "收到啦。\n\n我来帮你改好。\n\n已经更新好了。",
                    draft = AiReceiptDraft(
                        isReceipt = true,
                        action = "update",
                        amount = 10.0,
                        category = "餐饮美食",
                        desc = "午餐",
                        recordTime = formatDateTime(expectedRecord),
                        date = formatDate(expectedRecord),
                    ),
                ),
            )

            repository.sendMessage(
                userInput = "把昨天中午午餐改成10块",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val updated = transactionDao.requireTransaction(1L)
            assertEquals(10.0, updated.amount, 0.001)
            assertEquals(expectedRecord.timeInMillis, updated.recordTimestamp)

            val assistantReceipt = repository.observeChatRecords()
                .first()
                .last { it.role == "assistant" && it.isReceipt }

            assertEquals(1L, assistantReceipt.transactionId)
            assertEquals(expectedRecord.timeInMillis, assistantReceipt.receiptRecordTimestamp)
        }
    }

    @Test
    fun sendMessage_updatesDinnerWithoutExactTimeUsingTodaySemanticTime() {
        runBlocking {
            val now = Calendar.getInstance()
            val lunchRecord = calendarAt(now, dayOffset = 0, hour = 12, minute = 30)
            val dinnerRecord = calendarAt(now, dayOffset = 0, hour = 19, minute = 20)
            val expectedDinner = calendarAt(now, dayOffset = 0, hour = 19, minute = 0)
            val transactionDao = TimeSemanticsFakeTransactionDao(
                listOf(
                    TransactionEntity(
                        id = 1L,
                        type = 0,
                        amount = 18.0,
                        categoryName = "餐饮美食",
                        categoryIcon = "",
                        remark = "午餐",
                        recordTimestamp = lunchRecord.timeInMillis,
                        createdTimestamp = lunchRecord.timeInMillis,
                    ),
                    TransactionEntity(
                        id = 2L,
                        type = 0,
                        amount = 22.0,
                        categoryName = "餐饮美食",
                        categoryIcon = "",
                        remark = "晚饭",
                        recordTimestamp = dinnerRecord.timeInMillis,
                        createdTimestamp = dinnerRecord.timeInMillis,
                    ),
                ),
            )
            val repository = ChatRepository(
                chatMessageDao = TimeSemanticsFakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = TimeSemanticsFakeAiChatGateway(
                    reply = "收到啦。\n\n我来帮你改一下。\n\n已经改好了。",
                    draft = AiReceiptDraft(
                        isReceipt = true,
                        action = "update",
                        amount = 26.0,
                        category = "餐饮美食",
                        desc = "晚饭",
                        recordTime = null,
                        date = null,
                    ),
                ),
            )

            repository.sendMessage(
                userInput = "晚饭改成26",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val lunch = transactionDao.requireTransaction(1L)
            val dinner = transactionDao.requireTransaction(2L)
            assertEquals(18.0, lunch.amount, 0.001)
            assertEquals(26.0, dinner.amount, 0.001)
            assertEquals(expectedDinner.timeInMillis, dinner.recordTimestamp)

            val assistantReceipt = repository.observeChatRecords()
                .first()
                .last { it.role == "assistant" && it.isReceipt }

            assertEquals(2L, assistantReceipt.transactionId)
            assertEquals(expectedDinner.timeInMillis, assistantReceipt.receiptRecordTimestamp)
        }
    }

    private fun calendarAt(reference: Calendar, dayOffset: Int, hour: Int, minute: Int): Calendar {
        return (reference.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, dayOffset)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
    }

    private fun formatDate(calendar: Calendar): String {
        return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(calendar.time)
    }

    private fun formatDateTime(calendar: Calendar): String {
        return SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(calendar.time)
    }
}

private class TimeSemanticsFakeAiChatGateway(
    private val reply: String,
    private val draft: AiReceiptDraft? = null,
) : AiChatGateway {
    var lastRequest: AiChatRequest? = null
        private set

    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        lastRequest = request
        emit(AiStreamEvent.TextDelta(reply))
        draft?.let { emit(AiStreamEvent.ReceiptParsed(listOf(it))) }
        emit(AiStreamEvent.Completed)
    }
}

private class TimeSemanticsFakeChatMessageDao : ChatMessageDao {
    private val messages = mutableListOf<ChatMessageEntity>()
    private val messagesFlow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var nextId = 1L

    override suspend fun insertMessage(message: ChatMessageEntity): Long {
        val stored = message.copy(id = if (message.id == 0L) nextId++ else message.id)
        messages += stored
        publish()
        return stored.id
    }

    override fun observeAllMessages(): Flow<List<ChatMessageEntity>> = messagesFlow

    override suspend fun getRecentMessages(limit: Int): List<ChatMessageEntity> {
        return messages.sortedByDescending { it.timestamp }.take(limit)
    }

    override suspend fun getMessageById(id: Long): ChatMessageEntity? = messages.firstOrNull { it.id == id }

    override suspend fun updateMessage(
        id: Long,
        content: String,
        isReceipt: Boolean,
        transactionId: Long?,
        transactionBindings: String?,
    ) {
        val index = messages.indexOfFirst { it.id == id }
        if (index >= 0) {
            messages[index] = messages[index].copy(
                content = content,
                isReceipt = isReceipt,
                transactionId = transactionId,
                transactionBindings = transactionBindings,
            )
            publish()
        }
    }

    override suspend fun deleteMessagesByIds(ids: List<Long>) {
        messages.removeAll { it.id in ids }
        publish()
    }

    override suspend fun deleteMessageById(id: Long) {
        messages.removeAll { it.id == id }
        publish()
    }

    override suspend fun clearMessages() {
        messages.clear()
        publish()
    }

    private fun publish() {
        messagesFlow.value = messages.sortedBy { it.timestamp }
    }
}

private class TimeSemanticsFakeTransactionDao(initialTransactions: List<TransactionEntity> = emptyList()) : TransactionDao {
    private val transactions = initialTransactions.toMutableList()
    private val transactionsFlow = MutableStateFlow<List<TransactionEntity>>(emptyList())
    private var nextId = (transactions.maxOfOrNull { it.id } ?: 0L) + 1L

    init {
        publish()
    }

    override suspend fun insertTransaction(transaction: TransactionEntity): Long {
        val stored = transaction.copy(id = if (transaction.id == 0L) nextId++ else transaction.id)
        transactions += stored
        publish()
        return stored.id
    }

    override suspend fun insertTransactions(transactions: List<TransactionEntity>) {
        transactions.forEach { insertTransaction(it) }
    }

    override suspend fun countTransactions(): Int = transactions.size

    override fun observeAllTransactions(): Flow<List<TransactionEntity>> = transactionsFlow

    override suspend fun getRecentTransactions(limit: Int): List<TransactionEntity> {
        return transactions.sortedByDescending { it.recordTimestamp }.take(limit)
    }

    override suspend fun getTransactionsInRange(
        startAtMillis: Long,
        endAtMillis: Long,
        limit: Int,
    ): List<TransactionEntity> {
        return transactions
            .filter { it.recordTimestamp in startAtMillis..endAtMillis }
            .sortedByDescending { it.recordTimestamp }
            .take(limit)
    }

    override fun observeTransactionById(id: Long): Flow<TransactionEntity?> = MutableStateFlow(
        transactions.firstOrNull { it.id == id },
    )

    override suspend fun getTransactionById(id: Long): TransactionEntity? {
        return transactions.firstOrNull { it.id == id }
    }

    override suspend fun updateTransactionById(
        id: Long,
        type: Int,
        amount: Double,
        categoryName: String,
        categoryIcon: String,
        remark: String,
        recordTimestamp: Long,
    ) {
        val index = transactions.indexOfFirst { it.id == id }
        if (index >= 0) {
            val current = transactions[index]
            transactions[index] = current.copy(
                type = type,
                amount = amount,
                categoryName = categoryName,
                categoryIcon = categoryIcon,
                remark = remark,
                recordTimestamp = recordTimestamp,
            )
            publish()
        }
    }

    override suspend fun deleteTransactionById(id: Long) {
        transactions.removeAll { it.id == id }
        publish()
    }

    fun requireTransaction(id: Long): TransactionEntity {
        return transactions.first { it.id == id }
    }

    private fun publish() {
        transactionsFlow.value = transactions.sortedByDescending { it.recordTimestamp }
    }
}
