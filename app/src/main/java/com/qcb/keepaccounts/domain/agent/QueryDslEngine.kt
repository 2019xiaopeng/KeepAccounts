package com.qcb.keepaccounts.domain.agent

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private const val DAY_MILLIS = 24L * 60L * 60L * 1000L

enum class QueryWindowPreset(val wireValue: String) {
    TODAY("today"),
    YESTERDAY("yesterday"),
    LAST_7_DAYS("last7days"),
    LAST_30_DAYS("last30days"),
    LAST_12_MONTHS("last12months"),
    CUSTOM("custom"),
}

enum class QuerySortKey(val wireValue: String) {
    RECORD_TIME_DESC("record_time_desc"),
    AMOUNT_DESC("amount_desc"),
    VALUE_DESC("value_desc"),
    FREQUENCY_DESC("frequency_desc"),
}

enum class QueryGroupByDimension(val wireValue: String) {
    CATEGORY("category"),
    TIME_SLOT("timeslot"),
    DAY("day"),
    MONTH("month"),
}

enum class QueryMetricType(val wireValue: String) {
    TOTAL_AMOUNT("total_amount"),
    FREQUENCY("frequency"),
    CATEGORY_RATIO("category_ratio"),
}

data class QueryTimeWindow(
    val preset: QueryWindowPreset,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val label: String,
)

data class QueryExplainability(
    val sampleSize: Int,
    val timeWindow: String,
    val sortKey: String,
    val aggregationMethod: String,
)

data class LedgerTransactionSnapshot(
    val transactionId: Long,
    val type: Int,
    val amount: Double,
    val categoryName: String,
    val remark: String,
    val recordTimestamp: Long,
)

data class QueryTransactionsDsl(
    val filters: TransactionFilter,
    val timeWindow: QueryTimeWindow,
    val sortKey: QuerySortKey,
    val limit: Int,
)

data class QuerySpendingStatsDsl(
    val timeWindow: QueryTimeWindow,
    val groupBy: QueryGroupByDimension,
    val metric: QueryMetricType,
    val sortKey: QuerySortKey,
    val topN: Int,
)

data class QueryTransactionsResult(
    val items: List<LedgerTransactionSnapshot>,
    val explainability: QueryExplainability,
)

data class QueryStatsBucket(
    val key: String,
    val value: Double,
    val sampleSize: Int,
)

data class QuerySpendingStatsResult(
    val buckets: List<QueryStatsBucket>,
    val explainability: QueryExplainability,
)

class QueryDslBuilder(
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {

    fun buildTransactions(args: AgentToolArgs.QueryTransactionsArgs): QueryTransactionsDsl {
        val window = resolveWindow(
            windowValue = args.window,
            startAtMillis = args.startAtMillis,
            endAtMillis = args.endAtMillis,
        )
        return QueryTransactionsDsl(
            filters = args.filters,
            timeWindow = window,
            sortKey = parseSortKey(args.sortKey, default = QuerySortKey.RECORD_TIME_DESC),
            limit = args.limit.coerceIn(1, 100),
        )
    }

    fun buildStats(args: AgentToolArgs.QuerySpendingStatsArgs): QuerySpendingStatsDsl {
        val window = resolveWindow(
            windowValue = args.window,
            startAtMillis = args.startAtMillis,
            endAtMillis = args.endAtMillis,
        )
        return QuerySpendingStatsDsl(
            timeWindow = window,
            groupBy = parseGroupBy(args.groupBy),
            metric = parseMetric(args.metric),
            sortKey = parseSortKey(args.sortKey, default = QuerySortKey.VALUE_DESC),
            topN = args.topN.coerceIn(1, 50),
        )
    }

    private fun parseSortKey(value: String, default: QuerySortKey): QuerySortKey {
        return QuerySortKey.values().firstOrNull { it.wireValue == value } ?: default
    }

    private fun parseGroupBy(value: String): QueryGroupByDimension {
        return QueryGroupByDimension.values().firstOrNull { it.wireValue == value } ?: QueryGroupByDimension.CATEGORY
    }

    private fun parseMetric(value: String): QueryMetricType {
        return QueryMetricType.values().firstOrNull { it.wireValue == value } ?: QueryMetricType.TOTAL_AMOUNT
    }

    private fun resolveWindow(
        windowValue: String,
        startAtMillis: Long?,
        endAtMillis: Long?,
    ): QueryTimeWindow {
        val now = nowProvider()
        val todayStart = startOfDay(now)
        val todayEnd = endOfDay(now)

        return when (windowValue) {
            QueryWindowPreset.TODAY.wireValue -> QueryTimeWindow(
                preset = QueryWindowPreset.TODAY,
                startAtMillis = todayStart,
                endAtMillis = todayEnd,
                label = QueryWindowPreset.TODAY.wireValue,
            )

            QueryWindowPreset.YESTERDAY.wireValue -> QueryTimeWindow(
                preset = QueryWindowPreset.YESTERDAY,
                startAtMillis = todayStart - DAY_MILLIS,
                endAtMillis = todayEnd - DAY_MILLIS,
                label = QueryWindowPreset.YESTERDAY.wireValue,
            )

            QueryWindowPreset.LAST_7_DAYS.wireValue -> QueryTimeWindow(
                preset = QueryWindowPreset.LAST_7_DAYS,
                startAtMillis = todayStart - 6L * DAY_MILLIS,
                endAtMillis = todayEnd,
                label = QueryWindowPreset.LAST_7_DAYS.wireValue,
            )

            QueryWindowPreset.LAST_12_MONTHS.wireValue -> {
                val start = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.MONTH, -11)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                QueryTimeWindow(
                    preset = QueryWindowPreset.LAST_12_MONTHS,
                    startAtMillis = start,
                    endAtMillis = todayEnd,
                    label = QueryWindowPreset.LAST_12_MONTHS.wireValue,
                )
            }

            QueryWindowPreset.CUSTOM.wireValue -> {
                if (startAtMillis != null && endAtMillis != null && startAtMillis <= endAtMillis) {
                    QueryTimeWindow(
                        preset = QueryWindowPreset.CUSTOM,
                        startAtMillis = startAtMillis,
                        endAtMillis = endAtMillis,
                        label = "${formatDate(startAtMillis)}~${formatDate(endAtMillis)}",
                    )
                } else {
                    QueryTimeWindow(
                        preset = QueryWindowPreset.LAST_30_DAYS,
                        startAtMillis = todayStart - 29L * DAY_MILLIS,
                        endAtMillis = todayEnd,
                        label = QueryWindowPreset.LAST_30_DAYS.wireValue,
                    )
                }
            }

            else -> QueryTimeWindow(
                preset = QueryWindowPreset.LAST_30_DAYS,
                startAtMillis = todayStart - 29L * DAY_MILLIS,
                endAtMillis = todayEnd,
                label = QueryWindowPreset.LAST_30_DAYS.wireValue,
            )
        }
    }
}

class QueryDslExecutor {

    fun executeTransactions(
        dsl: QueryTransactionsDsl,
        source: List<LedgerTransactionSnapshot>,
    ): QueryTransactionsResult {
        val filtered = applyCommonFilters(source, dsl.filters, dsl.timeWindow)
        val sorted = when (dsl.sortKey) {
            QuerySortKey.AMOUNT_DESC -> filtered.sortedByDescending { abs(it.amount) }
            else -> filtered.sortedByDescending { it.recordTimestamp }
        }

        return QueryTransactionsResult(
            items = sorted.take(dsl.limit),
            explainability = QueryExplainability(
                sampleSize = filtered.size,
                timeWindow = dsl.timeWindow.label,
                sortKey = dsl.sortKey.wireValue,
                aggregationMethod = "none",
            ),
        )
    }

    fun executeStats(
        dsl: QuerySpendingStatsDsl,
        source: List<LedgerTransactionSnapshot>,
    ): QuerySpendingStatsResult {
        val filtered = applyCommonFilters(source, TransactionFilter(), dsl.timeWindow)
            .filter { it.type == 0 }

        val grouped = filtered.groupBy { transaction ->
            when (dsl.groupBy) {
                QueryGroupByDimension.CATEGORY -> transaction.categoryName.ifBlank { "未分类" }
                QueryGroupByDimension.TIME_SLOT -> resolveTimeSlot(transaction.recordTimestamp)
                QueryGroupByDimension.DAY -> formatDate(transaction.recordTimestamp)
                QueryGroupByDimension.MONTH -> formatMonth(transaction.recordTimestamp)
            }
        }

        val totalAmount = grouped.values.flatten().sumOf { abs(it.amount) }
        val buckets = grouped.map { (key, items) ->
            val amount = items.sumOf { abs(it.amount) }
            val value = when (dsl.metric) {
                QueryMetricType.TOTAL_AMOUNT -> amount
                QueryMetricType.FREQUENCY -> items.size.toDouble()
                QueryMetricType.CATEGORY_RATIO -> if (totalAmount <= 0.0) 0.0 else amount / totalAmount
            }
            QueryStatsBucket(
                key = key,
                value = value,
                sampleSize = items.size,
            )
        }

        val sortedBuckets = when (dsl.sortKey) {
            QuerySortKey.FREQUENCY_DESC -> buckets.sortedWith(
                compareByDescending<QueryStatsBucket> { it.sampleSize }
                    .thenByDescending { it.value }
                    .thenBy { it.key },
            )

            else -> buckets.sortedWith(
                compareByDescending<QueryStatsBucket> { it.value }
                    .thenByDescending { it.sampleSize }
                    .thenBy { it.key },
            )
        }

        return QuerySpendingStatsResult(
            buckets = sortedBuckets.take(dsl.topN),
            explainability = QueryExplainability(
                sampleSize = filtered.size,
                timeWindow = dsl.timeWindow.label,
                sortKey = dsl.sortKey.wireValue,
                aggregationMethod = when (dsl.metric) {
                    QueryMetricType.TOTAL_AMOUNT -> "sum_abs_amount"
                    QueryMetricType.FREQUENCY -> "count"
                    QueryMetricType.CATEGORY_RATIO -> "share_of_total_amount"
                },
            ),
        )
    }

    private fun applyCommonFilters(
        source: List<LedgerTransactionSnapshot>,
        filters: TransactionFilter,
        window: QueryTimeWindow,
    ): List<LedgerTransactionSnapshot> {
        return source.filter { transaction ->
            transaction.recordTimestamp in window.startAtMillis..window.endAtMillis &&
                matchesTransactionId(transaction, filters.transactionId) &&
                matchesKeyword(transaction, filters.keyword) &&
                matchesAmountRange(transaction, filters.amountMin, filters.amountMax) &&
                matchesDateKeyword(transaction.recordTimestamp, filters.dateKeyword)
        }
    }

    private fun matchesTransactionId(
        transaction: LedgerTransactionSnapshot,
        transactionId: Long?,
    ): Boolean {
        return transactionId == null || transaction.transactionId == transactionId
    }

    private fun matchesKeyword(
        transaction: LedgerTransactionSnapshot,
        keyword: String?,
    ): Boolean {
        if (keyword.isNullOrBlank()) return true
        val normalized = keyword.trim().lowercase()
        return transaction.remark.lowercase().contains(normalized) ||
            transaction.categoryName.lowercase().contains(normalized)
    }

    private fun matchesAmountRange(
        transaction: LedgerTransactionSnapshot,
        amountMin: Double?,
        amountMax: Double?,
    ): Boolean {
        val amount = abs(transaction.amount)
        if (amountMin != null && amount < amountMin) return false
        if (amountMax != null && amount > amountMax) return false
        return true
    }

    private fun matchesDateKeyword(timestamp: Long, dateKeyword: String?): Boolean {
        if (dateKeyword.isNullOrBlank()) return true
        val keyword = dateKeyword.trim().lowercase()
        val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
        val todayStart = startOfDay(System.currentTimeMillis())

        return when (keyword) {
            "today", "今天" -> timestamp in todayStart..endOfDay(todayStart)
            "yesterday", "昨天" -> {
                val start = todayStart - DAY_MILLIS
                timestamp in start..endOfDay(start)
            }

            "前天", "day_before_yesterday" -> {
                val start = todayStart - 2L * DAY_MILLIS
                timestamp in start..endOfDay(start)
            }

            else -> formatDate(calendar.timeInMillis) == keyword
        }
    }

    private fun resolveTimeSlot(timestamp: Long): String {
        val hour = Calendar.getInstance().apply { timeInMillis = timestamp }.get(Calendar.HOUR_OF_DAY)
        return when (hour) {
            in 5..10 -> "morning"
            in 11..13 -> "noon"
            in 14..17 -> "afternoon"
            in 18..22 -> "evening"
            else -> "night"
        }
    }
}

private fun startOfDay(timestamp: Long): Long {
    return Calendar.getInstance().apply {
        timeInMillis = timestamp
        set(Calendar.HOUR_OF_DAY, 0)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis
}

private fun endOfDay(timestamp: Long): Long {
    return startOfDay(timestamp) + DAY_MILLIS - 1L
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}

private fun formatMonth(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM", Locale.CHINA).format(Date(timestamp))
}
