package com.qcb.keepaccounts.domain.agent

import java.util.Locale

data class AgentWriteStyleFacts(
    val successCount: Int,
    val failureCount: Int,
    val createCount: Int,
    val updateCount: Int,
    val deleteCount: Int,
    val errors: List<String>,
    val primaryAction: String? = null,
    val primaryCategory: String? = null,
    val primaryDesc: String? = null,
    val primaryAmount: Double? = null,
    val userInput: String? = null,
)

class AgentStyleFormatter {

    fun formatQuery(
        structuredFacts: String,
        explainabilityLine: String,
        caringLine: String,
    ): String {
        return listOf(
            structuredFacts.trim(),
            explainabilityLine.trim(),
            caringLine.trim(),
        ).filter { it.isNotBlank() }.joinToString(separator = "\n\n")
    }

    fun formatStats(
        structuredFacts: String,
        explainabilityLine: String,
        caringLine: String,
    ): String {
        return listOf(
            structuredFacts.trim(),
            explainabilityLine.trim(),
            caringLine.trim(),
        ).filter { it.isNotBlank() }.joinToString(separator = "\n\n")
    }

    fun formatWrite(
        facts: AgentWriteStyleFacts,
        requestId: String,
    ): String {
        val action = facts.primaryAction.orEmpty().trim().lowercase()
        val sceneHint = listOfNotNull(facts.primaryCategory, facts.primaryDesc).joinToString(" ")
        val empathyLine = buildLifeShareAck(facts.userInput.orEmpty())

        if (facts.failureCount == 0 && facts.successCount == 1) {
            return when (action) {
                "update" -> listOf(
                    empathyLine.orEmpty(),
                    buildUpdateSuccessLine(facts),
                    "你要是想改时间或分类，也可以继续告诉我。",
                ).filter { it.isNotBlank() }.joinToString("\n\n")

                "delete" -> listOf(
                    empathyLine.orEmpty(),
                    "收到，这条记录已经帮你删除。",
                    "如果删错了，我也可以帮你按条件重新补回去。",
                ).filter { it.isNotBlank() }.joinToString("\n\n")

                else -> listOf(
                    empathyLine.orEmpty(),
                    buildCreateSuccessLine(sceneHint),
                    buildCreateCareLine(sceneHint),
                ).filter { it.isNotBlank() }.joinToString("\n\n")
            }
        }

        if (facts.failureCount == 0 && facts.successCount > 1) {
            return listOf(
                "这批我已经帮你处理好了，共 ${facts.successCount} 笔。",
                "要不要我再帮你看看这段时间哪一类花销最多？",
            ).joinToString("\n\n")
        }

        if (facts.successCount > 0 && facts.failureCount > 0) {
            return listOf(
                "我先帮你处理好了 ${facts.successCount} 笔，还有 ${facts.failureCount} 笔差一点点信息就能完成。",
                "点“去手动补全”我会陪你一起把剩下的补齐，不会丢下你的。",
            ).joinToString("\n\n")
        }

        return listOf(
            "哎呀，这次还差一点点信息就能记好啦。",
            "你告诉我金额和分类（比如餐饮美食、交通出行）中的缺项，我马上帮你补上。",
            "别担心，我会一直跟着你把这笔记完整。",
        ).joinToString("\n\n")
    }

    fun formatFallback(
        reason: String,
        requestId: String,
    ): String {
        return listOf(
            "我先换一种方式继续帮你处理这件事。",
            "如果你愿意，我也可以把这一步拆得更细一点再来一次。",
        ).joinToString(separator = "\n\n")
    }

    private fun buildCreateSuccessLine(sceneHint: String): String {
        return when {
            sceneHint.contains("打车") || sceneHint.contains("交通") -> "好嘞，这笔打车我记好了。"
            sceneHint.contains("餐饮") || sceneHint.contains("晚饭") || sceneHint.contains("中饭") ||
                sceneHint.contains("午饭") || sceneHint.contains("早餐") ||
                sceneHint.contains("吃饭") || sceneHint.contains("吃中饭") ||
                sceneHint.contains("夜宵") || sceneHint.contains("宵夜") -> "收到，这笔吃饭开销已经帮你记上啦。"
            sceneHint.contains("药") || sceneHint.contains("医疗") || sceneHint.contains("健康") -> "这笔医疗相关支出我已经记好了。"
            else -> "好嘞，这笔账我已经帮你记好了。"
        }
    }

    private fun buildCreateCareLine(sceneHint: String): String {
        return when {
            sceneHint.contains("打车") || sceneHint.contains("交通") -> "这是回家还是出去玩呀？我可以顺便帮你看出行开销趋势。"
            sceneHint.contains("餐饮") || sceneHint.contains("晚饭") || sceneHint.contains("中饭") ||
                sceneHint.contains("午饭") || sceneHint.contains("早餐") ||
                sceneHint.contains("吃饭") || sceneHint.contains("吃中饭") ||
                sceneHint.contains("夜宵") || sceneHint.contains("宵夜") -> "记得按时吃饭呀，要不要我顺便统计下这周餐饮花费？"
            sceneHint.contains("药") || sceneHint.contains("医疗") || sceneHint.contains("健康") -> "要照顾好自己，我也可以帮你盯着这类支出变化。"
            else -> "后面有新账单也直接告诉我，我会继续帮你整理。"
        }
    }

    private fun buildUpdateSuccessLine(facts: AgentWriteStyleFacts): String {
        val desc = facts.primaryDesc
            ?.trim()
            ?.takeIf { it.isNotBlank() }
            ?.take(24)
        val category = facts.primaryCategory
            ?.trim()
            ?.takeIf { it.isNotBlank() }
        val amountText = facts.primaryAmount
            ?.takeIf { it > 0.0 }
            ?.let(::formatAmountForWrite)

        return when {
            !desc.isNullOrBlank() && !amountText.isNullOrBlank() -> "好嘞，已将${desc}修改为${amountText}。"
            !category.isNullOrBlank() && !amountText.isNullOrBlank() -> "好嘞，已将这笔${category}修改为${amountText}。"
            !amountText.isNullOrBlank() -> "好嘞，这笔我已经改成${amountText}。"
            !desc.isNullOrBlank() -> "好嘞，${desc}这笔我已经帮你改好了。"
            else -> "好嘞，这笔我已经帮你改好了。"
        }
    }

    private fun formatAmountForWrite(amount: Double): String {
        val number = if (amount % 1.0 == 0.0) {
            amount.toLong().toString()
        } else {
            String.format(Locale.CHINA, "%.2f", amount)
                .trimEnd('0')
                .trimEnd('.')
        }
        return "${number}元"
    }

    private fun buildLifeShareAck(userInput: String): String? {
        val input = userInput.trim()
        if (input.isBlank()) return null

        return when {
            input.contains("比赛") || input.contains("训练") -> "比赛辛苦啦，先把这笔记好给你稳稳托住。"
            input.contains("考试") || input.contains("面试") -> "辛苦啦，这种时刻我先帮你把账记稳。"
            input.contains("加班") || input.contains("熬夜") -> "今天真的不容易，我先帮你把这笔整理好。"
            input.contains("出差") || input.contains("通勤") -> "在外面奔波辛苦了，这笔我先帮你记上。"
            input.contains("生病") || input.contains("感冒") || input.contains("医院") -> "先照顾好自己，这笔我已经帮你盯住了。"
            input.contains("旅行") || input.contains("旅游") -> "旅途开心也辛苦，这笔我先给你记清楚。"
            else -> null
        }
    }
}
