package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.remote.siliconflow.SiliconFlowApi
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatRequestDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowChatResponseDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowMessageDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowToolChoiceDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowToolChoiceFunctionDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowToolDto
import com.qcb.keepaccounts.data.remote.siliconflow.model.SiliconFlowToolSchemaDto
import com.qcb.keepaccounts.domain.agent.AgentPlanner
import com.qcb.keepaccounts.domain.agent.AgentToolArgs
import com.qcb.keepaccounts.domain.agent.IntentPlanV2
import com.qcb.keepaccounts.domain.agent.PlannerInputV2
import com.qcb.keepaccounts.domain.agent.PlannerIntentType
import com.qcb.keepaccounts.domain.agent.PlannerPromptProfile
import com.qcb.keepaccounts.domain.agent.PlannerRiskLevel
import com.qcb.keepaccounts.domain.agent.PlannerTargetMode
import com.qcb.keepaccounts.domain.agent.PreviewActionItem
import com.qcb.keepaccounts.domain.agent.TransactionFilter
import org.json.JSONArray
import org.json.JSONObject

class SiliconFlowPlannerGateway(
    private val api: SiliconFlowApi,
    private val model: String = DEFAULT_MODEL,
    private val promptProfile: PlannerPromptProfile = PlannerPromptProfile.PRO,
) : AgentPlanner {

    override suspend fun plan(input: PlannerInputV2): IntentPlanV2? {
        val request = SiliconFlowChatRequestDto(
            model = model,
            messages = buildPlannerMessages(input),
            temperature = 0.0,
            stream = false,
            tools = listOf(buildPlannerToolSchema()),
            toolChoice = SiliconFlowToolChoiceDto(
                function = SiliconFlowToolChoiceFunctionDto(name = PLANNER_FUNCTION_NAME),
            ),
            maxTokens = 480,
        )

        val response = runCatching { api.chatCompletions(request) }.getOrNull() ?: return null
        val payload = extractPlannerPayload(response) ?: return null
        return parseIntentPlan(payload)
    }

    private fun buildPlannerMessages(input: PlannerInputV2): List<SiliconFlowMessageDto> {
        val systemPrompt = when (promptProfile) {
            PlannerPromptProfile.LITE -> {
                """
                    你是 KeepAccounts 的轻量 planner，只输出结构化函数参数，不输出额外解释。
                    你优先处理单动作、低歧义请求（create/update/query），并尽量填充 queryArgs 或 writeItems。
                    遇到 delete、批量、多目标、复杂相对时间、统计趋势类请求时，intent 必须输出 unknown，confidence 低于 0.8。
                """.trimIndent()
            }

            PlannerPromptProfile.PRO -> {
                """
                    你是 KeepAccounts 的 planner，只输出结构化函数参数，不输出额外解释。
                    你必须根据用户输入判断 intent，并尽量填充 queryArgs/statsArgs/writeItems。
                    如果无法确定，intent 填 unknown，confidence 低于 0.5。
                """.trimIndent()
            }
        }

        val userPrompt = """
            请求ID: ${input.requestId}
            当前时间戳: ${input.nowMillis}
            时区: ${input.timezoneId}
            用户输入: ${input.userInput}
        """.trimIndent()

        return listOf(
            SiliconFlowMessageDto(role = "system", content = systemPrompt),
            SiliconFlowMessageDto(role = "user", content = userPrompt),
        )
    }

    private fun buildPlannerToolSchema(): SiliconFlowToolDto {
        val schema = mapOf<String, Any?>(
            "type" to "object",
            "properties" to mapOf(
                "intent" to mapOf(
                    "type" to "string",
                    "enum" to listOf(
                        "create_transactions",
                        "update_transactions",
                        "delete_transactions",
                        "query_transactions",
                        "query_spending_stats",
                        "chitchat",
                        "unknown",
                    ),
                ),
                "confidence" to mapOf("type" to "number"),
                "targetMode" to mapOf("type" to "string", "enum" to listOf("single", "set", "top_n")),
                "riskLevel" to mapOf("type" to "string", "enum" to listOf("low", "medium", "high")),
                "needsConfirmation" to mapOf("type" to "boolean"),
                "missingSlots" to mapOf("type" to "array", "items" to mapOf("type" to "string")),
                "queryArgs" to mapOf("type" to "object"),
                "statsArgs" to mapOf("type" to "object"),
                "writeItems" to mapOf("type" to "array", "items" to mapOf("type" to "object")),
                "createItems" to mapOf("type" to "array", "items" to mapOf("type" to "object")),
            ),
            "required" to listOf("intent", "confidence"),
            "additionalProperties" to true,
        )

        return SiliconFlowToolDto(
            function = SiliconFlowToolSchemaDto(
                name = PLANNER_FUNCTION_NAME,
                description = "根据用户输入产出 IntentPlanV2",
                parameters = schema,
            ),
        )
    }

    private fun extractPlannerPayload(response: SiliconFlowChatResponseDto): String? {
        val message = response.choices.firstOrNull()?.message ?: return null

        val toolPayload = message.toolCalls
            ?.firstOrNull { call ->
                call.function?.name?.equals(PLANNER_FUNCTION_NAME, ignoreCase = true) == true
            }
            ?.function
            ?.arguments
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        if (!toolPayload.isNullOrBlank()) {
            return toolPayload
        }

        val content = message.content?.trim().orEmpty()
        if (content.isBlank()) return null

        val normalized = content
            .removePrefix("```json")
            .removePrefix("```")
            .removeSuffix("```")
            .trim()

        if (normalized.startsWith("{")) {
            return normalized
        }

        val start = normalized.indexOf('{')
        val end = normalized.lastIndexOf('}')
        return if (start >= 0 && end > start) {
            normalized.substring(start, end + 1)
        } else {
            null
        }
    }

    private fun parseIntentPlan(payload: String): IntentPlanV2? {
        return runCatching {
            val root = JSONObject(payload)
            val intent = parseIntent(root.optString("intent"))
            val confidence = root.optDouble("confidence", 0.0).coerceIn(0.0, 1.0)
            val targetMode = parseTargetMode(root.optString("targetMode"))
            val riskLevel = parseRiskLevel(root.optString("riskLevel"))
            val needsConfirmation = root.optBoolean("needsConfirmation", false)
            val missingSlots = root.optJSONArray("missingSlots").toStringList()

            IntentPlanV2(
                intent = intent,
                confidence = confidence,
                targetMode = targetMode,
                riskLevel = riskLevel,
                needsConfirmation = needsConfirmation,
                missingSlots = missingSlots,
                queryArgs = root.optJSONObject("queryArgs")?.toQueryArgs(),
                statsArgs = root.optJSONObject("statsArgs")?.toStatsArgs(),
                writeItems = root.optJSONArray("writeItems").toCreateItems(),
                createItems = root.optJSONArray("createItems").toCreateItems(),
            )
        }.getOrNull()
    }

    private fun parseIntent(raw: String?): PlannerIntentType {
        return when (raw?.trim()?.lowercase()) {
            "create_transactions" -> PlannerIntentType.CREATE_TRANSACTIONS
            "update_transactions" -> PlannerIntentType.UPDATE_TRANSACTIONS
            "delete_transactions" -> PlannerIntentType.DELETE_TRANSACTIONS
            "query_transactions" -> PlannerIntentType.QUERY_TRANSACTIONS
            "query_spending_stats" -> PlannerIntentType.QUERY_SPENDING_STATS
            "chitchat" -> PlannerIntentType.CHITCHAT
            else -> PlannerIntentType.UNKNOWN
        }
    }

    private fun parseTargetMode(raw: String?): PlannerTargetMode {
        return when (raw?.trim()?.lowercase()) {
            "set" -> PlannerTargetMode.SET
            "top_n", "topn", "top-n" -> PlannerTargetMode.TOP_N
            else -> PlannerTargetMode.SINGLE
        }
    }

    private fun parseRiskLevel(raw: String?): PlannerRiskLevel {
        return when (raw?.trim()?.lowercase()) {
            "high" -> PlannerRiskLevel.HIGH
            "medium" -> PlannerRiskLevel.MEDIUM
            else -> PlannerRiskLevel.LOW
        }
    }

    private fun JSONObject.toQueryArgs(): AgentToolArgs.QueryTransactionsArgs {
        val filtersObj = optJSONObject("filters")
        val filters = TransactionFilter(
            transactionId = filtersObj?.optLongOrNull("transactionId"),
            dateKeyword = filtersObj?.optStringOrNull("dateKeyword"),
            keyword = filtersObj?.optStringOrNull("keyword"),
            amountMin = filtersObj?.optDoubleOrNull("amountMin"),
            amountMax = filtersObj?.optDoubleOrNull("amountMax"),
        )

        return AgentToolArgs.QueryTransactionsArgs(
            filters = filters,
            window = optStringOrNull("window") ?: "last30days",
            sortKey = optStringOrNull("sortKey") ?: "record_time_desc",
            limit = optInt("limit", 20),
            startAtMillis = optLongOrNull("startAtMillis"),
            endAtMillis = optLongOrNull("endAtMillis"),
        )
    }

    private fun JSONObject.toStatsArgs(): AgentToolArgs.QuerySpendingStatsArgs {
        return AgentToolArgs.QuerySpendingStatsArgs(
            window = optStringOrNull("window") ?: "last30days",
            groupBy = optStringOrNull("groupBy") ?: "category",
            metric = optStringOrNull("metric") ?: "total_amount",
            sortKey = optStringOrNull("sortKey") ?: "value_desc",
            topN = optInt("topN", 5),
            startAtMillis = optLongOrNull("startAtMillis"),
            endAtMillis = optLongOrNull("endAtMillis"),
        )
    }

    private fun JSONArray?.toCreateItems(): List<PreviewActionItem> {
        if (this == null || length() == 0) return emptyList()

        return buildList {
            for (index in 0 until length()) {
                val item = optJSONObject(index) ?: continue
                add(
                    PreviewActionItem(
                        action = item.optStringOrNull("action") ?: "create",
                        amount = item.optDoubleOrNull("amount"),
                        category = item.optStringOrNull("category"),
                        recordTime = item.optStringOrNull("recordTime"),
                        date = item.optStringOrNull("date"),
                        desc = item.optStringOrNull("desc"),
                        transactionId = item.optLongOrNull("transactionId"),
                    ),
                )
            }
        }
    }

    private fun JSONArray?.toStringList(): List<String> {
        if (this == null || length() == 0) return emptyList()
        return buildList {
            for (index in 0 until length()) {
                val value = optString(index).trim()
                if (value.isNotBlank()) {
                    add(value)
                }
            }
        }
    }

    private fun JSONObject.optStringOrNull(key: String): String? {
        if (!has(key) || isNull(key)) return null
        return optString(key).trim().takeIf { it.isNotBlank() }
    }

    private fun JSONObject.optDoubleOrNull(key: String): Double? {
        if (!has(key) || isNull(key)) return null
        return optDouble(key)
    }

    private fun JSONObject.optLongOrNull(key: String): Long? {
        if (!has(key) || isNull(key)) return null
        return optLong(key).takeIf { it > 0L }
    }

    companion object {
        private const val DEFAULT_MODEL = "deepseek-ai/DeepSeek-V3"
        private const val PLANNER_FUNCTION_NAME = "submit_intent_plan_v2"
    }
}
