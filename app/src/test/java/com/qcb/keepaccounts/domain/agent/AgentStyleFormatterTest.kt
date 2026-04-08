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
    fun formatWrite_containsSummaryAndTraceId() {
        val formatter = AgentStyleFormatter()

        val result = formatter.formatWrite(
            facts = AgentWriteStyleFacts(
                successCount = 2,
                failureCount = 1,
                createCount = 1,
                updateCount = 1,
                deleteCount = 0,
                errors = listOf("missing category"),
            ),
            requestId = "req-001",
        )

        assertTrue(result.contains("结构化结果：success=2"))
        assertTrue(result.contains("errors=missing category"))
        assertTrue(result.contains("追踪ID：req-001"))
        assertEquals(true, result.contains("\n\n"))
    }
}
