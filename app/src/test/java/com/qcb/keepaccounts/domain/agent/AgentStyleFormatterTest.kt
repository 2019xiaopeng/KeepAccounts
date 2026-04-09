package com.qcb.keepaccounts.domain.agent

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AgentStyleFormatterTest {

    @Test
    fun formatQuery_keepsStructuredBeforeExplainabilityAndCaring() {
        val formatter = AgentStyleFormatter()

        val result = formatter.formatQuery(
            structuredFacts = "structured",
            explainabilityLine = "explain",
            caringLine = "care",
        )

        assertTrue(result.startsWith("structured"))
        assertTrue(result.indexOf("structured") < result.indexOf("explain"))
        assertTrue(result.indexOf("explain") < result.indexOf("care"))
    }

    @Test
    fun formatWrite_outputsHumanizedTextWithoutTraceOrStructuredLogs() {
        val formatter = AgentStyleFormatter()

        val result = formatter.formatWrite(
            facts = AgentWriteStyleFacts(
                successCount = 2,
                failureCount = 1,
                createCount = 1,
                updateCount = 1,
                deleteCount = 0,
                errors = listOf("missing category"),
                primaryAction = "create",
                primaryCategory = "交通出行",
                primaryDesc = "打车",
            ),
            requestId = "req-001",
        )

        assertTrue(result.contains("处理好了"))
        assertTrue(result.contains("补全") || result.contains("修正") || result.contains("重试"))
        assertTrue(result.contains("结构化结果") == false)
        assertTrue(result.contains("追踪ID") == false)
        assertEquals(true, result.contains("\n\n"))
    }

    @Test
    fun formatWrite_failureDoesNotEchoRawBackendError() {
        val formatter = AgentStyleFormatter()

        val rawError = "哎呀，这笔账单还不知道是什么分类呢，告诉我是吃喝还是交通，我立刻补上~"
        val result = formatter.formatWrite(
            facts = AgentWriteStyleFacts(
                successCount = 0,
                failureCount = 1,
                createCount = 0,
                updateCount = 0,
                deleteCount = 0,
                errors = listOf(rawError),
                primaryAction = "create",
                primaryCategory = null,
                primaryDesc = null,
            ),
            requestId = "req-002",
        )

        assertTrue(result.contains("差一点点信息") || result.contains("马上帮你补上"))
        assertTrue(result.contains(rawError) == false)
    }
}
