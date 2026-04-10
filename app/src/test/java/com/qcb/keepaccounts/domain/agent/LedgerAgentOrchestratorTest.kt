package com.qcb.keepaccounts.domain.agent

import com.qcb.keepaccounts.domain.contract.AiReceiptDraft
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LedgerAgentOrchestratorTest {

    @Test
    fun execute_runsPreviewThenCreateAndCollectsSuccess() {
        val logger = FakeAgentRunLogger()
        val orchestrator = LedgerAgentOrchestrator(runLogger = logger)
        val draft = AiReceiptDraft(
            isReceipt = true,
            action = "create",
            amount = 18.0,
            category = "餐饮美食",
            desc = "午餐",
            recordTime = "2026-04-08 12:00",
            date = "2026-04-08",
        )

        val result = kotlinx.coroutines.runBlocking {
            orchestrator.execute(
                context = AgentRequestContext(
                    requestId = "req-1",
                    idempotencyKey = "idem-1",
                    userInput = "午饭18",
                    startedAt = 1000L,
                ),
                drafts = listOf(draft),
                adapter = object : LedgerAgentOrchestrator.WriteToolAdapter {
                    override suspend fun preview(args: AgentToolArgs.PreviewActionsArgs): LedgerAgentOrchestrator.AdapterResult {
                        return LedgerAgentOrchestrator.AdapterResult(
                            status = AgentToolStatus.SUCCESS,
                            resultJson = "{\"ok\":true}",
                        )
                    }

                    override suspend fun create(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        return LedgerAgentOrchestrator.WriteToolResult.Success(
                            transactionId = 11L,
                            type = 0,
                            action = "create",
                            resultJson = "{\"transactionId\":11}",
                        )
                    }

                    override suspend fun update(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        error("not expected")
                    }

                    override suspend fun delete(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        error("not expected")
                    }
                },
            )
        }

        assertEquals(1, result.successes.size)
        assertEquals(0, result.failures.size)
        assertEquals(AgentRunStatus.SUCCESS, result.runStatus)
        assertEquals(listOf(AgentToolName.PREVIEW_ACTIONS, AgentToolName.CREATE_TRANSACTIONS), logger.toolNames)
    }

    @Test
    fun execute_validationFailureStopsCreateCall() {
        val logger = FakeAgentRunLogger()
        val orchestrator = LedgerAgentOrchestrator(runLogger = logger)
        val invalidDraft = AiReceiptDraft(
            isReceipt = true,
            action = "create",
            amount = 20.0,
            category = "",
            desc = "缺分类",
            recordTime = null,
            date = null,
        )

        var createCalled = false

        val result = kotlinx.coroutines.runBlocking {
            orchestrator.execute(
                context = AgentRequestContext(
                    requestId = "req-2",
                    idempotencyKey = "idem-2",
                    userInput = "20块",
                    startedAt = 2000L,
                ),
                drafts = listOf(invalidDraft),
                adapter = object : LedgerAgentOrchestrator.WriteToolAdapter {
                    override suspend fun preview(args: AgentToolArgs.PreviewActionsArgs): LedgerAgentOrchestrator.AdapterResult {
                        return LedgerAgentOrchestrator.AdapterResult(
                            status = AgentToolStatus.SUCCESS,
                            resultJson = "{\"ok\":true}",
                        )
                    }

                    override suspend fun create(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        createCalled = true
                        return LedgerAgentOrchestrator.WriteToolResult.Success(
                            transactionId = 1L,
                            type = 0,
                            action = "create",
                            resultJson = "{}",
                        )
                    }

                    override suspend fun update(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        createCalled = true
                        return LedgerAgentOrchestrator.WriteToolResult.Failure("not expected")
                    }

                    override suspend fun delete(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        createCalled = true
                        return LedgerAgentOrchestrator.WriteToolResult.Failure("not expected")
                    }
                },
            )
        }

        assertEquals(0, result.successes.size)
        assertEquals(1, result.failures.size)
        assertTrue(!createCalled)
        assertEquals(AgentRunStatus.FAILED, result.runStatus)
    }

    @Test
    fun execute_runsPreviewThenDeleteAndCollectsSuccess() {
        val logger = FakeAgentRunLogger()
        val orchestrator = LedgerAgentOrchestrator(runLogger = logger)
        val deleteDraft = AiReceiptDraft(
            isReceipt = true,
            action = "delete",
            amount = null,
            category = "餐饮美食",
            desc = "最近两条",
            recordTime = null,
            date = null,
        )

        val result = kotlinx.coroutines.runBlocking {
            orchestrator.execute(
                context = AgentRequestContext(
                    requestId = "req-3",
                    idempotencyKey = "idem-3",
                    userInput = "确认删除最近两条",
                    startedAt = 3000L,
                ),
                drafts = listOf(deleteDraft),
                adapter = object : LedgerAgentOrchestrator.WriteToolAdapter {
                    override suspend fun preview(args: AgentToolArgs.PreviewActionsArgs): LedgerAgentOrchestrator.AdapterResult {
                        return LedgerAgentOrchestrator.AdapterResult(
                            status = AgentToolStatus.SUCCESS,
                            resultJson = "{\"targetCount\":2}",
                        )
                    }

                    override suspend fun create(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        error("not expected")
                    }

                    override suspend fun update(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        error("not expected")
                    }

                    override suspend fun delete(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                        return LedgerAgentOrchestrator.WriteToolResult.Success(
                            transactionId = 7L,
                            type = 0,
                            action = "delete",
                            resultJson = "{\"deletedCount\":2}",
                            affectedTransactions = listOf(
                                LedgerAgentOrchestrator.WriteToolResult.ToolTransactionRef(7L, 0),
                                LedgerAgentOrchestrator.WriteToolResult.ToolTransactionRef(8L, 0),
                            ),
                        )
                    }
                },
            )
        }

        assertEquals(1, result.successes.size)
        assertEquals(0, result.failures.size)
        assertEquals(AgentRunStatus.SUCCESS, result.runStatus)
        assertEquals(listOf(AgentToolName.PREVIEW_ACTIONS, AgentToolName.DELETE_TRANSACTIONS), logger.toolNames)
    }
}

private class FakeAgentRunLogger : AgentRunLogger {
    val toolNames = mutableListOf<AgentToolName>()

    override suspend fun markRunStarted(context: AgentRequestContext) = Unit

    override suspend fun appendToolCall(record: AgentToolCallRecord) {
        toolNames += record.toolName
    }

    override suspend fun markRunFinished(
        requestId: String,
        status: AgentRunStatus,
        finishedAt: Long,
        errorCode: AgentErrorCode?,
        errorMessage: String?,
    ) = Unit
}
