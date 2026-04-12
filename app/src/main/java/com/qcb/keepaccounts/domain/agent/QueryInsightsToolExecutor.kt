package com.qcb.keepaccounts.domain.agent

import org.json.JSONArray
import org.json.JSONObject

data class QueryToolCallResult<T>(
    val status: AgentToolStatus,
    val result: T? = null,
    val validationIssues: List<AgentValidationIssue> = emptyList(),
    val resultJson: String,
)

class QueryInsightsToolExecutor(
    private val sourceProvider: suspend () -> List<LedgerTransactionSnapshot>,
    private val validator: AgentToolValidator = AgentToolValidator(),
    private val dslBuilder: QueryDslBuilder = QueryDslBuilder(),
    private val dslExecutor: QueryDslExecutor = QueryDslExecutor(),
) {

    suspend fun queryTransactions(args: AgentToolArgs.QueryTransactionsArgs): QueryToolCallResult<QueryTransactionsResult> {
        val issues = validator.validateQueryArgs(args)
        if (issues.isNotEmpty()) {
            return QueryToolCallResult(
                status = AgentToolStatus.FAILURE,
                validationIssues = issues,
                resultJson = buildValidationFailureJson(issues),
            )
        }

        val dsl = dslBuilder.buildTransactions(args)
        val source = sourceProvider()
        val result = dslExecutor.executeTransactions(dsl, source)

        return QueryToolCallResult(
            status = AgentToolStatus.SUCCESS,
            result = result,
            resultJson = buildTransactionsResultJson(result),
        )
    }

    suspend fun querySpendingStats(args: AgentToolArgs.QuerySpendingStatsArgs): QueryToolCallResult<QuerySpendingStatsResult> {
        val issues = validator.validateStatsArgs(args)
        if (issues.isNotEmpty()) {
            return QueryToolCallResult(
                status = AgentToolStatus.FAILURE,
                validationIssues = issues,
                resultJson = buildValidationFailureJson(issues),
            )
        }

        val dsl = dslBuilder.buildStats(args)
        val source = sourceProvider()
        val result = dslExecutor.executeStats(dsl, source)

        return QueryToolCallResult(
            status = AgentToolStatus.SUCCESS,
            result = result,
            resultJson = buildStatsResultJson(result),
        )
    }

    private fun buildValidationFailureJson(issues: List<AgentValidationIssue>): String {
        val errors = JSONArray().apply {
            issues.forEach { issue ->
                put(
                    JSONObject().apply {
                        put("field", issue.field)
                        put("code", issue.code.name)
                        put("message", issue.message)
                    },
                )
            }
        }
        return JSONObject()
            .put("status", "failure")
            .put("errors", errors)
            .toString()
    }

    private fun buildTransactionsResultJson(result: QueryTransactionsResult): String {
        val items = JSONArray().apply {
            result.items.forEach { item ->
                put(
                    JSONObject().apply {
                        put("transactionId", item.transactionId)
                        put("type", item.type)
                        put("amount", item.amount)
                        put("category", item.categoryName)
                        put("remark", item.remark)
                        put("recordTimestamp", item.recordTimestamp)
                    },
                )
            }
        }

        return JSONObject().apply {
            put("status", "success")
            put("items", items)
            put("sampleSize", result.explainability.sampleSize)
            put("timeWindow", result.explainability.timeWindow)
            put("sortKey", result.explainability.sortKey)
            put("aggregationMethod", result.explainability.aggregationMethod)
        }.toString()
    }

    private fun buildStatsResultJson(result: QuerySpendingStatsResult): String {
        val buckets = JSONArray().apply {
            result.buckets.forEach { bucket ->
                put(
                    JSONObject().apply {
                        put("key", bucket.key)
                        put("value", bucket.value)
                        put("sampleSize", bucket.sampleSize)
                    },
                )
            }
        }

        return JSONObject().apply {
            put("status", "success")
            put("buckets", buckets)
            put("sampleSize", result.explainability.sampleSize)
            put("timeWindow", result.explainability.timeWindow)
            put("sortKey", result.explainability.sortKey)
            put("aggregationMethod", result.explainability.aggregationMethod)
        }.toString()
    }
}
