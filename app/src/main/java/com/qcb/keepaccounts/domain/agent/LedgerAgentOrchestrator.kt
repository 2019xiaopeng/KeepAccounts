package com.qcb.keepaccounts.domain.agent

import com.qcb.keepaccounts.domain.contract.AiReceiptDraft
import kotlin.math.abs

class LedgerAgentOrchestrator(
    private val runLogger: AgentRunLogger = NoOpAgentRunLogger,
    private val validator: AgentToolValidator = AgentToolValidator(),
    private val nowProvider: () -> Long = { System.currentTimeMillis() },
) {

    interface WriteToolAdapter {
        suspend fun preview(args: AgentToolArgs.PreviewActionsArgs): AdapterResult

        suspend fun create(draft: AiReceiptDraft): WriteToolResult

        suspend fun update(draft: AiReceiptDraft): WriteToolResult

        suspend fun delete(draft: AiReceiptDraft): WriteToolResult
    }

    data class AdapterResult(
        val status: AgentToolStatus,
        val resultJson: String,
        val errorCode: AgentErrorCode? = null,
        val errorMessage: String? = null,
    )

    sealed interface WriteToolResult {
        data class ToolTransactionRef(
            val transactionId: Long,
            val type: Int,
        )

        data class Success(
            val transactionId: Long,
            val type: Int,
            val action: String,
            val resultJson: String,
            val affectedTransactions: List<ToolTransactionRef> = listOf(
                ToolTransactionRef(
                    transactionId = transactionId,
                    type = type,
                ),
            ),
        ) : WriteToolResult

        data class Failure(
            val reason: String,
            val errorCode: AgentErrorCode? = null,
        ) : WriteToolResult
    }

    data class LedgerActionSuccess(
        val index: Int,
        val transactionId: Long,
        val type: Int,
        val action: String,
        val draft: AiReceiptDraft,
        val affectedTransactions: List<WriteToolResult.ToolTransactionRef>,
    )

    data class LedgerActionFailure(
        val index: Int,
        val draft: AiReceiptDraft,
        val reason: String,
    )

    data class LedgerOrchestrationResult(
        val successes: List<LedgerActionSuccess>,
        val failures: List<LedgerActionFailure>,
        val runStatus: AgentRunStatus,
    ) {
        val hasSuccess: Boolean
            get() = successes.isNotEmpty()
    }

    suspend fun execute(
        context: AgentRequestContext,
        drafts: List<AiReceiptDraft>,
        adapter: WriteToolAdapter,
    ): LedgerOrchestrationResult {
        if (drafts.isEmpty()) {
            return LedgerOrchestrationResult(
                successes = emptyList(),
                failures = emptyList(),
                runStatus = AgentRunStatus.SUCCESS,
            )
        }

        runLogger.markRunStarted(context)

        val successes = mutableListOf<LedgerActionSuccess>()
        val failures = mutableListOf<LedgerActionFailure>()
        var stepIndex = 0

        drafts.forEachIndexed { index, draft ->
            val order = index + 1

            val previewArgs = AgentToolArgs.PreviewActionsArgs(
                actions = listOf(
                    PreviewActionItem(
                        action = draft.action,
                        amount = draft.amount,
                        category = draft.category,
                        recordTime = draft.recordTime,
                        date = draft.date,
                        desc = draft.desc,
                        transactionId = draft.transactionId,
                    ),
                ),
            )
            val previewStartedAt = nowProvider()
            val previewResult = adapter.preview(previewArgs)
            val previewFinishedAt = nowProvider()
            val previewLatency = (previewFinishedAt - previewStartedAt).coerceAtLeast(0L)
            stepIndex += 1
            runLogger.appendToolCall(
                AgentToolCallRecord(
                    requestId = context.requestId,
                    runId = context.requestId,
                    stepIndex = stepIndex,
                    toolName = AgentToolName.PREVIEW_ACTIONS,
                    argsJson = previewArgs.toLogJson(),
                    resultJson = previewResult.resultJson,
                    status = previewResult.status,
                    errorCode = previewResult.errorCode,
                    errorMessage = previewResult.errorMessage,
                    latencyMs = previewLatency,
                    timestamp = previewFinishedAt,
                ),
            )

            if (previewResult.status == AgentToolStatus.FAILURE) {
                failures += LedgerActionFailure(
                    index = order,
                    draft = draft,
                    reason = previewResult.errorMessage ?: "预演失败，已阻断执行。",
                )
                return@forEachIndexed
            }

            val validationIssues = validateDraft(draft)
            if (validationIssues.isNotEmpty()) {
                val firstIssue = validationIssues.first()
                stepIndex += 1
                val failedToolName = resolveToolName(draft.action)
                val validationTimestamp = nowProvider()
                runLogger.appendToolCall(
                    AgentToolCallRecord(
                        requestId = context.requestId,
                        runId = context.requestId,
                        stepIndex = stepIndex,
                        toolName = failedToolName,
                        argsJson = draft.toLogJson(),
                        resultJson = "{\"status\":\"validation_failed\"}",
                        status = AgentToolStatus.FAILURE,
                        errorCode = firstIssue.code,
                        errorMessage = firstIssue.message,
                        latencyMs = 0L,
                        timestamp = validationTimestamp,
                    ),
                )
                failures += LedgerActionFailure(
                    index = order,
                    draft = draft,
                    reason = firstIssue.message,
                )
                return@forEachIndexed
            }

            val normalizedAction = draft.action.lowercase()
            val toolName = resolveToolName(normalizedAction)
            val toolStartedAt = nowProvider()
            val toolResult = when (normalizedAction) {
                "update" -> adapter.update(draft)
                "delete" -> adapter.delete(draft)
                else -> adapter.create(draft)
            }
            val toolFinishedAt = nowProvider()
            val toolLatency = (toolFinishedAt - toolStartedAt).coerceAtLeast(0L)

            stepIndex += 1
            when (toolResult) {
                is WriteToolResult.Success -> {
                    runLogger.appendToolCall(
                        AgentToolCallRecord(
                            requestId = context.requestId,
                            runId = context.requestId,
                            stepIndex = stepIndex,
                            toolName = toolName,
                            argsJson = draft.toLogJson(),
                            resultJson = toolResult.resultJson,
                            status = AgentToolStatus.SUCCESS,
                            latencyMs = toolLatency,
                            timestamp = toolFinishedAt,
                        ),
                    )
                    successes += LedgerActionSuccess(
                        index = order,
                        transactionId = toolResult.transactionId,
                        type = toolResult.type,
                        action = toolResult.action,
                        draft = draft,
                        affectedTransactions = toolResult.affectedTransactions,
                    )
                }

                is WriteToolResult.Failure -> {
                    runLogger.appendToolCall(
                        AgentToolCallRecord(
                            requestId = context.requestId,
                            runId = context.requestId,
                            stepIndex = stepIndex,
                            toolName = toolName,
                            argsJson = draft.toLogJson(),
                            resultJson = "{\"status\":\"failed\"}",
                            status = AgentToolStatus.FAILURE,
                            errorCode = toolResult.errorCode,
                            errorMessage = toolResult.reason,
                            latencyMs = toolLatency,
                            timestamp = toolFinishedAt,
                        ),
                    )
                    failures += LedgerActionFailure(
                        index = order,
                        draft = draft,
                        reason = toolResult.reason,
                    )
                }
            }
        }

        val runStatus = when {
            failures.isEmpty() -> AgentRunStatus.SUCCESS
            successes.isEmpty() -> AgentRunStatus.FAILED
            else -> AgentRunStatus.PARTIAL_SUCCESS
        }

        runLogger.markRunFinished(
            requestId = context.requestId,
            status = runStatus,
            finishedAt = nowProvider(),
            errorCode = if (runStatus == AgentRunStatus.FAILED) AgentErrorCode.VALIDATION_FAILED else null,
            errorMessage = if (runStatus == AgentRunStatus.FAILED && failures.isNotEmpty()) {
                failures.first().reason
            } else {
                null
            },
        )

        return LedgerOrchestrationResult(
            successes = successes,
            failures = failures,
            runStatus = runStatus,
        )
    }

    private fun validateDraft(draft: AiReceiptDraft): List<AgentValidationIssue> {
        val normalizedAction = draft.action.lowercase()
        return when (normalizedAction) {
            "update" -> {
                validator.validateUpdateArgs(
                    AgentToolArgs.UpdateTransactionsArgs(
                        filters = TransactionFilter(
                            transactionId = draft.transactionId,
                            dateKeyword = draft.date,
                            keyword = draft.desc,
                            amountMin = draft.amount,
                            amountMax = draft.amount,
                        ),
                        patch = TransactionPatch(
                            amount = draft.amount,
                            category = draft.category,
                            recordTime = draft.recordTime,
                            date = draft.date,
                            remark = draft.desc,
                        ),
                    ),
                )
            }

            "delete" -> {
                validator.validateDeleteArgs(
                    AgentToolArgs.DeleteTransactionsArgs(
                        filters = TransactionFilter(
                            transactionId = draft.transactionId,
                            dateKeyword = draft.date,
                            keyword = draft.desc,
                            amountMin = draft.amount,
                            amountMax = draft.amount,
                        ),
                    ),
                )
            }

            else -> {
                val amount = draft.amount
                val needsAmountIssue = amount == null || abs(amount) <= 0.0
                val amountValue = if (needsAmountIssue) 0.0 else amount
                validator.validateCreateArgs(
                    AgentToolArgs.CreateTransactionsArgs(
                        items = listOf(
                            PreviewActionItem(
                                action = draft.action,
                                amount = amountValue,
                                category = draft.category,
                                recordTime = draft.recordTime,
                                date = draft.date,
                                desc = draft.desc,
                                transactionId = draft.transactionId,
                            ),
                        ),
                    ),
                )
            }
        }
    }

    private fun resolveToolName(action: String): AgentToolName {
        return when (action.lowercase()) {
            "update" -> AgentToolName.UPDATE_TRANSACTIONS
            "delete" -> AgentToolName.DELETE_TRANSACTIONS
            else -> AgentToolName.CREATE_TRANSACTIONS
        }
    }
}

private fun String.escapeJson(): String {
    return replace("\\", "\\\\").replace("\"", "\\\"")
}

private fun AgentToolArgs.PreviewActionsArgs.toLogJson(): String {
    val actionsJson = actions.joinToString(separator = ",") { action ->
        "{\"action\":\"${action.action.escapeJson()}\",\"amount\":${action.amount ?: "null"},\"category\":${action.category?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"recordTime\":${action.recordTime?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"date\":${action.date?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"desc\":${action.desc?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"transactionId\":${action.transactionId ?: "null"}}"
    }
    return "{\"actions\":[${actionsJson}]}"
}

private fun AiReceiptDraft.toLogJson(): String {
    return "{\"action\":\"${action.escapeJson()}\",\"amount\":${amount ?: "null"},\"category\":${category?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"desc\":${desc?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"recordTime\":${recordTime?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"date\":${date?.let { "\"${it.escapeJson()}\"" } ?: "null"},\"transactionId\":${transactionId ?: "null"}}"
}
