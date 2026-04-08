package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.local.dao.ChatMessageDao
import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ChatRepositoryBatchLedgerTest {

    @Test
    fun sendMessage_supportsMultipleDataBlocksAndCreatesThreeTransactions() {
        runBlocking {
            val chatMessageDao = FakeChatMessageDao()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = transactionDao,
                aiChatGateway = FakeAiChatGateway(
                    """
                    收到啦。
                    
                    我来帮你整理好。
                    
                    已经记好了。
                    <DATA>{"isReceipt":true,"action":"create","amount":3.5,"category":"购物消费","desc":"杨桃"}</DATA>
                    <DATA>{"isReceipt":true,"action":"create","amount":18,"category":"餐饮美食","desc":"中餐"}</DATA>
                    <DATA>{"isReceipt":true,"action":"create","amount":16.9,"category":"餐饮美食","desc":"奶茶加蛋糕"}</DATA>
                    """.trimIndent(),
                ),
            )

            repository.sendMessage(
                userInput = "3.5的杨桃，18块钱的中餐，16.9的奶茶加蛋糕",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            assertEquals(3, transactionDao.countTransactions())

            val assistantReceipt = repository.observeChatRecords()
                .first()
                .lastOrNull { it.role == "assistant" && it.isReceipt }

            assertNotNull(assistantReceipt)
            assertEquals(listOf(1L, 2L, 3L), assistantReceipt?.transactionIds)
            assertEquals(3, assistantReceipt?.receiptSummary?.successCount)
            assertEquals(0, assistantReceipt?.receiptSummary?.failureCount)
            assertEquals(3, assistantReceipt?.receiptSummary?.items?.size)
        }
    }

    @Test
    fun sendMessage_supportsItemsPayloadAndKeepsPartialSuccessWhenOneCategoryMissing() {
        runBlocking {
            val chatMessageDao = FakeChatMessageDao()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = transactionDao,
                aiChatGateway = FakeAiChatGateway(
                    """
                    收到啦。
                    
                    我来帮你整理好。
                    
                    批量记账已处理完成。
                    <DATA>{
                      "isReceipt": true,
                      "action": "create",
                      "items": [
                        {"amount": 3.5, "category": "购物消费", "desc": "杨桃"},
                        {"amount": 18, "desc": "中餐"},
                        {"amount": 16.9, "category": "餐饮美食", "desc": "奶茶加蛋糕"}
                      ]
                    }</DATA>
                    """.trimIndent(),
                ),
            )

            repository.sendMessage(
                userInput = "3.5的杨桃，18块钱的中餐，16.9的奶茶加蛋糕",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            assertEquals(2, transactionDao.countTransactions())

            val assistantReceipt = repository.observeChatRecords()
                .first()
                .lastOrNull { it.role == "assistant" && it.isReceipt }

            assertNotNull(assistantReceipt)
            assertEquals(listOf(1L, 2L), assistantReceipt?.transactionIds)
            assertEquals(2, assistantReceipt?.receiptSummary?.successCount)
            assertEquals(1, assistantReceipt?.receiptSummary?.failureCount)
            assertEquals(3, assistantReceipt?.receiptSummary?.items?.size)
            val failedItem = assistantReceipt?.receiptSummary?.items?.firstOrNull { it.status == "failed" }
            assertEquals("这笔账单缺少分类，补一句分类后我再试一次。", failedItem?.failureReason)
        }
    }

    @Test
    fun sendMessage_marksAllFailedBatchAsReceiptAndShowsStructuredFailures() {
        runBlocking {
            val chatMessageDao = FakeChatMessageDao()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = transactionDao,
                aiChatGateway = FakeAiChatGateway(
                    """
                    我来试着帮你记一下。
                    <DATA>{
                      "isReceipt": true,
                      "action": "create",
                      "items": [
                        {"amount": 18, "desc": "中餐"},
                        {"amount": 16.9, "desc": "奶茶加蛋糕"}
                      ]
                    }</DATA>
                    """.trimIndent(),
                ),
            )

            repository.sendMessage(
                userInput = "18块钱的中餐，16.9的奶茶加蛋糕",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            assertEquals(0, transactionDao.countTransactions())

            val assistantReceipt = repository.observeChatRecords()
                .first()
                .lastOrNull { it.role == "assistant" && it.isReceipt }

            assertNotNull(assistantReceipt)
            assertEquals(emptyList<Long>(), assistantReceipt?.transactionIds)
            assertEquals(0, assistantReceipt?.receiptSummary?.successCount)
            assertEquals(2, assistantReceipt?.receiptSummary?.failureCount)
            assertEquals(2, assistantReceipt?.receiptSummary?.items?.size)
            assistantReceipt?.receiptSummary?.items?.forEach { item ->
                assertEquals("failed", item.status)
                assertEquals("这笔账单缺少分类，补一句分类后我再试一次。", item.failureReason)
            }
        }
    }

    @Test
    fun sendMessage_batchDeleteRequiresPreviewConfirmationThenDeletesOnConfirm() {
        runBlocking {
            val now = System.currentTimeMillis()
            val chatMessageDao = FakeChatMessageDao()
            val transactionDao = FakeTransactionDao(
                initialTransactions = listOf(
                    TransactionEntity(
                        id = 1L,
                        type = 0,
                        amount = 22.0,
                        categoryName = "餐饮美食",
                        categoryIcon = "",
                        remark = "午餐",
                        recordTimestamp = now - 1_000,
                        createdTimestamp = now - 1_000,
                    ),
                    TransactionEntity(
                        id = 2L,
                        type = 0,
                        amount = 18.0,
                        categoryName = "餐饮美食",
                        categoryIcon = "",
                        remark = "咖啡",
                        recordTimestamp = now - 2_000,
                        createdTimestamp = now - 2_000,
                    ),
                    TransactionEntity(
                        id = 3L,
                        type = 0,
                        amount = 30.0,
                        categoryName = "交通出行",
                        categoryIcon = "",
                        remark = "打车",
                        recordTimestamp = now - 3_000,
                        createdTimestamp = now - 3_000,
                    ),
                ),
            )
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = transactionDao,
                aiChatGateway = FakeAiChatGateway(
                    """
                    先预览一下。
                    <DATA>{"isReceipt":true,"action":"delete","category":"餐饮美食","desc":"最近两条"}</DATA>
                    """.trimIndent(),
                ),
            )

            repository.sendMessage(
                userInput = "删除最近两条餐饮记录",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            assertEquals(3, transactionDao.countTransactions())
            val previewReceipt = repository.observeChatRecords().first().last { it.role == "assistant" && it.isReceipt }
            assertEquals(0, previewReceipt.receiptSummary?.successCount)
            assertEquals(1, previewReceipt.receiptSummary?.failureCount)
            assertEquals(true, previewReceipt.receiptSummary?.errors?.firstOrNull()?.contains("确认删除"))

            repository.sendMessage(
                userInput = "确认删除最近两条餐饮记录",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            assertEquals(1, transactionDao.countTransactions())
            val deleteReceipt = repository.observeChatRecords().first().last { it.role == "assistant" && it.isReceipt }
            assertEquals(2, deleteReceipt.receiptSummary?.successCount)
            assertEquals(0, deleteReceipt.receiptSummary?.failureCount)
            assertEquals(listOf(1L, 2L), deleteReceipt.transactionIds.sorted())
        }
    }
}

private class FakeAiChatGateway(
    private val reply: String,
) : AiChatGateway {
    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        emit(AiStreamEvent.TextDelta(reply))
        emit(AiStreamEvent.Completed)
    }
}

private class FakeChatMessageDao : ChatMessageDao {
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
        return messages
            .sortedByDescending { it.timestamp }
            .take(limit)
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

private class FakeTransactionDao(initialTransactions: List<TransactionEntity> = emptyList()) : TransactionDao {
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

    private fun publish() {
        transactionsFlow.value = transactions.sortedByDescending { it.recordTimestamp }
    }
}
