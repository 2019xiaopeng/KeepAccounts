package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.agent.AgentQualityFeedbackRepository
import com.qcb.keepaccounts.data.agent.AgentQualityStage
import com.qcb.keepaccounts.data.local.dao.AgentQualityFeedbackDao
import com.qcb.keepaccounts.data.local.dao.ChatMessageDao
import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.AgentQualityFeedbackEntity
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.domain.agent.AgentPlanner
import com.qcb.keepaccounts.domain.agent.IntentPlanV2
import com.qcb.keepaccounts.domain.agent.AgentToolArgs
import com.qcb.keepaccounts.domain.agent.AgentToolStatus
import com.qcb.keepaccounts.domain.agent.PlannerInputV2
import com.qcb.keepaccounts.domain.agent.PlannerIntentType
import com.qcb.keepaccounts.domain.agent.TransactionFilter
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
                agentDefaultPathEnabled = false,
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
            assertEquals("哎呀，这笔账单还不知道是什么分类呢，告诉我是吃喝还是交通，我立刻补上~", failedItem?.failureReason)
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
                agentDefaultPathEnabled = false,
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
                assertEquals("哎呀，这笔账单还不知道是什么分类呢，告诉我是吃喝还是交通，我立刻补上~", item.failureReason)
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

    @Test
    fun queryTransactionsTool_recentHighestExpenseInLast7Days_returnsExpectedTopRecord() {
        runBlocking {
            val now = System.currentTimeMillis()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 1L,
                            type = 0,
                            amount = 12.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "早餐",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                        TransactionEntity(
                            id = 2L,
                            type = 0,
                            amount = 88.0,
                            categoryName = "交通出行",
                            categoryIcon = "",
                            remark = "打车",
                            recordTimestamp = now - 2 * 24 * 60 * 60 * 1000L,
                            createdTimestamp = now - 2 * 24 * 60 * 60 * 1000L,
                        ),
                        TransactionEntity(
                            id = 3L,
                            type = 0,
                            amount = 199.0,
                            categoryName = "购物消费",
                            categoryIcon = "",
                            remark = "外套",
                            recordTimestamp = now - 20 * 24 * 60 * 60 * 1000L,
                            createdTimestamp = now - 20 * 24 * 60 * 60 * 1000L,
                        ),
                    ),
                ),
                aiChatGateway = FakeAiChatGateway("收到"),
            )

            val result = repository.queryTransactionsTool(
                AgentToolArgs.QueryTransactionsArgs(
                    filters = TransactionFilter(),
                    window = "last7days",
                    sortKey = "amount_desc",
                    limit = 1,
                ),
            )

            assertEquals(AgentToolStatus.SUCCESS, result.status)
            assertEquals(1, result.result?.items?.size)
            assertEquals(2L, result.result?.items?.first()?.transactionId)
            assertEquals("amount_desc", result.result?.explainability?.sortKey)
            assertEquals(true, result.resultJson.contains("\"sampleSize\""))
        }
    }

    @Test
    fun querySpendingStatsTool_frequencyByCategory_returnsMostFrequentCategory() {
        runBlocking {
            val now = System.currentTimeMillis()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 1L,
                            type = 0,
                            amount = 20.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "午餐",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                        TransactionEntity(
                            id = 2L,
                            type = 0,
                            amount = 25.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "晚餐",
                            recordTimestamp = now - 2_000,
                            createdTimestamp = now - 2_000,
                        ),
                        TransactionEntity(
                            id = 3L,
                            type = 0,
                            amount = 45.0,
                            categoryName = "交通出行",
                            categoryIcon = "",
                            remark = "打车",
                            recordTimestamp = now - 3_000,
                            createdTimestamp = now - 3_000,
                        ),
                    ),
                ),
                aiChatGateway = FakeAiChatGateway("收到"),
            )

            val result = repository.querySpendingStatsTool(
                AgentToolArgs.QuerySpendingStatsArgs(
                    window = "last30days",
                    groupBy = "category",
                    metric = "frequency",
                    sortKey = "frequency_desc",
                    topN = 1,
                ),
            )

            assertEquals(AgentToolStatus.SUCCESS, result.status)
            assertEquals(1, result.result?.buckets?.size)
            assertEquals("餐饮美食", result.result?.buckets?.first()?.key)
            assertEquals(2.0, result.result?.buckets?.first()?.value ?: 0.0, 0.001)
            assertEquals("count", result.result?.explainability?.aggregationMethod)
        }
    }

    @Test
    fun sendMessage_queryRecentOneRoutesToQueryToolAndBypassesGateway() {
        runBlocking {
            val now = System.currentTimeMillis()
            val gateway = CountingAiChatGateway()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 11L,
                            type = 0,
                            amount = 26.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "晚饭",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                        TransactionEntity(
                            id = 12L,
                            type = 0,
                            amount = 15.0,
                            categoryName = "饮品",
                            categoryIcon = "",
                            remark = "奶茶",
                            recordTimestamp = now - 5_000,
                            createdTimestamp = now - 5_000,
                        ),
                    ),
                ),
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "帮我查最近一笔记录",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = stripHiddenPayloads(assistantText)

            assertEquals(0, gateway.requestCount)
            assertEquals(true, visibleAssistantText.contains("最近一笔"))
            assertEquals(false, visibleAssistantText.contains("结构化结果"))
            assertEquals(false, visibleAssistantText.contains("追踪ID"))
            assertEquals(false, visibleAssistantText.contains("sampleSize="))
        }
    }

    @Test
    fun sendMessage_statsSameMerchantRoutesToStatsToolAndReturnsMerchantFrequency() {
        runBlocking {
            val now = System.currentTimeMillis()
            val gateway = CountingAiChatGateway()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 21L,
                            type = 0,
                            amount = 18.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "瑞幸咖啡",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                        TransactionEntity(
                            id = 22L,
                            type = 0,
                            amount = 22.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "瑞幸咖啡",
                            recordTimestamp = now - 2_000,
                            createdTimestamp = now - 2_000,
                        ),
                        TransactionEntity(
                            id = 23L,
                            type = 0,
                            amount = 30.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "星巴克",
                            recordTimestamp = now - 3_000,
                            createdTimestamp = now - 3_000,
                        ),
                    ),
                ),
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "我是不是总吃同一家？",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = stripHiddenPayloads(assistantText)

            assertEquals(0, gateway.requestCount)
            assertEquals(true, visibleAssistantText.contains("瑞幸咖啡"))
            assertEquals(false, visibleAssistantText.contains("topMerchant="))
            assertEquals(false, visibleAssistantText.contains("结构化结果"))
        }
    }

    @Test
    fun sendMessage_queryRoute_recordsPlannerShadowMatchSample() {
        runBlocking {
            val now = System.currentTimeMillis()
            val feedbackDao = object : AgentQualityFeedbackDao {
                private val items = mutableListOf<AgentQualityFeedbackEntity>()
                private var nextId = 1L

                override suspend fun insertFeedback(feedback: AgentQualityFeedbackEntity) {
                    items += feedback.copy(id = nextId++)
                }

                override suspend fun listByRequestId(requestId: String): List<AgentQualityFeedbackEntity> {
                    return items.filter { it.requestId == requestId }
                }

                override suspend fun getLatestFeedback(): AgentQualityFeedbackEntity? {
                    return items.maxByOrNull { it.createdAt }
                }

                override suspend fun listSince(sinceMillis: Long): List<AgentQualityFeedbackEntity> {
                    return items.filter { it.createdAt >= sinceMillis }
                }

                override suspend fun markCorrectionSample(
                    requestId: String,
                    correctedByRequestId: String,
                    metadataJson: String?,
                ) {
                    val index = items.indexOfLast { it.requestId == requestId }
                    if (index < 0) return
                    val current = items[index]
                    items[index] = current.copy(
                        isCorrectionSample = true,
                        correctedByRequestId = correctedByRequestId,
                        metadataJson = metadataJson,
                    )
                }
            }

            val qualityRepository = AgentQualityFeedbackRepository(feedbackDao)
            val planner = object : AgentPlanner {
                override suspend fun plan(input: PlannerInputV2): IntentPlanV2 {
                    return IntentPlanV2(
                        intent = PlannerIntentType.QUERY_TRANSACTIONS,
                        confidence = 0.94,
                    )
                }
            }

            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 91L,
                            type = 0,
                            amount = 19.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "晚饭",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                    ),
                ),
                aiChatGateway = CountingAiChatGateway(),
                qualityFeedbackRepository = qualityRepository,
                agentPlanner = planner,
                plannerShadowEnabled = true,
            )

            repository.sendMessage(
                userInput = "帮我查最近一笔记录",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val records = feedbackDao.listSince(0L)
            val shadowRecord = records.lastOrNull { it.stage == AgentQualityStage.PLANNER_SHADOW.name }

            assertEquals(true, records.any { it.stage == AgentQualityStage.TOOL_EXECUTION.name })
            assertEquals(true, shadowRecord != null)
            assertEquals("SHADOW_MATCH", shadowRecord?.runStatus)
            assertEquals("QUERY_TRANSACTIONS", shadowRecord?.expectedAction)
            assertEquals("QUERY_TRANSACTIONS", shadowRecord?.actualAction)
            assertEquals(true, shadowRecord?.metadataJson?.contains("\"plannerAvailable\":true") == true)
        }
    }

    @Test
    fun sendMessage_colloquialTaxiExpenseRoutesToLocalWriteAndBypassesGateway() {
        runBlocking {
            val gateway = CountingAiChatGateway()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "打车花了 50",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val latest = transactionDao.getRecentTransactions(limit = 1).firstOrNull()
            assertEquals(0, gateway.requestCount)
            assertEquals(1, transactionDao.countTransactions())
            assertEquals("交通出行", latest?.categoryName)
            assertEquals(50.0, latest?.amount ?: 0.0, 0.001)
        }
    }

    @Test
    fun sendMessage_colloquialRelativeMealRoutesToLocalWriteAndInfersCategory() {
        runBlocking {
            val gateway = CountingAiChatGateway()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "前天晚饭 30",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val latest = transactionDao.getRecentTransactions(limit = 1).firstOrNull()
            assertEquals(0, gateway.requestCount)
            assertEquals(1, transactionDao.countTransactions())
            assertEquals("餐饮美食", latest?.categoryName)
            assertEquals(30.0, latest?.amount ?: 0.0, 0.001)
        }
    }

    @Test
    fun sendMessage_colloquialMiddayMealPhraseRoutesToLocalWriteAndInfersCategory() {
        runBlocking {
            val gateway = CountingAiChatGateway()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "前天吃中饭花了20",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val latest = transactionDao.getRecentTransactions(limit = 1).firstOrNull()
            assertEquals(0, gateway.requestCount)
            assertEquals(1, transactionDao.countTransactions())
            assertEquals("餐饮美食", latest?.categoryName)
            assertEquals(20.0, latest?.amount ?: 0.0, 0.001)
        }
    }

    @Test
    fun sendMessage_monthCategoryColloquialQueryRoutesToLocalQueryAndBypassesGateway() {
        runBlocking {
            val now = System.currentTimeMillis()
            val gateway = CountingAiChatGateway()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 31L,
                            type = 0,
                            amount = 48.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "午餐",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                    ),
                ),
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "本月餐饮",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = stripHiddenPayloads(assistantText)

            assertEquals(0, gateway.requestCount)
            assertEquals(true, visibleAssistantText.contains("餐饮") || visibleAssistantText.contains("账单"))
            assertEquals(false, visibleAssistantText.contains("结构化结果"))
            assertEquals(false, visibleAssistantText.contains("sampleSize="))
        }
    }

    @Test
    fun sendMessage_updateRecentOneColloquialRoutesToWriteAndUpdatesLatest() {
        runBlocking {
            val now = System.currentTimeMillis()
            val gateway = CountingAiChatGateway()
            val transactionDao = FakeTransactionDao(
                initialTransactions = listOf(
                    TransactionEntity(
                        id = 41L,
                        type = 0,
                        amount = 20.0,
                        categoryName = "餐饮美食",
                        categoryIcon = "",
                        remark = "午饭",
                        recordTimestamp = now - 10_000,
                        createdTimestamp = now - 10_000,
                    ),
                    TransactionEntity(
                        id = 42L,
                        type = 0,
                        amount = 50.0,
                        categoryName = "交通出行",
                        categoryIcon = "",
                        remark = "打车",
                        recordTimestamp = now - 1_000,
                        createdTimestamp = now - 1_000,
                    ),
                ),
            )
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "帮我把最近一笔改成30块",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val latest = transactionDao.getRecentTransactions(limit = 1).firstOrNull()
            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = stripHiddenPayloads(assistantText)

            assertEquals(0, gateway.requestCount)
            assertEquals(2, transactionDao.countTransactions())
            assertEquals(30.0, latest?.amount ?: 0.0, 0.001)
            assertEquals(true, visibleAssistantText.contains("改好") || visibleAssistantText.contains("修改"))
            assertEquals(false, visibleAssistantText.contains("结构化结果"))
            assertEquals(false, visibleAssistantText.contains("追踪ID"))
        }
    }

    @Test
    fun sendMessage_updatePhraseDoesNotTreatAmountAsTransactionId() {
        runBlocking {
            val now = System.currentTimeMillis()
            val gateway = CountingAiChatGateway()
            val transactionDao = FakeTransactionDao(
                initialTransactions = listOf(
                    TransactionEntity(
                        id = 15L,
                        type = 0,
                        amount = 99.0,
                        categoryName = "购物消费",
                        categoryIcon = "",
                        remark = "旧记录",
                        recordTimestamp = now - 100_000,
                        createdTimestamp = now - 100_000,
                    ),
                    TransactionEntity(
                        id = 42L,
                        type = 0,
                        amount = 50.0,
                        categoryName = "交通出行",
                        categoryIcon = "",
                        remark = "打车",
                        recordTimestamp = now - 1_000,
                        createdTimestamp = now - 1_000,
                    ),
                ),
            )
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "帮我把刚刚打车记录改成15",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val updatedRecent = transactionDao.getTransactionById(42L)
            val untouchedOld = transactionDao.getTransactionById(15L)

            assertEquals(0, gateway.requestCount)
            assertEquals(2, transactionDao.countTransactions())
            assertEquals(15.0, updatedRecent?.amount ?: 0.0, 0.001)
            assertEquals(99.0, untouchedOld?.amount ?: 0.0, 0.001)
        }
    }

    @Test
    fun sendMessage_localWriteReplyWithParagraphsSplitsIntoMultipleBubbles() {
        runBlocking {
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(),
                aiChatGateway = CountingAiChatGateway(),
            )

            repository.sendMessage(
                userInput = "今天打车花了12",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantMessages = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }

            assertEquals(true, assistantMessages.size >= 2)
            assertEquals(false, assistantMessages.first().isReceipt)
            assertEquals(true, assistantMessages.last().isReceipt)
        }
    }

    @Test
    fun sendMessage_statsPastWeekTotalSpendReturnsNaturalSummary() {
        runBlocking {
            val now = System.currentTimeMillis()
            val gateway = CountingAiChatGateway()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 51L,
                            type = 0,
                            amount = 20.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "午饭",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                        TransactionEntity(
                            id = 52L,
                            type = 0,
                            amount = 30.0,
                            categoryName = "交通出行",
                            categoryIcon = "",
                            remark = "打车",
                            recordTimestamp = now - 2_000,
                            createdTimestamp = now - 2_000,
                        ),
                    ),
                ),
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "统计过去一周花了多少钱",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = stripHiddenPayloads(assistantText)

            assertEquals(0, gateway.requestCount)
            assertEquals(true, visibleAssistantText.contains("过去一周"))
            assertEquals(true, visibleAssistantText.contains("总共花了") || visibleAssistantText.contains("总花费"))
            assertEquals(true, visibleAssistantText.contains("**交通出行**"))
            assertEquals(false, visibleAssistantText.contains("topAmount="))
            assertEquals(false, visibleAssistantText.contains("结构化结果"))
        }
    }

    @Test
    fun sendMessage_pastWeekSpendQuestionWithoutStatsKeywordRoutesToLocalStats() {
        runBlocking {
            val now = System.currentTimeMillis()
            val gateway = CountingAiChatGateway()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(
                    initialTransactions = listOf(
                        TransactionEntity(
                            id = 61L,
                            type = 0,
                            amount = 18.0,
                            categoryName = "餐饮美食",
                            categoryIcon = "",
                            remark = "午饭",
                            recordTimestamp = now - 1_000,
                            createdTimestamp = now - 1_000,
                        ),
                        TransactionEntity(
                            id = 62L,
                            type = 0,
                            amount = 22.0,
                            categoryName = "交通出行",
                            categoryIcon = "",
                            remark = "打车",
                            recordTimestamp = now - 2_000,
                            createdTimestamp = now - 2_000,
                        ),
                    ),
                ),
                aiChatGateway = gateway,
            )

            repository.sendMessage(
                userInput = "过去一周花了多少钱",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = stripHiddenPayloads(assistantText)

            assertEquals(0, gateway.requestCount)
            assertEquals(true, visibleAssistantText.contains("过去一周"))
            assertEquals(true, visibleAssistantText.contains("总共花了") || visibleAssistantText.contains("总花费"))
            assertEquals(false, visibleAssistantText.contains("结构化结果"))
        }
    }

    @Test
    fun sendMessage_fallbackLongParagraphWithoutDelimiters_keepsSingleAssistantBubble() {
        runBlocking {
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = FakeTransactionDao(),
                aiChatGateway = FakeAiChatGateway(
                    "这是一段比较长的回复但是没有双换行分隔所以应该保持在一个气泡里显示避免阅读时被硬切成多个碎片并且句意仍然完整。",
                ),
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "随便聊聊",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantMessages = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }

            assertEquals(1, assistantMessages.size)
        }
    }

    @Test
    fun sendMessage_markdownJsonReceiptBlock_parsesAndDoesNotLeakJsonText() {
        runBlocking {
            val chatMessageDao = FakeChatMessageDao()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = transactionDao,
                aiChatGateway = FakeAiChatGateway(
                    """
                    好的，我来处理。

                    ```json
                    {"isReceipt":true,"action":"create","amount":50,"category":"交通出行","desc":"打车"}
                    ```
                    """.trimIndent(),
                ),
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "帮我记一笔打车 50",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = assistantText.replace(
                Regex("<RECEIPT>[\\s\\S]*?</RECEIPT>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
                "",
            )

            assertEquals(1, transactionDao.countTransactions())
            assertEquals(false, assistantText.contains("```"))
            assertEquals(false, assistantText.contains("<DATA>"))
            assertEquals(false, assistantText.contains("</DATA>"))
            assertEquals(false, visibleAssistantText.contains("\"isReceipt\""))
        }
    }

    @Test
    fun sendMessage_splitDataTagAcrossDeltas_appliesReceiptWithoutTagLeak() {
        runBlocking {
            val chatMessageDao = FakeChatMessageDao()
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = transactionDao,
                aiChatGateway = MultiDeltaAiChatGateway(
                    deltas = listOf(
                        "好的，",
                        "<DA",
                        "TA>{\"isReceipt\":true,\"action\":\"create\",\"amount\":30,\"category\":\"餐饮美食\",\"desc\":\"晚饭\"}</DATA>",
                    ),
                ),
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "帮我记一笔晚饭 30",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }

            assertEquals(1, transactionDao.countTransactions())
            assertEquals(false, assistantText.contains("<DA"))
            assertEquals(false, assistantText.contains("</DATA>"))
        }
    }

    @Test
    fun sendMessage_streamingDeltas_flushesAssistantMessageOnlyAfterCompleted() {
        runBlocking {
            val chatMessageDao = FakeChatMessageDao()
            val repository = ChatRepository(
                chatMessageDao = chatMessageDao,
                transactionDao = FakeTransactionDao(),
                aiChatGateway = MultiDeltaAiChatGateway(
                    deltas = listOf("你", "好", "呀"),
                ),
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "随便聊聊",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantMessages = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }

            assertEquals(1, assistantMessages.size)
            assertEquals("你好呀", assistantMessages.first().content)
            assertEquals(2, chatMessageDao.insertCount)
            assertEquals(0, chatMessageDao.updateCount)
        }
    }

    @Test
    fun sendMessage_inlineJsonWithoutTags_parsesAndDoesNotLeakJsonText() {
        runBlocking {
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = FakeAiChatGateway(
                    """
                    好的，我来处理。
                    {"isReceipt":true,"action":"create","amount":66,"category":"交通出行","desc":"打车"}
                    已经帮你记好了。
                    """.trimIndent(),
                ),
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "打车66",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = assistantText.replace(
                Regex("<RECEIPT>[\\s\\S]*?</RECEIPT>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
                "",
            )

            assertEquals(1, transactionDao.countTransactions())
            assertEquals(1, repository.observeChatRecords().first().count { it.role == "assistant" && it.isReceipt })
            assertEquals(false, visibleAssistantText.contains("\"isReceipt\""))
            assertEquals(false, visibleAssistantText.contains("\"amount\""))
            assertEquals(false, visibleAssistantText.contains("{"))
            assertEquals(false, visibleAssistantText.contains("}"))
        }
    }

    @Test
    fun sendMessage_unclosedInlineJsonFragment_doesNotLeakJsonText() {
        runBlocking {
            val transactionDao = FakeTransactionDao()
            val repository = ChatRepository(
                chatMessageDao = FakeChatMessageDao(),
                transactionDao = transactionDao,
                aiChatGateway = FakeAiChatGateway(
                    """
                    我来帮你记。
                    {"isReceipt":true,"action":"create","amount":66
                    """.trimIndent(),
                ),
                agentDefaultPathEnabled = false,
            )

            repository.sendMessage(
                userInput = "打车66",
                aiConfig = AiAssistantConfig(),
                userName = "测试用户",
            )

            val assistantText = repository.observeChatRecords()
                .first()
                .filter { it.role == "assistant" }
                .joinToString("\n") { it.content }
            val visibleAssistantText = assistantText.replace(
                Regex("<RECEIPT>[\\s\\S]*?</RECEIPT>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)),
                "",
            )

            assertEquals(0, transactionDao.countTransactions())
            assertEquals(false, visibleAssistantText.contains("\"isReceipt\""))
            assertEquals(false, visibleAssistantText.contains("{\""))
        }
    }
}

private fun stripHiddenPayloads(text: String): String {
    return text
        .replace(Regex("<NOTE>[\\s\\S]*?</NOTE>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<RECEIPT>[\\s\\S]*?</RECEIPT>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .replace(Regex("<DATA>[\\s\\S]*?</DATA>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE)), "")
        .trim()
}

private class FakeAiChatGateway(
    private val reply: String,
) : AiChatGateway {
    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        emit(AiStreamEvent.TextDelta(reply))
        emit(AiStreamEvent.Completed)
    }
}

private class CountingAiChatGateway(
    private val reply: String = "unused",
) : AiChatGateway {
    var requestCount: Int = 0

    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        requestCount += 1
        emit(AiStreamEvent.TextDelta(reply))
        emit(AiStreamEvent.Completed)
    }
}

private class MultiDeltaAiChatGateway(
    private val deltas: List<String>,
) : AiChatGateway {
    override fun streamReply(request: AiChatRequest): Flow<AiStreamEvent> = flow {
        deltas.forEach { delta ->
            emit(AiStreamEvent.TextDelta(delta))
        }
        emit(AiStreamEvent.Completed)
    }
}

private class FakeChatMessageDao : ChatMessageDao {
    private val messages = mutableListOf<ChatMessageEntity>()
    private val messagesFlow = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    private var nextId = 1L
    var insertCount: Int = 0
        private set
    var updateCount: Int = 0
        private set

    override suspend fun insertMessage(message: ChatMessageEntity): Long {
        val stored = message.copy(id = if (message.id == 0L) nextId++ else message.id)
        messages += stored
        insertCount += 1
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
            updateCount += 1
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

    private fun publish() {
        transactionsFlow.value = transactions.sortedByDescending { it.recordTimestamp }
    }
}
