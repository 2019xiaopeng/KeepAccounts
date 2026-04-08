package com.qcb.keepaccounts.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import kotlin.math.abs

class QueryDslEngineTest {

    private val fixedNow = Calendar.getInstance().apply {
        set(2026, Calendar.APRIL, 8, 12, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private val builder = QueryDslBuilder(nowProvider = { fixedNow })
    private val executor = QueryDslExecutor()

    @Test
    fun queryTransactions_recentOne_returnsLatestRecord() {
        val dsl = builder.buildTransactions(
            AgentToolArgs.QueryTransactionsArgs(
                filters = TransactionFilter(),
                window = "last30days",
                sortKey = "record_time_desc",
                limit = 1,
            ),
        )

        val result = executor.executeTransactions(dsl, sampleTransactions())

        assertEquals(1, result.items.size)
        assertEquals(1L, result.items.first().transactionId)
        assertEquals("last30days", result.explainability.timeWindow)
        assertEquals("record_time_desc", result.explainability.sortKey)
        assertEquals("none", result.explainability.aggregationMethod)
    }

    @Test
    fun queryTransactions_last7DaysHighestExpense_returnsTopAmountInWindow() {
        val dsl = builder.buildTransactions(
            AgentToolArgs.QueryTransactionsArgs(
                filters = TransactionFilter(),
                window = "last7days",
                sortKey = "amount_desc",
                limit = 1,
            ),
        )

        val result = executor.executeTransactions(dsl, sampleTransactions())

        assertEquals(1, result.items.size)
        assertEquals(3L, result.items.first().transactionId)
        assertEquals(65.0, result.items.first().amount, 0.001)
    }

    @Test
    fun querySpendingStats_mostFrequentConsumption_returnsTopCategoryByFrequency() {
        val dsl = builder.buildStats(
            AgentToolArgs.QuerySpendingStatsArgs(
                window = "last30days",
                groupBy = "category",
                metric = "frequency",
                sortKey = "frequency_desc",
                topN = 1,
            ),
        )

        val result = executor.executeStats(dsl, sampleTransactions())

        assertEquals(1, result.buckets.size)
        assertEquals("餐饮美食", result.buckets.first().key)
        assertEquals(4.0, result.buckets.first().value, 0.001)
        assertEquals("count", result.explainability.aggregationMethod)
    }

    @Test
    fun querySpendingStats_monthlyCategoryRatio_returnsExplainableShareBuckets() {
        val start = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 1, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val end = Calendar.getInstance().apply {
            set(2026, Calendar.APRIL, 30, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis

        val dsl = builder.buildStats(
            AgentToolArgs.QuerySpendingStatsArgs(
                window = "custom",
                groupBy = "category",
                metric = "category_ratio",
                sortKey = "value_desc",
                topN = 5,
                startAtMillis = start,
                endAtMillis = end,
            ),
        )

        val result = executor.executeStats(dsl, sampleTransactions())

        assertTrue(result.buckets.isNotEmpty())
        assertEquals("餐饮美食", result.buckets.first().key)
        assertTrue(result.explainability.timeWindow.contains("2026-04-01"))
        assertEquals("share_of_total_amount", result.explainability.aggregationMethod)

        val ratioSum = result.buckets.sumOf { it.value }
        assertTrue(abs(ratioSum - 1.0) < 0.001)
    }

    @Test
    fun querySpendingStats_mostFrequentMerchant_returnsTopMerchantByRemark() {
        val dsl = builder.buildStats(
            AgentToolArgs.QuerySpendingStatsArgs(
                window = "last30days",
                groupBy = "merchant",
                metric = "frequency",
                sortKey = "frequency_desc",
                topN = 1,
            ),
        )

        val source = listOf(
            transaction(id = 101L, amount = 19.0, category = "餐饮美食", remark = "瑞幸咖啡", daysAgo = 1, hour = 9),
            transaction(id = 102L, amount = 21.0, category = "餐饮美食", remark = "瑞幸咖啡", daysAgo = 2, hour = 9),
            transaction(id = 103L, amount = 24.0, category = "餐饮美食", remark = "星巴克", daysAgo = 3, hour = 10),
        )

        val result = executor.executeStats(dsl, source)

        assertEquals(1, result.buckets.size)
        assertEquals("瑞幸咖啡", result.buckets.first().key)
        assertEquals(2.0, result.buckets.first().value, 0.001)
    }

    private fun sampleTransactions(): List<LedgerTransactionSnapshot> {
        return listOf(
            transaction(id = 1L, amount = 12.0, category = "餐饮美食", remark = "早餐", daysAgo = 0, hour = 9),
            transaction(id = 2L, amount = 38.0, category = "餐饮美食", remark = "晚饭", daysAgo = 1, hour = 19),
            transaction(id = 3L, amount = 65.0, category = "交通出行", remark = "打车", daysAgo = 2, hour = 8),
            transaction(id = 4L, amount = 45.0, category = "餐饮美食", remark = "午餐", daysAgo = 4, hour = 12),
            transaction(id = 5L, amount = 120.0, category = "购物消费", remark = "外套", daysAgo = 20, hour = 10),
            transaction(id = 6L, amount = 28.0, category = "餐饮美食", remark = "奶茶", daysAgo = 6, hour = 18),
            transaction(id = 7L, amount = 999.0, category = "购物消费", remark = "旧账单", daysAgo = 45, hour = 11),
        )
    }

    private fun transaction(
        id: Long,
        amount: Double,
        category: String,
        remark: String,
        daysAgo: Int,
        hour: Int,
    ): LedgerTransactionSnapshot {
        val timestamp = Calendar.getInstance().apply {
            timeInMillis = fixedNow
            add(Calendar.DAY_OF_YEAR, -daysAgo)
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis

        return LedgerTransactionSnapshot(
            transactionId = id,
            type = 0,
            amount = amount,
            categoryName = category,
            remark = remark,
            recordTimestamp = timestamp,
        )
    }
}
