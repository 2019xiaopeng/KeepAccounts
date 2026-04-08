package com.qcb.keepaccounts.domain.agent

data class AgentWriteStyleFacts(
    val successCount: Int,
    val failureCount: Int,
    val createCount: Int,
    val updateCount: Int,
    val deleteCount: Int,
    val errors: List<String>,
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
        val structured = buildString {
            append("结构化结果：success=")
            append(facts.successCount)
            append("，failure=")
            append(facts.failureCount)
            append("，create=")
            append(facts.createCount)
            append("，update=")
            append(facts.updateCount)
            append("，delete=")
            append(facts.deleteCount)
            if (facts.errors.isNotEmpty()) {
                append("，errors=")
                append(facts.errors.joinToString(separator = " | "))
            }
            append("。")
        }

        val caring = when {
            facts.failureCount == 0 && facts.successCount > 0 -> "已经帮你处理好了，我会继续帮你盯着账本变化。"
            facts.successCount > 0 -> "我先把能处理的都处理了，剩下失败项我们可以逐条修正。"
            else -> "这次没有成功执行，我已经保留错误信息，我们可以马上重试。"
        }

        val trace = "追踪ID：$requestId"
        return listOf(structured, caring, trace).joinToString(separator = "\n\n")
    }

    fun formatFallback(
        reason: String,
        requestId: String,
    ): String {
        val structured = "结构化结果：fallback=true，reason=$reason。"
        val caring = "我先走兜底流程继续服务你，但工具链问题我已经记下了。"
        val trace = "追踪ID：$requestId"
        return listOf(structured, caring, trace).joinToString(separator = "\n\n")
    }
}
