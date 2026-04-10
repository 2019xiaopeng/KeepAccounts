package com.qcb.keepaccounts.domain.agent

data class AgentRequestContext(
    val requestId: String,
    val idempotencyKey: String,
    val userInput: String,
    val startedAt: Long,
)

data class PlannerInputV2(
    val requestId: String,
    val userInput: String,
    val nowMillis: Long,
    val timezoneId: String,
)

enum class PlannerIntentType {
    CREATE_TRANSACTIONS,
    UPDATE_TRANSACTIONS,
    DELETE_TRANSACTIONS,
    QUERY_TRANSACTIONS,
    QUERY_SPENDING_STATS,
    CHITCHAT,
    UNKNOWN,
}

enum class PlannerTargetMode {
    SINGLE,
    SET,
    TOP_N,
}

enum class PlannerRiskLevel {
    LOW,
    MEDIUM,
    HIGH,
}

data class IntentPlanV2(
    val intent: PlannerIntentType,
    val confidence: Double,
    val targetMode: PlannerTargetMode = PlannerTargetMode.SINGLE,
    val riskLevel: PlannerRiskLevel = PlannerRiskLevel.LOW,
    val needsConfirmation: Boolean = false,
    val missingSlots: List<String> = emptyList(),
    val queryArgs: AgentToolArgs.QueryTransactionsArgs? = null,
    val statsArgs: AgentToolArgs.QuerySpendingStatsArgs? = null,
    val createItems: List<PreviewActionItem> = emptyList(),
)

data class ToolCallEnvelope(
    val requestId: String,
    val stepIndex: Int,
    val toolName: AgentToolName,
    val argsJson: String,
    val plannedAt: Long,
)

data class ObservationEnvelope(
    val requestId: String,
    val toolName: AgentToolName?,
    val status: AgentToolStatus,
    val resultJson: String? = null,
    val observedAt: Long,
)

interface AgentPlanner {
    suspend fun plan(input: PlannerInputV2): IntentPlanV2?
}

object NoOpAgentPlanner : AgentPlanner {
    override suspend fun plan(input: PlannerInputV2): IntentPlanV2? = null
}

enum class AgentToolName {
    PREVIEW_ACTIONS,
    CREATE_TRANSACTIONS,
    UPDATE_TRANSACTIONS,
    DELETE_TRANSACTIONS,
    QUERY_TRANSACTIONS,
    QUERY_SPENDING_STATS,
}

enum class AgentToolStatus {
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILURE,
    SKIPPED,
}

enum class AgentRunStatus {
    PROCESSING,
    SUCCESS,
    PARTIAL_SUCCESS,
    FAILED,
}

enum class AgentErrorCode {
    INVALID_AMOUNT,
    EMPTY_CATEGORY,
    INVALID_TIME_WINDOW,
    TARGET_NOT_FOUND,
    VALIDATION_FAILED,
    TOOL_NOT_IMPLEMENTED,
    UNEXPECTED_ERROR,
}

data class PreviewActionItem(
    val action: String,
    val amount: Double?,
    val category: String?,
    val recordTime: String?,
    val date: String? = null,
    val desc: String? = null,
    val transactionId: Long? = null,
)

data class TransactionFilter(
    val transactionId: Long? = null,
    val dateKeyword: String? = null,
    val keyword: String? = null,
    val amountMin: Double? = null,
    val amountMax: Double? = null,
)

data class TransactionPatch(
    val amount: Double? = null,
    val category: String? = null,
    val recordTime: String? = null,
    val date: String? = null,
    val remark: String? = null,
)

sealed interface AgentToolArgs {
    data class PreviewActionsArgs(
        val actions: List<PreviewActionItem>,
    ) : AgentToolArgs

    data class CreateTransactionsArgs(
        val items: List<PreviewActionItem>,
    ) : AgentToolArgs

    data class UpdateTransactionsArgs(
        val filters: TransactionFilter,
        val patch: TransactionPatch,
    ) : AgentToolArgs

    data class DeleteTransactionsArgs(
        val filters: TransactionFilter,
    ) : AgentToolArgs

    data class QueryTransactionsArgs(
        val filters: TransactionFilter,
        val window: String = "last30days",
        val sortKey: String = "record_time_desc",
        val limit: Int = 20,
        val startAtMillis: Long? = null,
        val endAtMillis: Long? = null,
    ) : AgentToolArgs

    data class QuerySpendingStatsArgs(
        val window: String,
        val groupBy: String,
        val metric: String,
        val sortKey: String = "value_desc",
        val topN: Int = 5,
        val startAtMillis: Long? = null,
        val endAtMillis: Long? = null,
    ) : AgentToolArgs
}

data class AgentValidationIssue(
    val field: String,
    val code: AgentErrorCode,
    val message: String,
)

data class AgentToolExecutionResult(
    val toolName: AgentToolName,
    val status: AgentToolStatus,
    val argsJson: String,
    val resultJson: String,
    val errorCode: AgentErrorCode? = null,
    val errorMessage: String? = null,
    val latencyMs: Long = 0L,
)

data class AgentToolCallRecord(
    val requestId: String,
    val runId: String,
    val stepIndex: Int,
    val toolName: AgentToolName,
    val argsJson: String,
    val resultJson: String,
    val status: AgentToolStatus,
    val errorCode: AgentErrorCode? = null,
    val errorMessage: String? = null,
    val latencyMs: Long,
    val timestamp: Long,
)

data class AgentReplayTrace(
    val requestId: String,
    val runStatus: AgentRunStatus,
    val userInput: String,
    val startedAt: Long,
    val endedAt: Long?,
    val calls: List<AgentToolCallRecord>,
)

interface AgentRunLogger {
    suspend fun markRunStarted(context: AgentRequestContext)

    suspend fun appendToolCall(record: AgentToolCallRecord)

    suspend fun markRunFinished(
        requestId: String,
        status: AgentRunStatus,
        finishedAt: Long,
        errorCode: AgentErrorCode? = null,
        errorMessage: String? = null,
    )
}

object NoOpAgentRunLogger : AgentRunLogger {
    override suspend fun markRunStarted(context: AgentRequestContext) = Unit

    override suspend fun appendToolCall(record: AgentToolCallRecord) = Unit

    override suspend fun markRunFinished(
        requestId: String,
        status: AgentRunStatus,
        finishedAt: Long,
        errorCode: AgentErrorCode?,
        errorMessage: String?,
    ) = Unit
}
