package com.qcb.keepaccounts.domain.agent

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

        if (facts.failureCount == 0 && facts.successCount == 1) {
            return when (action) {
                "update" -> listOf(
                    "好嘞，这笔我已经帮你改好了。",
                    "你要是想改时间或分类，也可以继续告诉我。",
                ).joinToString("\n\n")

                "delete" -> listOf(
                    "收到，这条记录已经帮你删除。",
                    "如果删错了，我也可以帮你按条件重新补回去。",
                ).joinToString("\n\n")

                else -> listOf(
                    buildCreateSuccessLine(sceneHint),
                    buildCreateCareLine(sceneHint),
                ).joinToString("\n\n")
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
}
