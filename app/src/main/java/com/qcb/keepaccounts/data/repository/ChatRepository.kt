package com.qcb.keepaccounts.data.repository

import com.qcb.keepaccounts.data.agent.AgentQualityFeedbackInput
import com.qcb.keepaccounts.data.agent.AgentQualityFeedbackRepository
import com.qcb.keepaccounts.data.agent.AgentQualityMetrics
import com.qcb.keepaccounts.data.agent.AgentObservationReport
import com.qcb.keepaccounts.data.agent.AgentQualityStage
import com.qcb.keepaccounts.data.agent.AgentRoutePath
import com.qcb.keepaccounts.data.agent.AgentReplayService
import com.qcb.keepaccounts.data.local.dao.ChatMessageDao
import com.qcb.keepaccounts.data.local.dao.TransactionDao
import com.qcb.keepaccounts.data.local.entity.ChatMessageEntity
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.domain.agent.AgentReplayTrace
import com.qcb.keepaccounts.domain.agent.AgentRequestContext
import com.qcb.keepaccounts.domain.agent.AgentRunLogger
import com.qcb.keepaccounts.domain.agent.AgentRunStatus
import com.qcb.keepaccounts.domain.agent.AgentValidationIssue
import com.qcb.keepaccounts.domain.agent.AgentToolArgs
import com.qcb.keepaccounts.domain.agent.AgentToolCallRecord
import com.qcb.keepaccounts.domain.agent.AgentToolName
import com.qcb.keepaccounts.domain.agent.AgentToolStatus
import com.qcb.keepaccounts.domain.agent.AgentErrorCode
import com.qcb.keepaccounts.domain.agent.AgentPlanner
import com.qcb.keepaccounts.domain.agent.AgentStyleFormatter
import com.qcb.keepaccounts.domain.agent.AgentWriteStyleFacts
import com.qcb.keepaccounts.domain.agent.AmountNormalizer
import com.qcb.keepaccounts.domain.agent.CountNormalizer
import com.qcb.keepaccounts.domain.agent.IntentPlanV2
import com.qcb.keepaccounts.domain.agent.LedgerTransactionSnapshot
import com.qcb.keepaccounts.domain.agent.LedgerAgentOrchestrator
import com.qcb.keepaccounts.domain.agent.NoOpAgentPlanner
import com.qcb.keepaccounts.domain.agent.NoOpAgentRunLogger
import com.qcb.keepaccounts.domain.agent.PlannerInputV2
import com.qcb.keepaccounts.domain.agent.PlannerIntentType
import com.qcb.keepaccounts.domain.agent.PlannerOutputValidator
import com.qcb.keepaccounts.domain.agent.PendingIntentState
import com.qcb.keepaccounts.domain.agent.PendingIntentStateStore
import com.qcb.keepaccounts.domain.agent.PreviewActionItem
import com.qcb.keepaccounts.domain.agent.QueryInsightsToolExecutor
import com.qcb.keepaccounts.domain.agent.QuerySpendingStatsResult
import com.qcb.keepaccounts.domain.agent.QueryToolCallResult
import com.qcb.keepaccounts.domain.agent.QueryTransactionsResult
import com.qcb.keepaccounts.domain.agent.InMemoryPendingIntentStateStore
import com.qcb.keepaccounts.domain.agent.TemporalResolverV2
import com.qcb.keepaccounts.domain.agent.TransactionFilter
import com.qcb.keepaccounts.domain.contract.AiChatGateway
import com.qcb.keepaccounts.domain.contract.AiChatRequest
import com.qcb.keepaccounts.domain.contract.AiMessage
import com.qcb.keepaccounts.domain.contract.AiReceiptDraft
import com.qcb.keepaccounts.domain.contract.AiStreamEvent
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiChatRecord
import com.qcb.keepaccounts.ui.model.AiChatReceiptItem
import com.qcb.keepaccounts.ui.model.AiChatReceiptSummary
import com.qcb.keepaccounts.ui.model.AiTone
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.combine
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID
import kotlin.math.abs

class ChatRepository(
    private val chatMessageDao: ChatMessageDao,
    private val transactionDao: TransactionDao,
    private val aiChatGateway: AiChatGateway,
    private val agentRunLogger: AgentRunLogger = NoOpAgentRunLogger,
    private val agentReplayService: AgentReplayService? = null,
    private val qualityFeedbackRepository: AgentQualityFeedbackRepository? = null,
    private val requestIdProvider: () -> String = { UUID.randomUUID().toString() },
    private val styleFormatter: AgentStyleFormatter = AgentStyleFormatter(),
    private val agentPlanner: AgentPlanner = NoOpAgentPlanner,
    private val plannerShadowEnabled: Boolean = true,
    private val plannerPrimaryEnabled: Boolean = false,
    private val plannerPrimaryRolloutPercent: Int = 0,
    private val plannerPrimaryMinConfidence: Double = 0.75,
    private val plannerOutputValidator: PlannerOutputValidator = PlannerOutputValidator(),
    private val pendingIntentStateStore: PendingIntentStateStore = InMemoryPendingIntentStateStore(),
    private val pendingIntentTtlMillis: Long = defaultPendingIntentTtlMillis,
    private val amountNormalizer: AmountNormalizer = AmountNormalizer(),
    private val countNormalizer: CountNormalizer = CountNormalizer(),
    private val temporalResolverV2: TemporalResolverV2 = TemporalResolverV2(),
    private val plannerRolloutGateEnabled: Boolean = true,
    private val plannerGateWindowDays: Int = 7,
    private val plannerGateMinSamples: Int = 20,
    private val plannerGateMaxMisjudgeRate: Double = 0.30,
    private val plannerGateMaxMismatchSamples: Int = 10,
    private val agentDefaultPathEnabled: Boolean = true,
    private val promptFallbackEnabled: Boolean = true,
    private val agentOrchestrator: LedgerAgentOrchestrator = LedgerAgentOrchestrator(
        runLogger = agentRunLogger,
    ),
) {

    private data class AppliedTransaction(
        val transactionId: Long,
        val type: Int,
        val action: String,
        val draft: AiReceiptDraft,
        val index: Int = 0,
    )

    private data class FailedTransaction(
        val index: Int,
        val draft: AiReceiptDraft,
        val reason: String,
    )

    private data class DeletePlan(
        val targets: List<TransactionEntity>,
        val requiresConfirmation: Boolean,
        val isConfirmed: Boolean,
        val previewMessage: String,
    )

    private data class QueryIntentPlan(
        val toolName: AgentToolName,
        val queryArgs: AgentToolArgs.QueryTransactionsArgs? = null,
        val statsArgs: AgentToolArgs.QuerySpendingStatsArgs? = null,
    )

    private data class QueryIntentExecutionResult(
        val toolName: AgentToolName,
        val status: AgentToolStatus,
        val argsJson: String,
        val resultJson: String,
        val assistantReply: String,
        val errorCode: AgentErrorCode? = null,
        val errorMessage: String? = null,
    )

    private data class WriteIntentExecutionResult(
        val status: AgentToolStatus,
        val assistantReply: String,
        val applyResult: ApplyTransactionsResult,
        val errorCode: AgentErrorCode? = null,
        val errorMessage: String? = null,
    )

    private data class PlannerPrimaryExecutionResult(
        val expectedAction: String,
        val actualAction: String? = null,
        val stage: AgentQualityStage = AgentQualityStage.TOOL_EXECUTION,
        val status: AgentToolStatus,
        val errorCode: AgentErrorCode? = null,
        val errorMessage: String? = null,
        val metadataJson: String? = null,
        val fallbackUsed: Boolean = false,
        val isMisjudged: Boolean = false,
    )

    private data class PlannerRolloutGuardSnapshot(
        val allowed: Boolean,
        val reason: String,
        val sampleCount: Int,
        val misjudgeRate: Double,
        val mismatchSamples: Int,
    )

    private data class ApplyTransactionsResult(
        val appliedTransactions: List<AppliedTransaction> = emptyList(),
        val failedTransactions: List<FailedTransaction> = emptyList(),
        val errors: List<String> = emptyList(),
    ) {
        val hasSuccess: Boolean get() = appliedTransactions.isNotEmpty()
        val primaryAppliedTransaction: AppliedTransaction? get() = appliedTransactions.firstOrNull()
        val createCount: Int get() = appliedTransactions.count { it.action == ACTION_CREATE }
        val updateCount: Int get() = appliedTransactions.count { it.action == ACTION_UPDATE }
        val deleteCount: Int get() = appliedTransactions.count { it.action == ACTION_DELETE }
    }

    private data class TransactionBinding(
        val transactionId: Long,
        val action: String,
    )

    fun observeChatRecords(): Flow<List<AiChatRecord>> {
        return combine(
            chatMessageDao.observeAllMessages(),
            transactionDao.observeAllTransactions(),
        ) { messages, transactions ->
            val transactionById = transactions.associateBy { it.id }

            messages.map { entity ->
                val linkedTransaction = entity.transactionId?.let(transactionById::get)
                val transactionBindings = parseTransactionBindings(entity.transactionBindings)
                val transactionIds = transactionBindings
                    .map { it.transactionId }
                    .ifEmpty { listOfNotNull(entity.transactionId) }
                val receiptSummary = parseReceiptSummary(
                    content = entity.content,
                    isReceipt = entity.isReceipt,
                    linkedTransaction = linkedTransaction,
                    transactionBindings = transactionBindings,
                    transactionById = transactionById,
                    fallbackTimestamp = entity.timestamp,
                )
                val primaryReceiptItem = receiptSummary?.items?.firstOrNull { it.status == RECEIPT_STATUS_SUCCESS }
                AiChatRecord(
                    id = entity.id,
                    timestamp = entity.timestamp,
                    role = entity.role,
                    content = entity.content,
                    isReceipt = entity.isReceipt,
                    transactionId = entity.transactionId,
                    transactionIds = transactionIds,
                    receiptRecordTimestamp = linkedTransaction?.recordTimestamp ?: primaryReceiptItem?.recordTimestamp,
                    receiptType = linkedTransaction?.type ?: primaryReceiptItem?.isIncome?.let { if (it) 1 else 0 },
                    receiptSummary = receiptSummary,
                )
            }
        }
    }

    suspend fun deleteMessage(messageId: Long) {
        val message = chatMessageDao.getMessageById(messageId) ?: return
        chatMessageDao.deleteMessageById(messageId)
        val boundCreateIds = parseTransactionBindings(message.transactionBindings)
            .filter { it.action == ACTION_CREATE }
            .map { it.transactionId }
            .ifEmpty { listOfNotNull(message.transactionId) }
            .distinct()
        boundCreateIds.forEach { transactionDao.deleteTransactionById(it) }
    }

    suspend fun sendMessage(
        userInput: String,
        aiConfig: AiAssistantConfig,
        userName: String,
    ) {
        val requestId = requestIdProvider()
        val now = System.currentTimeMillis()
        chatMessageDao.insertMessage(
            ChatMessageEntity(
                role = "user",
                content = userInput,
                isReceipt = false,
                timestamp = now,
            ),
        )

        val isCorrectionInput = isUserCorrectionInput(userInput)
        if (isCorrectionInput) {
            qualityFeedbackRepository?.markLatestAsCorrectionSample(
                correctedByRequestId = requestId,
                correctionInput = userInput,
            )
        }

        if (agentDefaultPathEnabled) {
            val pendingHandled = tryHandlePendingIntent(
                requestId = requestId,
                userInput = userInput,
                isCorrectionInput = isCorrectionInput,
            )
            if (pendingHandled) {
                return
            }
        }

        val shouldTryPlannerPrimary = agentDefaultPathEnabled && shouldUsePlannerPrimary(requestId)
        val plannerPlan = if (agentDefaultPathEnabled && (plannerShadowEnabled || shouldTryPlannerPrimary)) {
            runPlannerShadow(
                requestId = requestId,
                userInput = userInput,
            )
        } else {
            null
        }

        if (agentDefaultPathEnabled) {
            if (shouldTryPlannerPrimary) {
                val plannerPrimaryHandled = tryHandlePlannerPrimaryIntent(
                    requestId = requestId,
                    userInput = userInput,
                    plannerPlan = plannerPlan,
                    isCorrectionInput = isCorrectionInput,
                )
                if (plannerPrimaryHandled) {
                    return
                }
            }

            val queryPlan = buildQueryIntentPlan(userInput)
            if (queryPlan != null) {
                val execution = handleQueryIntent(
                    requestId = requestId,
                    userInput = userInput,
                    plan = queryPlan,
                )
                recordQualityFeedback(
                    requestId = requestId,
                    routePath = AgentRoutePath.AGENT_PRIMARY,
                    stage = AgentQualityStage.TOOL_EXECUTION,
                    userInput = userInput,
                    expectedAction = queryPlan.toolName.name,
                    actualAction = execution.toolName.name,
                    runStatus = if (execution.status == AgentToolStatus.SUCCESS) "SUCCESS" else "FAILED",
                    fallbackUsed = false,
                    isMisjudged = isCorrectionInput || execution.status != AgentToolStatus.SUCCESS,
                    errorCode = execution.errorCode?.name,
                    errorMessage = execution.errorMessage,
                    metadataJson = execution.resultJson,
                )
                recordPlannerShadowFeedback(
                    requestId = requestId,
                    userInput = userInput,
                    routePath = AgentRoutePath.AGENT_PRIMARY,
                    plannerPlan = plannerPlan,
                    legacyAction = queryPlan.toolName.name,
                    legacyRunStatus = if (execution.status == AgentToolStatus.SUCCESS) "SUCCESS" else "FAILED",
                    fallbackUsed = false,
                    isCorrectionInput = isCorrectionInput,
                )
                return
            }

            val writeDrafts = buildWriteDraftsFromInput(userInput)
            if (writeDrafts.isNotEmpty()) {
                val execution = handleWriteIntent(
                    requestId = requestId,
                    userInput = userInput,
                    drafts = writeDrafts,
                )
                recordQualityFeedback(
                    requestId = requestId,
                    routePath = AgentRoutePath.AGENT_PRIMARY,
                    stage = AgentQualityStage.TOOL_EXECUTION,
                    userInput = userInput,
                    expectedAction = writeDrafts.firstOrNull()?.action,
                    actualAction = writeDrafts.firstOrNull()?.action,
                    runStatus = if (execution.status == AgentToolStatus.SUCCESS) "SUCCESS" else "FAILED",
                    fallbackUsed = false,
                    isMisjudged = isCorrectionInput || execution.status != AgentToolStatus.SUCCESS,
                    errorCode = execution.errorCode?.name,
                    errorMessage = execution.errorMessage,
                    metadataJson = buildReceiptMetaTag(execution.applyResult),
                )
                recordPlannerShadowFeedback(
                    requestId = requestId,
                    userInput = userInput,
                    routePath = AgentRoutePath.AGENT_PRIMARY,
                    plannerPlan = plannerPlan,
                    legacyAction = writeDrafts.firstOrNull()?.action,
                    legacyRunStatus = if (execution.status == AgentToolStatus.SUCCESS) "SUCCESS" else "FAILED",
                    fallbackUsed = false,
                    isCorrectionInput = isCorrectionInput,
                )
                return
            }

            if (!promptFallbackEnabled) {
                val assistantMessageIds = mutableListOf<Long>()
                syncAssistantReplyChunks(
                    messageIds = assistantMessageIds,
                    chunks = splitAssistantReply(
                        styleFormatter.formatFallback(
                            reason = "prompt_fallback_disabled",
                            requestId = requestId,
                        ),
                    ),
                    baseTimestamp = System.currentTimeMillis() + 1,
                    isReceiptLast = false,
                    receiptTransactionId = null,
                    receiptTransactionBindings = null,
                )
                recordQualityFeedback(
                    requestId = requestId,
                    routePath = AgentRoutePath.FALLBACK_BLOCKED,
                    stage = AgentQualityStage.INTENT_ROUTING,
                    userInput = userInput,
                    expectedAction = null,
                    actualAction = null,
                    runStatus = "FAILED",
                    fallbackUsed = true,
                    isMisjudged = true,
                    errorCode = AgentErrorCode.TOOL_NOT_IMPLEMENTED.name,
                    errorMessage = "prompt fallback disabled",
                    metadataJson = null,
                )
                recordPlannerShadowFeedback(
                    requestId = requestId,
                    userInput = userInput,
                    routePath = AgentRoutePath.FALLBACK_BLOCKED,
                    plannerPlan = plannerPlan,
                    legacyAction = "PROMPT_FALLBACK_BLOCKED",
                    legacyRunStatus = "FAILED",
                    fallbackUsed = true,
                    isCorrectionInput = isCorrectionInput,
                )
                return
            }

            recordQualityFeedback(
                requestId = requestId,
                routePath = AgentRoutePath.PROMPT_FALLBACK,
                stage = AgentQualityStage.INTENT_ROUTING,
                userInput = userInput,
                expectedAction = null,
                actualAction = null,
                runStatus = "FALLBACK",
                fallbackUsed = true,
                isMisjudged = isCorrectionInput,
                errorCode = null,
                errorMessage = null,
                metadataJson = null,
            )
            recordPlannerShadowFeedback(
                requestId = requestId,
                userInput = userInput,
                routePath = AgentRoutePath.PROMPT_FALLBACK,
                plannerPlan = plannerPlan,
                legacyAction = "PROMPT_FALLBACK",
                legacyRunStatus = "FALLBACK",
                fallbackUsed = true,
                isCorrectionInput = isCorrectionInput,
            )
        }

        val fallbackContextLimit = resolveFallbackContextLimit(
            userInput = userInput,
            isCorrectionInput = isCorrectionInput,
        )
        val recentMessages = chatMessageDao.getRecentMessages(limit = fallbackContextLimit).asReversed()
        val systemPrompt = buildSystemPrompt(aiConfig, userName)
        val requestMessages = buildGatewayRequestMessages(systemPrompt, recentMessages)

        val textBuffer = StringBuilder()
        val parsedReceipts = mutableListOf<AiReceiptDraft>()
        var streamErrorMessage: String? = null
        val assistantMessageIds = mutableListOf<Long>()
        val streamBaseTimestamp = System.currentTimeMillis() + 1

        aiChatGateway.streamReply(
            AiChatRequest(
                model = "Pro/moonshotai/Kimi-K2.5",
                messages = requestMessages,
                temperature = resolveTemperature(aiConfig.tone),
                stream = true,
            ),
        ).collect { event ->
            when (event) {
                is AiStreamEvent.TextDelta -> {
                    textBuffer.append(event.text)
                }

                is AiStreamEvent.ReceiptParsed -> parsedReceipts += event.drafts
                is AiStreamEvent.Error -> {
                    streamErrorMessage = event.message.ifBlank { "AI 服务暂时不可用，请稍后重试。" }
                }

                AiStreamEvent.Completed -> Unit
            }
        }

        val rawAssistantText = textBuffer.toString().trim()
        val fallbackReceipts = extractReceiptDraftsFromText(rawAssistantText)
        val resolvedReceipts = resolveReceiptDrafts(parsedReceipts, fallbackReceipts)
        val cleanedAssistantText = sanitizeAssistantVisibleText(rawAssistantText)

        val normalizedReceipts = resolvedReceipts
            .filter { it.isReceipt }
            .map { draft ->
                val normalizedAction = normalizeReceiptAction(draft, userInput)
                if (draft.action.equals(normalizedAction, ignoreCase = true)) {
                    draft
                } else {
                    draft.copy(action = normalizedAction)
                }
            }
        val transactionApplyResult = applyReceiptTransactions(
            requestId = requestId,
            drafts = normalizedReceipts,
            assistantText = cleanedAssistantText,
            userInput = userInput,
        )
        val appliedTransaction = transactionApplyResult.primaryAppliedTransaction
        val linkedTransactionId = appliedTransaction?.transactionId
        val transactionBindings = buildTransactionBindingsPayload(transactionApplyResult.appliedTransactions)
        val summaryText = buildApplySummaryText(transactionApplyResult)
        val finalAssistantText = if (transactionApplyResult.hasSuccess) {
            mergeAssistantText(
                baseText = cleanedAssistantText,
                extraText = summaryText.takeIf {
                    shouldAppendApplySummary(
                        result = transactionApplyResult,
                        totalDraftCount = normalizedReceipts.size,
                    )
                },
                fallbackText = defaultReceiptMessage(transactionApplyResult),
            )
        } else {
            mergeAssistantText(
                baseText = cleanedAssistantText,
                extraText = summaryText,
                fallbackText = streamErrorMessage?.trim().takeUnless { it.isNullOrBlank() } ?: "收到，我在。",
            )
        }

        val shouldRenderReceiptCard = normalizedReceipts.isNotEmpty() &&
            (transactionApplyResult.hasSuccess || transactionApplyResult.failedTransactions.isNotEmpty())

        if (shouldRenderReceiptCard) {
            val replyChunks = ensureReceiptConversationChunks(
                chunks = splitAssistantReply(finalAssistantText),
                draft = normalizedReceipts.firstOrNull(),
            )

            val receiptMeta = buildReceiptMetaTag(transactionApplyResult)
            val defaultReceiptText = defaultReceiptMessage(transactionApplyResult)
            val receiptText = replyChunks.lastOrNull()?.ifBlank { defaultReceiptText } ?: defaultReceiptText
            val receiptContent = if (receiptMeta.isBlank()) {
                receiptText
            } else {
                "$receiptText\n$receiptMeta"
            }

            val finalChunks = if (replyChunks.isEmpty()) {
                listOf(receiptContent)
            } else {
                replyChunks.dropLast(1) + receiptContent
            }

            syncAssistantReplyChunks(
                messageIds = assistantMessageIds,
                chunks = finalChunks,
                baseTimestamp = streamBaseTimestamp,
                isReceiptLast = true,
                receiptTransactionId = linkedTransactionId,
                receiptTransactionBindings = transactionBindings,
            )
            return
        }

        val finalChunks = splitAssistantReply(finalAssistantText)

        syncAssistantReplyChunks(
            messageIds = assistantMessageIds,
            chunks = finalChunks,
            baseTimestamp = streamBaseTimestamp,
            isReceiptLast = false,
            receiptTransactionId = null,
            receiptTransactionBindings = null,
        )
    }

    private suspend fun syncAssistantReplyChunks(
        messageIds: MutableList<Long>,
        chunks: List<String>,
        baseTimestamp: Long,
        isReceiptLast: Boolean,
        receiptTransactionId: Long?,
        receiptTransactionBindings: String?,
    ) {
        val normalizedChunks = chunks
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf("收到，我在。") }

        normalizedChunks.forEachIndexed { index, chunk ->
            val isLast = index == normalizedChunks.lastIndex
            val markAsReceipt = isReceiptLast && isLast
            val transactionId = if (markAsReceipt) receiptTransactionId else null

            if (index < messageIds.size) {
                chatMessageDao.updateMessage(
                    id = messageIds[index],
                    content = chunk,
                    isReceipt = markAsReceipt,
                    transactionId = transactionId,
                    transactionBindings = if (markAsReceipt) receiptTransactionBindings else null,
                )
            } else {
                val insertedId = chatMessageDao.insertMessage(
                    ChatMessageEntity(
                        role = "assistant",
                        content = chunk,
                        isReceipt = markAsReceipt,
                        transactionId = transactionId,
                        transactionBindings = if (markAsReceipt) receiptTransactionBindings else null,
                        timestamp = baseTimestamp + index,
                    ),
                )
                messageIds += insertedId
            }
        }

        if (messageIds.size > normalizedChunks.size) {
            val staleIds = messageIds.subList(normalizedChunks.size, messageIds.size).toList()
            if (staleIds.isNotEmpty()) {
                chatMessageDao.deleteMessagesByIds(staleIds)
            }
            repeat(messageIds.size - normalizedChunks.size) {
                messageIds.removeAt(messageIds.lastIndex)
            }
        }
    }

    private suspend fun applyReceiptTransactions(
        requestId: String,
        drafts: List<AiReceiptDraft>,
        assistantText: String,
        userInput: String,
    ): ApplyTransactionsResult {
        if (drafts.isEmpty()) return ApplyTransactionsResult()

        val context = AgentRequestContext(
            requestId = requestId,
            idempotencyKey = buildIdempotencyKey(userInput, drafts),
            userInput = userInput,
            startedAt = System.currentTimeMillis(),
        )
        val deletePlanCache = mutableMapOf<String, DeletePlan>()

        val orchestrationResult = agentOrchestrator.execute(
            context = context,
            drafts = drafts,
            adapter = object : LedgerAgentOrchestrator.WriteToolAdapter {
                override suspend fun preview(args: AgentToolArgs.PreviewActionsArgs): LedgerAgentOrchestrator.AdapterResult {
                    val firstAction = args.actions.firstOrNull()
                    val actionName = firstAction?.action?.trim().orEmpty().lowercase(Locale.ROOT)

                    if (actionName == ACTION_DELETE) {
                        val deleteDraft = firstAction?.toDeleteDraft() ?: AiReceiptDraft(
                            isReceipt = true,
                            action = ACTION_DELETE,
                            amount = null,
                            category = null,
                            desc = null,
                            recordTime = null,
                            date = null,
                            transactionId = null,
                        )
                        val cacheKey = buildDeletePlanCacheKey(deleteDraft, userInput)
                        val deletePlan = deletePlanCache.getOrPut(cacheKey) {
                            buildDeletePlan(
                                draft = deleteDraft,
                                userInput = userInput,
                            )
                        }

                        val previewJson = buildDeletePreviewJson(deletePlan)
                        val needsStopForConfirm = deletePlan.requiresConfirmation && !deletePlan.isConfirmed
                        val status = if (deletePlan.targets.isEmpty() || needsStopForConfirm) {
                            AgentToolStatus.FAILURE
                        } else {
                            AgentToolStatus.SUCCESS
                        }
                        val errorMessage = when {
                            deletePlan.targets.isEmpty() -> "我暂时没找到可删除的记录，你可以补充关键词或日期。"
                            needsStopForConfirm -> deletePlan.previewMessage
                            else -> null
                        }

                        return LedgerAgentOrchestrator.AdapterResult(
                            status = status,
                            resultJson = previewJson,
                            errorMessage = errorMessage,
                        )
                    }

                    val hasMeaningfulAction = args.actions.any { action ->
                        !action.action.isBlank() && (
                            action.amount != null ||
                                !action.category.isNullOrBlank() ||
                                !action.desc.isNullOrBlank() ||
                                !action.recordTime.isNullOrBlank() ||
                                !action.date.isNullOrBlank() ||
                                action.transactionId != null
                            )
                    }
                    val status = if (hasMeaningfulAction) AgentToolStatus.SUCCESS else AgentToolStatus.FAILURE
                    return LedgerAgentOrchestrator.AdapterResult(
                        status = status,
                        resultJson = "{\"previewCount\":${args.actions.size},\"status\":\"${status.name.lowercase(Locale.ROOT)}\"}",
                        errorMessage = if (hasMeaningfulAction) null else "预演未命中可执行动作。",
                    )
                }

                override suspend fun create(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                    val amount = draft.amount ?: return LedgerAgentOrchestrator.WriteToolResult.Failure(
                        reason = "哎呀，这笔还不知道金额呢，告诉我花了多少我就马上记好~",
                    )
                    if (draft.category.isNullOrBlank()) {
                        return LedgerAgentOrchestrator.WriteToolResult.Failure(
                            reason = "哎呀，这笔账单还不知道是什么分类呢，告诉我是吃喝还是交通，我立刻补上~",
                        )
                    }

                    val created = tryCreateTransaction(
                        draft = draft,
                        assistantText = assistantText,
                        userInput = userInput,
                        rawAmount = amount,
                    ) ?: return LedgerAgentOrchestrator.WriteToolResult.Failure(
                        reason = "唔，这笔我刚刚没写进去，再发一次我会继续盯着它。",
                    )

                    return LedgerAgentOrchestrator.WriteToolResult.Success(
                        transactionId = created.transactionId,
                        type = created.type,
                        action = created.action,
                        resultJson = buildToolSuccessJson(created),
                    )
                }

                override suspend fun update(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                    val hasPatch = draft.amount != null ||
                        !draft.category.isNullOrBlank() ||
                        !draft.recordTime.isNullOrBlank() ||
                        !draft.date.isNullOrBlank() ||
                        !draft.desc.isNullOrBlank()
                    if (!hasPatch) {
                        return LedgerAgentOrchestrator.WriteToolResult.Failure(
                            reason = "我知道你想改账单啦，再告诉我想改哪一项（金额、分类、时间或备注）就好。",
                        )
                    }

                    val updated = tryUpdateTransaction(
                        draft = draft,
                        assistantText = assistantText,
                        userInput = userInput,
                        rawAmount = draft.amount,
                    ) ?: return LedgerAgentOrchestrator.WriteToolResult.Failure(
                        reason = "我暂时没定位到要修改的那笔记录，你可以补一句“哪一天/哪一笔”。",
                    )

                    return LedgerAgentOrchestrator.WriteToolResult.Success(
                        transactionId = updated.transactionId,
                        type = updated.type,
                        action = updated.action,
                        resultJson = buildToolSuccessJson(updated),
                    )
                }

                override suspend fun delete(draft: AiReceiptDraft): LedgerAgentOrchestrator.WriteToolResult {
                    val cacheKey = buildDeletePlanCacheKey(draft, userInput)
                    val deletePlan = deletePlanCache[cacheKey] ?: buildDeletePlan(
                        draft = draft,
                        userInput = userInput,
                    )

                    if (deletePlan.targets.isEmpty()) {
                        return LedgerAgentOrchestrator.WriteToolResult.Failure(
                            reason = "我暂时没找到可删除的记录，你可以补充关键词或日期。",
                        )
                    }

                    if (deletePlan.requiresConfirmation && !deletePlan.isConfirmed) {
                        return LedgerAgentOrchestrator.WriteToolResult.Failure(
                            reason = deletePlan.previewMessage,
                        )
                    }

                    deletePlan.targets.forEach { target ->
                        transactionDao.deleteTransactionById(target.id)
                    }

                    val firstTarget = deletePlan.targets.first()
                    return LedgerAgentOrchestrator.WriteToolResult.Success(
                        transactionId = firstTarget.id,
                        type = firstTarget.type,
                        action = ACTION_DELETE,
                        resultJson = buildDeleteToolSuccessJson(deletePlan),
                        affectedTransactions = deletePlan.targets.map { target ->
                            LedgerAgentOrchestrator.WriteToolResult.ToolTransactionRef(
                                transactionId = target.id,
                                type = target.type,
                            )
                        },
                    )
                }
            },
        )

        val applied = buildList {
            var appliedIndex = 1
            orchestrationResult.successes
                .sortedBy { it.index }
                .forEach { success ->
                    val affected = success.affectedTransactions.ifEmpty {
                        listOf(
                            LedgerAgentOrchestrator.WriteToolResult.ToolTransactionRef(
                                transactionId = success.transactionId,
                                type = success.type,
                            ),
                        )
                    }
                    affected.forEach { target ->
                        add(
                            AppliedTransaction(
                                transactionId = target.transactionId,
                                type = target.type,
                                action = success.action,
                                draft = success.draft,
                                index = appliedIndex,
                            ),
                        )
                        appliedIndex += 1
                    }
                }
            }

        val failed = orchestrationResult.failures
            .sortedBy { it.index }
            .map { failure ->
                FailedTransaction(
                    index = failure.index,
                    draft = failure.draft,
                    reason = failure.reason,
                )
            }

        val errors = failed
            .map { it.reason.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        return ApplyTransactionsResult(
            appliedTransactions = applied,
            failedTransactions = failed,
            errors = errors,
        )
    }

    suspend fun replayAgentCallsByRequestId(requestId: String): AgentReplayTrace? {
        return agentReplayService?.replayFromMirrorOrDatabase(requestId)
    }

    suspend fun queryTransactionsTool(args: AgentToolArgs.QueryTransactionsArgs): QueryToolCallResult<QueryTransactionsResult> {
        val executor = QueryInsightsToolExecutor(
            sourceProvider = { loadTransactionSnapshotsForQuery(args) },
        )
        return executor.queryTransactions(args)
    }

    suspend fun querySpendingStatsTool(args: AgentToolArgs.QuerySpendingStatsArgs): QueryToolCallResult<QuerySpendingStatsResult> {
        val executor = QueryInsightsToolExecutor(
            sourceProvider = { loadTransactionSnapshotsForStats(args) },
        )
        return executor.querySpendingStats(args)
    }

    private suspend fun loadTransactionSnapshotsForQuery(args: AgentToolArgs.QueryTransactionsArgs): List<LedgerTransactionSnapshot> {
        val snapshotLimit = resolveQuerySnapshotLimit(args)
        return loadTransactionSnapshotsByWindow(
            window = args.window,
            startAtMillis = args.startAtMillis,
            endAtMillis = args.endAtMillis,
            limit = snapshotLimit,
        )
    }

    private suspend fun loadTransactionSnapshotsForStats(args: AgentToolArgs.QuerySpendingStatsArgs): List<LedgerTransactionSnapshot> {
        val snapshotLimit = resolveStatsSnapshotLimit(args)
        return loadTransactionSnapshotsByWindow(
            window = args.window,
            startAtMillis = args.startAtMillis,
            endAtMillis = args.endAtMillis,
            limit = snapshotLimit,
        )
    }

    private suspend fun loadTransactionSnapshotsByWindow(
        window: String,
        startAtMillis: Long?,
        endAtMillis: Long?,
        limit: Int,
    ): List<LedgerTransactionSnapshot> {
        val safeLimit = limit.coerceIn(20, 1000)
        val range = resolveSnapshotRange(window, startAtMillis, endAtMillis)
        val entities = if (range != null) {
            transactionDao.getTransactionsInRange(
                startAtMillis = range.first,
                endAtMillis = range.second,
                limit = safeLimit,
            )
        } else {
            transactionDao.getRecentTransactions(limit = safeLimit)
        }

        return entities.map { entity ->
            LedgerTransactionSnapshot(
                transactionId = entity.id,
                type = entity.type,
                amount = entity.amount,
                categoryName = entity.categoryName,
                remark = entity.remark,
                recordTimestamp = entity.recordTimestamp,
            )
        }
    }

    private fun resolveSnapshotRange(
        window: String,
        startAtMillis: Long?,
        endAtMillis: Long?,
    ): Pair<Long, Long>? {
        val now = System.currentTimeMillis()
        val todayStart = startOfDayTimestamp(now)
        val todayEnd = endOfDayTimestamp(now)

        return when (window) {
            "today" -> todayStart to todayEnd
            "yesterday" -> (todayStart - oneDayMillis) to (todayEnd - oneDayMillis)
            "last7days" -> (todayStart - 6L * oneDayMillis) to todayEnd
            "last30days" -> (todayStart - 29L * oneDayMillis) to todayEnd
            "last12months" -> {
                val rangeStart = Calendar.getInstance().apply {
                    timeInMillis = now
                    add(Calendar.MONTH, -11)
                    set(Calendar.DAY_OF_MONTH, 1)
                    set(Calendar.HOUR_OF_DAY, 0)
                    set(Calendar.MINUTE, 0)
                    set(Calendar.SECOND, 0)
                    set(Calendar.MILLISECOND, 0)
                }.timeInMillis
                rangeStart to todayEnd
            }

            "custom" -> {
                if (startAtMillis != null && endAtMillis != null && startAtMillis <= endAtMillis) {
                    startAtMillis to endAtMillis
                } else {
                    null
                }
            }

            else -> null
        }
    }

    private fun resolveQuerySnapshotLimit(args: AgentToolArgs.QueryTransactionsArgs): Int {
        val hasFilter = args.filters.transactionId != null ||
            !args.filters.keyword.isNullOrBlank() ||
            args.filters.amountMin != null ||
            args.filters.amountMax != null ||
            !args.filters.dateKeyword.isNullOrBlank()

        return when {
            isSimpleLatestQuery(args, hasFilter) -> 40
            args.sortKey == "record_time_desc" && args.limit <= 3 && !hasFilter -> 80
            args.sortKey == "record_time_desc" -> 180
            args.sortKey == "amount_desc" && hasFilter -> 480
            args.sortKey == "amount_desc" -> 600
            else -> 320
        }
    }

    private fun isSimpleLatestQuery(
        args: AgentToolArgs.QueryTransactionsArgs,
        hasFilter: Boolean,
    ): Boolean {
        return args.sortKey == "record_time_desc" &&
            args.limit <= 1 &&
            !hasFilter &&
            args.window != "last12months"
    }

    private fun resolveStatsSnapshotLimit(args: AgentToolArgs.QuerySpendingStatsArgs): Int {
        val base = when (args.window) {
            "today", "yesterday" -> 160
            "last7days" -> 320
            "last30days" -> 520
            "last12months" -> 800
            "custom" -> 600
            else -> 480
        }

        val byTopN = (args.topN.coerceIn(1, 50) * 18).coerceAtLeast(120)
        return maxOf(base, byTopN).coerceAtMost(1000)
    }

    private suspend fun handleQueryIntent(
        requestId: String,
        userInput: String,
        plan: QueryIntentPlan,
    ): QueryIntentExecutionResult {
        val context = AgentRequestContext(
            requestId = requestId,
            idempotencyKey = buildIdempotencyKey(userInput, emptyList()),
            userInput = userInput,
            startedAt = System.currentTimeMillis(),
        )

        agentRunLogger.markRunStarted(context)
        val startedAt = System.currentTimeMillis()

        val execution = runCatching {
            executeQueryIntentPlan(
                userInput = userInput,
                plan = plan,
            )
        }.getOrElse { error ->
            QueryIntentExecutionResult(
                toolName = plan.toolName,
                status = AgentToolStatus.FAILURE,
                argsJson = if (plan.toolName == AgentToolName.QUERY_TRANSACTIONS) {
                    plan.queryArgs?.toQueryArgsJson().orEmpty()
                } else {
                    plan.statsArgs?.toStatsArgsJson().orEmpty()
                },
                resultJson = "{\"status\":\"failure\",\"message\":\"${error.message.orEmpty()}\"}",
                assistantReply = "我这次查询遇到点小问题，稍后再试一次就好。",
                errorCode = AgentErrorCode.UNEXPECTED_ERROR,
                errorMessage = error.message,
            )
        }

        val finishedAt = System.currentTimeMillis()
        val latencyMs = (finishedAt - startedAt).coerceAtLeast(0L)

        agentRunLogger.appendToolCall(
            AgentToolCallRecord(
                requestId = context.requestId,
                runId = context.requestId,
                stepIndex = 1,
                toolName = execution.toolName,
                argsJson = execution.argsJson,
                resultJson = execution.resultJson,
                status = execution.status,
                errorCode = execution.errorCode,
                errorMessage = execution.errorMessage,
                latencyMs = latencyMs,
                timestamp = finishedAt,
            ),
        )

        val runStatus = when (execution.status) {
            AgentToolStatus.SUCCESS -> AgentRunStatus.SUCCESS
            AgentToolStatus.PARTIAL_SUCCESS -> AgentRunStatus.PARTIAL_SUCCESS
            else -> AgentRunStatus.FAILED
        }

        agentRunLogger.markRunFinished(
            requestId = context.requestId,
            status = runStatus,
            finishedAt = finishedAt,
            errorCode = execution.errorCode,
            errorMessage = execution.errorMessage,
        )

        applyHumanizedLocalDelay(userInput)

        val insightMeta = buildInsightNoteTag(
            toolName = execution.toolName,
            resultJson = execution.resultJson,
        )
        val visibleReply = mergeAssistantText(
            baseText = execution.assistantReply,
            extraText = insightMeta,
            fallbackText = execution.assistantReply.ifBlank { "我已经帮你查好了。" },
        )

        val assistantMessageIds = mutableListOf<Long>()
        syncAssistantReplyChunks(
            messageIds = assistantMessageIds,
            chunks = splitAssistantReply(visibleReply),
            baseTimestamp = System.currentTimeMillis() + 1,
            isReceiptLast = false,
            receiptTransactionId = null,
            receiptTransactionBindings = null,
        )

        return execution
    }

    private suspend fun executeQueryIntentPlan(
        userInput: String,
        plan: QueryIntentPlan,
    ): QueryIntentExecutionResult {
        return when (plan.toolName) {
            AgentToolName.QUERY_TRANSACTIONS -> {
                val args = plan.queryArgs ?: buildQueryArgsFromText(userInput)
                val result = queryTransactionsTool(args)
                QueryIntentExecutionResult(
                    toolName = AgentToolName.QUERY_TRANSACTIONS,
                    status = result.status,
                    argsJson = args.toQueryArgsJson(),
                    resultJson = result.resultJson,
                    assistantReply = buildQueryAssistantReply(userInput, args, result),
                    errorCode = result.validationIssues.firstOrNull()?.code,
                    errorMessage = result.validationIssues.firstOrNull()?.message,
                )
            }

            AgentToolName.QUERY_SPENDING_STATS -> {
                val args = plan.statsArgs ?: buildStatsArgsFromText(userInput)
                val result = querySpendingStatsTool(args)
                QueryIntentExecutionResult(
                    toolName = AgentToolName.QUERY_SPENDING_STATS,
                    status = result.status,
                    argsJson = args.toStatsArgsJson(),
                    resultJson = result.resultJson,
                    assistantReply = buildStatsAssistantReply(userInput, args, result),
                    errorCode = result.validationIssues.firstOrNull()?.code,
                    errorMessage = result.validationIssues.firstOrNull()?.message,
                )
            }

            else -> {
                QueryIntentExecutionResult(
                    toolName = plan.toolName,
                    status = AgentToolStatus.FAILURE,
                    argsJson = "{}",
                    resultJson = "{\"status\":\"failure\",\"message\":\"tool not implemented\"}",
                    assistantReply = "当前这类查询工具还没接好，我先记下你的需求。",
                    errorCode = AgentErrorCode.TOOL_NOT_IMPLEMENTED,
                    errorMessage = "tool not implemented",
                )
            }
        }
    }

    private fun buildQueryIntentPlan(userInput: String): QueryIntentPlan? {
        val input = userInput.trim()
        if (input.isBlank()) return null

        val hasStrongQueryHint = queryStrongIntentHints.any { hint -> input.contains(hint) }
        val hasSoftQueryHint = querySoftIntentHints.any { hint -> input.contains(hint) }
        val hasWindowHint = queryWindowRelaxedHints.any { hint -> input.contains(hint) }
        val hasQuestionSignal = input.contains("?") || input.contains("？")
        val hasSemanticSpendSignal = hasSemanticSpendQuerySignal(input)
        val hasQuerySubjectHint = queryKeywordHints.any { hint -> input.contains(hint) } ||
            statsIntentHints.any { hint -> input.contains(hint) } ||
            trendHints.any { hint -> input.contains(hint) } ||
            frequencyHints.any { hint -> input.contains(hint) } ||
            ratioHints.any { hint -> input.contains(hint) } ||
            spendQuestionHints.any { hint -> input.contains(hint) }
        val hasExplicitWriteAction = updateIntentHints.any { hint -> input.contains(hint) } ||
            deleteIntentHints.any { hint -> input.contains(hint) }

        if (hasExplicitWriteAction) return null

        val relaxedQuerySignal = hasSoftQueryHint ||
            hasSemanticSpendSignal ||
            (hasWindowHint && (hasQuestionSignal || hasQuerySubjectHint || input.contains("多少")))
        if (!hasStrongQueryHint && !relaxedQuerySignal) return null

        val hasWriteHint = writeIntentHints.any { hint -> input.contains(hint) }
        val hasQueryOverride = queryOverrideHints.any { hint -> input.contains(hint) }
        if (hasWriteHint && !hasQueryOverride) return null

        val statsIntent = statsIntentHints.any { hint -> input.contains(hint) } || hasSemanticSpendSignal
        return if (statsIntent) {
            QueryIntentPlan(
                toolName = AgentToolName.QUERY_SPENDING_STATS,
                statsArgs = buildStatsArgsFromText(input),
            )
        } else {
            QueryIntentPlan(
                toolName = AgentToolName.QUERY_TRANSACTIONS,
                queryArgs = buildQueryArgsFromText(input),
            )
        }
    }

    private fun buildWriteDraftsFromInput(userInput: String): List<AiReceiptDraft> {
        val input = userInput.trim()
        if (input.isBlank()) return emptyList()

        val hasWriteHint = writeIntentHints.any { hint -> input.contains(hint) }
        val hasAmount = parseAmountCandidates(input).isNotEmpty()
        val hasQuerySignal = queryStrongIntentHints.any { hint -> input.contains(hint) } ||
            querySoftIntentHints.any { hint -> input.contains(hint) } ||
            statsIntentHints.any { hint -> input.contains(hint) } ||
            (queryWindowRelaxedHints.any { hint -> input.contains(hint) } &&
                (input.contains("查") || input.contains("看") || input.contains("统计") || input.contains("分析")))
        val hasWriteSceneHint = writeSceneHints.any { hint -> input.contains(hint) }

        if (hasQuerySignal && !hasWriteHint) return emptyList()
        if (!hasWriteHint && !(hasAmount && hasWriteSceneHint)) return emptyList()

        val action = when {
            deleteIntentHints.any { hint -> input.contains(hint) } -> ACTION_DELETE
            updateIntentHints.any { hint -> input.contains(hint) } -> ACTION_UPDATE
            else -> ACTION_CREATE
        }

        val transactionId = parseTransactionIdHint(input)
        val dateHint = resolveRelativeDateKeyword(input)

        if (action == ACTION_DELETE) {
            return listOf(
                AiReceiptDraft(
                    isReceipt = true,
                    action = ACTION_DELETE,
                    amount = parseAmountCandidates(input).firstOrNull(),
                    category = inferCategoryFromText(input, type = 0),
                    desc = input.take(24),
                    recordTime = null,
                    date = dateHint,
                    transactionId = transactionId,
                ),
            )
        }

        if (action == ACTION_UPDATE) {
            val amount = parseAmountCandidates(input).firstOrNull()
            return if (amount != null || transactionId != null) {
                listOf(
                    AiReceiptDraft(
                        isReceipt = true,
                        action = ACTION_UPDATE,
                        amount = amount,
                        category = inferCategoryFromText(input, type = 0),
                        desc = input.take(24),
                        recordTime = null,
                        date = dateHint,
                        transactionId = transactionId,
                    ),
                )
            } else {
                emptyList()
            }
        }

        val segments = input.split(Regex("[，,、；;]\\s*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val draftList = mutableListOf<AiReceiptDraft>()
        segments.forEach { segment ->
            val amount = parseAmountCandidates(segment).firstOrNull() ?: return@forEach
            val inferredCategory = inferCategoryFromText(segment, type = 0)
                ?: inferCategoryFromText(input, type = 0)
            val desc = normalizeWriteDesc(segment)
            draftList += AiReceiptDraft(
                isReceipt = true,
                action = ACTION_CREATE,
                amount = amount,
                category = inferredCategory,
                desc = desc,
                recordTime = null,
                date = dateHint,
                transactionId = null,
            )
        }

        if (draftList.isNotEmpty()) return draftList

        val singleAmount = parseAmountCandidates(input).firstOrNull() ?: return emptyList()
        return listOf(
            AiReceiptDraft(
                isReceipt = true,
                action = ACTION_CREATE,
                amount = singleAmount,
                category = inferCategoryFromText(input, type = 0),
                desc = normalizeWriteDesc(input),
                recordTime = null,
                date = dateHint,
                transactionId = null,
            ),
        )
    }

    private fun parseAmountCandidates(text: String): List<Double> {
        return amountNormalizer.parseAmountCandidates(text)
    }

    private fun parseTransactionIdHint(text: String): Long? {
        transactionIdHintRegexes.forEach { regex ->
            val candidate = regex.find(text)
                ?.groupValues
                ?.getOrNull(1)
                ?.toLongOrNull()
                ?.takeIf { it > 0L }
            if (candidate != null) return candidate
        }
        return null
    }

    private fun resolveRelativeDateKeyword(text: String): String? {
        return when {
            text.contains("大前天") -> "大前天"
            text.contains("前天") -> "前天"
            text.contains("昨天") || text.contains("昨日") -> "昨天"
            text.contains("今天") || text.contains("今日") -> "今天"
            else -> null
        }
    }

    private fun normalizeWriteDesc(text: String): String {
        return text
            .replace(amountWithCurrencyRegex, "")
            .replace("记一笔", "")
            .replace("记账", "")
            .replace("花了", "")
            .replace("消费", "")
            .replace("吃中饭", "")
            .replace("吃饭", "")
            .replace("吃了", "")
            .replace("买了", "")
            .trim()
            .ifBlank { text.trim() }
            .take(24)
    }

    private suspend fun handleWriteIntent(
        requestId: String,
        userInput: String,
        drafts: List<AiReceiptDraft>,
    ): WriteIntentExecutionResult {
        val applyResult = applyReceiptTransactions(
            requestId = requestId,
            drafts = drafts,
            assistantText = "",
            userInput = userInput,
        )

        val status = when {
            applyResult.hasSuccess && applyResult.failedTransactions.isEmpty() -> AgentToolStatus.SUCCESS
            applyResult.hasSuccess -> AgentToolStatus.PARTIAL_SUCCESS
            else -> AgentToolStatus.FAILURE
        }

        val styleReply = styleFormatter.formatWrite(
            facts = AgentWriteStyleFacts(
                successCount = applyResult.appliedTransactions.size,
                failureCount = applyResult.failedTransactions.size,
                createCount = applyResult.createCount,
                updateCount = applyResult.updateCount,
                deleteCount = applyResult.deleteCount,
                errors = applyResult.errors,
                primaryAction = applyResult.primaryAppliedTransaction?.action,
                primaryCategory = applyResult.primaryAppliedTransaction?.draft?.category,
                primaryDesc = applyResult.primaryAppliedTransaction?.draft?.desc,
            ),
            requestId = requestId,
        )

        val receiptMeta = buildReceiptMetaTag(applyResult)
        val replyChunks = splitAssistantReply(styleReply)
        val defaultReceiptText = defaultReceiptMessage(applyResult)
        val receiptText = replyChunks.lastOrNull()?.ifBlank { defaultReceiptText } ?: defaultReceiptText
        val receiptContent = if (receiptMeta.isBlank()) {
            receiptText
        } else {
            "$receiptText\n$receiptMeta"
        }
        val finalChunks = if (replyChunks.isEmpty()) {
            listOf(receiptContent)
        } else {
            replyChunks.dropLast(1) + receiptContent
        }
        applyHumanizedLocalDelay(userInput)
        val assistantMessageIds = mutableListOf<Long>()
        syncAssistantReplyChunks(
            messageIds = assistantMessageIds,
            chunks = finalChunks,
            baseTimestamp = System.currentTimeMillis() + 1,
            isReceiptLast = true,
            receiptTransactionId = applyResult.primaryAppliedTransaction?.transactionId,
            receiptTransactionBindings = buildTransactionBindingsPayload(applyResult.appliedTransactions),
        )

        return WriteIntentExecutionResult(
            status = status,
            assistantReply = styleReply,
            applyResult = applyResult,
            errorCode = if (status == AgentToolStatus.FAILURE) AgentErrorCode.VALIDATION_FAILED else null,
            errorMessage = if (status == AgentToolStatus.FAILURE) {
                applyResult.errors.firstOrNull() ?: "写入执行失败"
            } else {
                null
            },
        )
    }

    private suspend fun recordQualityFeedback(
        requestId: String,
        routePath: AgentRoutePath,
        stage: AgentQualityStage,
        userInput: String,
        expectedAction: String?,
        actualAction: String?,
        runStatus: String,
        fallbackUsed: Boolean,
        isMisjudged: Boolean,
        errorCode: String?,
        errorMessage: String?,
        metadataJson: String?,
    ) {
        qualityFeedbackRepository?.record(
            AgentQualityFeedbackInput(
                requestId = requestId,
                routePath = routePath,
                stage = stage,
                userInput = userInput,
                expectedAction = expectedAction,
                actualAction = actualAction,
                runStatus = runStatus,
                fallbackUsed = fallbackUsed,
                isMisjudged = isMisjudged,
                errorCode = errorCode,
                errorMessage = errorMessage,
                metadataJson = metadataJson,
                createdAt = System.currentTimeMillis(),
            ),
        )
    }

    private suspend fun runPlannerShadow(
        requestId: String,
        userInput: String,
    ): IntentPlanV2? {
        return runCatching {
            agentPlanner.plan(
                PlannerInputV2(
                    requestId = requestId,
                    userInput = userInput,
                    nowMillis = System.currentTimeMillis(),
                    timezoneId = TimeZone.getDefault().id,
                ),
            )
        }.getOrNull()
    }

    private suspend fun shouldUsePlannerPrimary(requestId: String): Boolean {
        if (!plannerPrimaryEnabled) return false

        val rollout = plannerPrimaryRolloutPercent.coerceIn(0, 100)
        if (rollout <= 0) return false

        val bucketHit = if (rollout >= 100) {
            true
        } else {
            val bucket = ((requestId.hashCode().toLong() and 0x7FFFFFFF) % 100L).toInt()
            bucket < rollout
        }
        if (!bucketHit) return false

        if (!plannerRolloutGateEnabled) return true
        return evaluatePlannerRolloutGate().allowed
    }

    private suspend fun evaluatePlannerRolloutGate(): PlannerRolloutGuardSnapshot {
        val sinceMillis = System.currentTimeMillis() - plannerGateWindowDays.coerceAtLeast(1) * oneDayMillis
        val report = qualityFeedbackRepository?.buildObservationReport(sinceMillis)
            ?: return PlannerRolloutGuardSnapshot(
                allowed = true,
                reason = "no_quality_repository",
                sampleCount = 0,
                misjudgeRate = 0.0,
                mismatchSamples = 0,
            )

        val buckets = report.buckets.filter {
            it.routePath == AgentRoutePath.PLANNER_PRIMARY.name && it.stage == AgentQualityStage.TOOL_EXECUTION.name
        }
        if (buckets.isEmpty()) {
            return PlannerRolloutGuardSnapshot(
                allowed = true,
                reason = "insufficient_samples",
                sampleCount = 0,
                misjudgeRate = 0.0,
                mismatchSamples = 0,
            )
        }

        val sampleCount = buckets.sumOf { it.totalSamples }
        val misjudgedSamples = buckets.sumOf { it.misjudgedSamples }
        val mismatchSamples = buckets.sumOf { it.mismatchSamples }
        val misjudgeRate = if (sampleCount <= 0) 0.0 else misjudgedSamples.toDouble() / sampleCount.toDouble()

        if (sampleCount < plannerGateMinSamples) {
            return PlannerRolloutGuardSnapshot(
                allowed = true,
                reason = "insufficient_samples",
                sampleCount = sampleCount,
                misjudgeRate = misjudgeRate,
                mismatchSamples = mismatchSamples,
            )
        }

        if (misjudgeRate > plannerGateMaxMisjudgeRate) {
            return PlannerRolloutGuardSnapshot(
                allowed = false,
                reason = "misjudge_rate_exceeded",
                sampleCount = sampleCount,
                misjudgeRate = misjudgeRate,
                mismatchSamples = mismatchSamples,
            )
        }

        if (mismatchSamples > plannerGateMaxMismatchSamples) {
            return PlannerRolloutGuardSnapshot(
                allowed = false,
                reason = "mismatch_samples_exceeded",
                sampleCount = sampleCount,
                misjudgeRate = misjudgeRate,
                mismatchSamples = mismatchSamples,
            )
        }

        return PlannerRolloutGuardSnapshot(
            allowed = true,
            reason = "within_threshold",
            sampleCount = sampleCount,
            misjudgeRate = misjudgeRate,
            mismatchSamples = mismatchSamples,
        )
    }

    private suspend fun tryHandlePlannerPrimaryIntent(
        requestId: String,
        userInput: String,
        plannerPlan: IntentPlanV2?,
        isCorrectionInput: Boolean,
    ): Boolean {
        if (plannerPlan == null) return false
        if (plannerPlan.confidence < plannerPrimaryMinConfidence) return false

        val validationIssues = plannerOutputValidator.validate(plannerPlan)
        if (validationIssues.isNotEmpty()) {
            val pendingHandled = trySavePendingIntentFromPlan(
                requestId = requestId,
                userInput = userInput,
                plan = plannerPlan,
                issues = validationIssues,
            )

            val validationResult = PlannerPrimaryExecutionResult(
                expectedAction = plannerPlan.intent.name,
                actualAction = null,
                stage = AgentQualityStage.INTENT_ROUTING,
                status = AgentToolStatus.SKIPPED,
                errorCode = validationIssues.firstOrNull()?.code,
                errorMessage = validationIssues.firstOrNull()?.message,
                metadataJson = buildPlannerValidationIssuesJson(validationIssues),
                fallbackUsed = true,
                isMisjudged = true,
            )
            recordPlannerPrimaryFeedback(
                requestId = requestId,
                userInput = userInput,
                result = validationResult,
                isCorrectionInput = isCorrectionInput,
            )

            if (pendingHandled) {
                return true
            }
            return false
        }

        val executionResult = when (plannerPlan.intent) {
            PlannerIntentType.QUERY_TRANSACTIONS -> {
                val execution = handleQueryIntent(
                    requestId = requestId,
                    userInput = userInput,
                    plan = QueryIntentPlan(
                        toolName = AgentToolName.QUERY_TRANSACTIONS,
                        queryArgs = plannerPlan.queryArgs ?: buildQueryArgsFromText(userInput),
                    ),
                )
                PlannerPrimaryExecutionResult(
                    expectedAction = PlannerIntentType.QUERY_TRANSACTIONS.name,
                    actualAction = execution.toolName.name,
                    status = execution.status,
                    errorCode = execution.errorCode,
                    errorMessage = execution.errorMessage,
                    metadataJson = execution.resultJson,
                )
            }

            PlannerIntentType.QUERY_SPENDING_STATS -> {
                val execution = handleQueryIntent(
                    requestId = requestId,
                    userInput = userInput,
                    plan = QueryIntentPlan(
                        toolName = AgentToolName.QUERY_SPENDING_STATS,
                        statsArgs = plannerPlan.statsArgs ?: buildStatsArgsFromText(userInput),
                    ),
                )
                PlannerPrimaryExecutionResult(
                    expectedAction = PlannerIntentType.QUERY_SPENDING_STATS.name,
                    actualAction = execution.toolName.name,
                    status = execution.status,
                    errorCode = execution.errorCode,
                    errorMessage = execution.errorMessage,
                    metadataJson = execution.resultJson,
                )
            }

            PlannerIntentType.CREATE_TRANSACTIONS,
            PlannerIntentType.UPDATE_TRANSACTIONS,
            PlannerIntentType.DELETE_TRANSACTIONS,
            -> {
                val drafts = buildWriteDraftsFromPlannerPlan(
                    plannerPlan = plannerPlan,
                    userInput = userInput,
                )
                if (drafts.isEmpty()) {
                    return false
                }

                val execution = handleWriteIntent(
                    requestId = requestId,
                    userInput = userInput,
                    drafts = drafts,
                )
                PlannerPrimaryExecutionResult(
                    expectedAction = plannerPlan.intent.name,
                    actualAction = plannerPlan.intent.name,
                    status = execution.status,
                    errorCode = execution.errorCode,
                    errorMessage = execution.errorMessage,
                    metadataJson = buildReceiptMetaTag(execution.applyResult),
                )
            }

            else -> return false
        }

        recordPlannerPrimaryFeedback(
            requestId = requestId,
            userInput = userInput,
            result = executionResult,
            isCorrectionInput = isCorrectionInput,
        )
        return true
    }

    private suspend fun tryHandlePendingIntent(
        requestId: String,
        userInput: String,
        isCorrectionInput: Boolean,
    ): Boolean {
        val now = System.currentTimeMillis()
        val pending = pendingIntentStateStore.getActive(now) ?: return false
        if (userInput.contains("取消", ignoreCase = true) || userInput.contains("算了", ignoreCase = true)) {
            pendingIntentStateStore.clear()
            return false
        }

        val mergedDraft = mergePendingDraft(pending.draft, userInput)
        val unresolved = resolveMissingSlots(mergedDraft, pending.missingSlots)
        if (unresolved.isNotEmpty()) {
            pendingIntentStateStore.save(
                pending.copy(
                    draft = mergedDraft,
                    missingSlots = unresolved,
                    expiresAt = now + pendingIntentTtlMillis,
                ),
            )
            syncAssistantReplyChunks(
                messageIds = mutableListOf(),
                chunks = splitAssistantReply(buildMissingSlotPrompt(unresolved)),
                baseTimestamp = System.currentTimeMillis() + 1,
                isReceiptLast = false,
                receiptTransactionId = null,
                receiptTransactionBindings = null,
            )
            return true
        }

        val execution = handleWriteIntent(
            requestId = requestId,
            userInput = userInput,
            drafts = listOf(mergedDraft),
        )
        pendingIntentStateStore.clear()

        val expected = when (pending.action.lowercase(Locale.ROOT)) {
            ACTION_UPDATE -> PlannerIntentType.UPDATE_TRANSACTIONS.name
            ACTION_DELETE -> PlannerIntentType.DELETE_TRANSACTIONS.name
            else -> PlannerIntentType.CREATE_TRANSACTIONS.name
        }
        recordQualityFeedback(
            requestId = requestId,
            routePath = AgentRoutePath.PLANNER_PRIMARY,
            stage = AgentQualityStage.TOOL_EXECUTION,
            userInput = userInput,
            expectedAction = expected,
            actualAction = expected,
            runStatus = if (execution.status == AgentToolStatus.SUCCESS) "SUCCESS" else "FAILED",
            fallbackUsed = false,
            isMisjudged = isCorrectionInput || execution.status != AgentToolStatus.SUCCESS,
            errorCode = execution.errorCode?.name,
            errorMessage = execution.errorMessage,
            metadataJson = buildReceiptMetaTag(execution.applyResult),
        )
        return true
    }

    private fun mergePendingDraft(
        base: AiReceiptDraft,
        userInput: String,
    ): AiReceiptDraft {
        val amountHint = parseAmountCandidates(userInput).firstOrNull()
        val categoryHint = inferCategoryFromText(userInput, type = 0)
        val dateHint = resolveRelativeDateKeyword(userInput)
        val transactionIdHint = parseTransactionIdHint(userInput)
        val descHint = normalizeWriteDesc(userInput)

        return base.copy(
            amount = amountHint ?: base.amount,
            category = categoryHint ?: base.category,
            date = dateHint ?: base.date,
            transactionId = transactionIdHint ?: base.transactionId,
            desc = if (descHint.isNotBlank()) descHint else base.desc,
        )
    }

    private fun resolveMissingSlots(
        draft: AiReceiptDraft,
        preferred: List<String>,
    ): List<String> {
        val raw = if (preferred.isNotEmpty()) preferred else listOf("amount", "category")
        return raw.filter { slot ->
            when (slot) {
                "amount" -> draft.amount == null
                "category" -> draft.category.isNullOrBlank()
                "target" -> draft.transactionId == null && draft.desc.isNullOrBlank() && draft.category.isNullOrBlank() && draft.date.isNullOrBlank() && draft.amount == null
                else -> false
            }
        }
    }

    private fun buildMissingSlotPrompt(missingSlots: List<String>): String {
        val first = missingSlots.firstOrNull() ?: return "我还差一点信息，你补一句我就继续。"
        val hint = when (first) {
            "amount" -> "金额"
            "category" -> "分类"
            "target" -> "要操作的那笔记录"
            else -> "关键信息"
        }
        return "我还差一个信息：$hint。补一句我就继续处理。"
    }

    private suspend fun trySavePendingIntentFromPlan(
        requestId: String,
        userInput: String,
        plan: IntentPlanV2,
        issues: List<AgentValidationIssue>,
    ): Boolean {
        if (plan.intent !in setOf(
                PlannerIntentType.CREATE_TRANSACTIONS,
                PlannerIntentType.UPDATE_TRANSACTIONS,
                PlannerIntentType.DELETE_TRANSACTIONS,
            )
        ) {
            return false
        }

        val sourceItems = if (plan.writeItems.isNotEmpty()) plan.writeItems else plan.createItems
        val sourceItem = sourceItems.firstOrNull() ?: return false
        val fallbackAction = when (plan.intent) {
            PlannerIntentType.UPDATE_TRANSACTIONS -> ACTION_UPDATE
            PlannerIntentType.DELETE_TRANSACTIONS -> ACTION_DELETE
            else -> ACTION_CREATE
        }
        val normalizedAction = sourceItem.action
            .trim()
            .lowercase(Locale.ROOT)
            .ifBlank { fallbackAction }
        val draft = AiReceiptDraft(
            isReceipt = true,
            action = normalizedAction,
            amount = sourceItem.amount,
            category = sourceItem.category?.trim().takeUnless { it.isNullOrBlank() }
                ?: inferCategoryFromText(userInput, type = 0),
            desc = sourceItem.desc?.trim().orEmpty().ifBlank { normalizeWriteDesc(userInput) },
            recordTime = sourceItem.recordTime,
            date = sourceItem.date,
            transactionId = sourceItem.transactionId,
        )
        val missingSlots = plan.missingSlots
            .ifEmpty {
                issues.mapNotNull { issue ->
                    when {
                        issue.field.contains("amount") -> "amount"
                        issue.field.contains("category") -> "category"
                        issue.field.contains("target") -> "target"
                        else -> null
                    }
                }.distinct()
            }
            .ifEmpty { listOf("amount", "category") }

        val now = System.currentTimeMillis()
        pendingIntentStateStore.save(
            PendingIntentState(
                requestId = requestId,
                source = AgentRoutePath.PLANNER_PRIMARY.name,
                action = draft.action,
                draft = draft,
                missingSlots = missingSlots,
                createdAt = now,
                expiresAt = now + pendingIntentTtlMillis,
            ),
        )

        syncAssistantReplyChunks(
            messageIds = mutableListOf(),
            chunks = splitAssistantReply(buildMissingSlotPrompt(missingSlots)),
            baseTimestamp = System.currentTimeMillis() + 1,
            isReceiptLast = false,
            receiptTransactionId = null,
            receiptTransactionBindings = null,
        )
        return true
    }

    private suspend fun recordPlannerPrimaryFeedback(
        requestId: String,
        userInput: String,
        result: PlannerPrimaryExecutionResult,
        isCorrectionInput: Boolean,
    ) {
        val runStatus = when (result.status) {
            AgentToolStatus.SUCCESS -> "SUCCESS"
            AgentToolStatus.PARTIAL_SUCCESS -> "PARTIAL_SUCCESS"
            AgentToolStatus.SKIPPED -> "SKIPPED"
            else -> "FAILED"
        }

        recordQualityFeedback(
            requestId = requestId,
            routePath = AgentRoutePath.PLANNER_PRIMARY,
            stage = result.stage,
            userInput = userInput,
            expectedAction = result.expectedAction,
            actualAction = result.actualAction,
            runStatus = runStatus,
            fallbackUsed = result.fallbackUsed,
            isMisjudged = result.isMisjudged || isCorrectionInput,
            errorCode = result.errorCode?.name,
            errorMessage = result.errorMessage,
            metadataJson = result.metadataJson,
        )
    }

    private fun buildPlannerValidationIssuesJson(issues: List<AgentValidationIssue>): String {
        return JSONObject().apply {
            put(
                "issues",
                JSONArray().apply {
                    issues.forEach { issue ->
                        put(
                            JSONObject().apply {
                                put("field", issue.field)
                                put("code", issue.code.name)
                                put("message", issue.message)
                            },
                        )
                    }
                },
            )
        }.toString()
    }

    private fun buildWriteDraftsFromPlannerPlan(
        plannerPlan: IntentPlanV2,
        userInput: String,
    ): List<AiReceiptDraft> {
        val sourceItems = if (plannerPlan.writeItems.isNotEmpty()) plannerPlan.writeItems else plannerPlan.createItems

        return sourceItems.mapNotNull { item ->
            val fallbackAction = when (plannerPlan.intent) {
                PlannerIntentType.UPDATE_TRANSACTIONS -> ACTION_UPDATE
                PlannerIntentType.DELETE_TRANSACTIONS -> ACTION_DELETE
                else -> ACTION_CREATE
            }
            val normalizedAction = item.action
                .trim()
                .lowercase(Locale.ROOT)
                .ifBlank { fallbackAction }

            val amount = item.amount
            val normalizedDesc = item.desc?.trim().orEmpty().ifBlank { normalizeWriteDesc(userInput) }
            val category = item.category?.trim().takeUnless { it.isNullOrBlank() }
                ?: inferCategoryFromText(normalizedDesc, type = 0)
                ?: inferCategoryFromText(userInput, type = 0)

            AiReceiptDraft(
                isReceipt = true,
                action = normalizedAction,
                amount = amount,
                category = category,
                desc = normalizedDesc,
                recordTime = item.recordTime,
                date = item.date,
                transactionId = item.transactionId,
            )
        }.filter { draft ->
            when (draft.action) {
                ACTION_CREATE -> draft.amount != null
                ACTION_UPDATE -> draft.amount != null || !draft.category.isNullOrBlank() || !draft.recordTime.isNullOrBlank() || !draft.date.isNullOrBlank() || !draft.desc.isNullOrBlank()
                ACTION_DELETE -> draft.transactionId != null || !draft.category.isNullOrBlank() || !draft.date.isNullOrBlank() || !draft.desc.isNullOrBlank() || draft.amount != null
                else -> false
            }
        }
    }

    private suspend fun recordPlannerShadowFeedback(
        requestId: String,
        userInput: String,
        routePath: AgentRoutePath,
        plannerPlan: IntentPlanV2?,
        legacyAction: String?,
        legacyRunStatus: String,
        fallbackUsed: Boolean,
        isCorrectionInput: Boolean,
    ) {
        if (!plannerShadowEnabled) return

        val normalizedLegacyAction = normalizeLegacyAction(legacyAction)
        val plannerAction = normalizePlannerAction(plannerPlan)
        val comparable = plannerPlan != null && !normalizedLegacyAction.isNullOrBlank() && !plannerAction.isNullOrBlank()
        val isMatch = comparable && plannerAction == normalizedLegacyAction

        val shadowRunStatus = when {
            plannerPlan == null -> "SHADOW_NO_PLAN"
            comparable && isMatch -> "SHADOW_MATCH"
            comparable -> "SHADOW_MISMATCH"
            else -> "SHADOW_PARTIAL"
        }

        recordQualityFeedback(
            requestId = requestId,
            routePath = routePath,
            stage = AgentQualityStage.PLANNER_SHADOW,
            userInput = userInput,
            expectedAction = normalizedLegacyAction,
            actualAction = plannerAction,
            runStatus = shadowRunStatus,
            fallbackUsed = fallbackUsed,
            isMisjudged = isCorrectionInput || (comparable && !isMatch),
            errorCode = null,
            errorMessage = null,
            metadataJson = buildPlannerShadowMetadata(
                plannerPlan = plannerPlan,
                legacyAction = legacyAction,
                normalizedLegacyAction = normalizedLegacyAction,
                normalizedPlannerAction = plannerAction,
                legacyRunStatus = legacyRunStatus,
                isMatch = isMatch,
            ),
        )
    }

    private fun normalizeLegacyAction(action: String?): String? {
        val normalized = action?.trim()?.uppercase(Locale.ROOT).orEmpty()
        if (normalized.isBlank()) return null

        return when (normalized) {
            ACTION_CREATE.uppercase(Locale.ROOT) -> PlannerIntentType.CREATE_TRANSACTIONS.name
            ACTION_UPDATE.uppercase(Locale.ROOT) -> PlannerIntentType.UPDATE_TRANSACTIONS.name
            ACTION_DELETE.uppercase(Locale.ROOT) -> PlannerIntentType.DELETE_TRANSACTIONS.name
            AgentToolName.QUERY_TRANSACTIONS.name -> PlannerIntentType.QUERY_TRANSACTIONS.name
            AgentToolName.QUERY_SPENDING_STATS.name -> PlannerIntentType.QUERY_SPENDING_STATS.name
            "PROMPT_FALLBACK", "PROMPT_FALLBACK_BLOCKED" -> PlannerIntentType.CHITCHAT.name
            else -> normalized
        }
    }

    private fun normalizePlannerAction(plan: IntentPlanV2?): String? {
        return plan?.intent?.name
    }

    private fun buildPlannerShadowMetadata(
        plannerPlan: IntentPlanV2?,
        legacyAction: String?,
        normalizedLegacyAction: String?,
        normalizedPlannerAction: String?,
        legacyRunStatus: String,
        isMatch: Boolean,
    ): String {
        return JSONObject().apply {
            put("legacyActionRaw", legacyAction)
            put("legacyAction", normalizedLegacyAction)
            put("legacyRunStatus", legacyRunStatus)
            put("plannerAction", normalizedPlannerAction)
            put("plannerAvailable", plannerPlan != null)
            put("isMatch", isMatch)

            if (plannerPlan != null) {
                put("confidence", plannerPlan.confidence)
                put("targetMode", plannerPlan.targetMode.name)
                put("riskLevel", plannerPlan.riskLevel.name)
                put("needsConfirmation", plannerPlan.needsConfirmation)
                put("missingSlots", JSONArray(plannerPlan.missingSlots))
                plannerPlan.queryArgs?.let { args ->
                    put("queryArgs", JSONObject(args.toQueryArgsJson()))
                }
                plannerPlan.statsArgs?.let { args ->
                    put("statsArgs", JSONObject(args.toStatsArgsJson()))
                }
            }
        }.toString()
    }

    suspend fun getAgentQualityMetrics(windowDays: Int = 30): AgentQualityMetrics? {
        val sinceMillis = System.currentTimeMillis() - windowDays.coerceAtLeast(1) * oneDayMillis
        return qualityFeedbackRepository?.computeMetrics(sinceMillis)
    }

    suspend fun getPlannerObservationReport(windowDays: Int = 7): AgentObservationReport? {
        val sinceMillis = System.currentTimeMillis() - windowDays.coerceAtLeast(1) * oneDayMillis
        return qualityFeedbackRepository?.buildObservationReport(sinceMillis)
    }

    private fun isUserCorrectionInput(text: String): Boolean {
        return correctionIntentHints.any { hint -> text.contains(hint) }
    }

    private fun buildQueryArgsFromText(userInput: String): AgentToolArgs.QueryTransactionsArgs {
        val (window, startAt, endAt) = resolveWindowAndRange(userInput)
        val isHighestQuery = highestSpendingHints.any { hint -> userInput.contains(hint) }
        val sortKey = if (isHighestQuery) "amount_desc" else "record_time_desc"
        val limit = when {
            recentOneHints.any { hint -> userInput.contains(hint) } -> 1
            else -> parseTopNHint(userInput) ?: if (isHighestQuery) 1 else 10
        }

        return AgentToolArgs.QueryTransactionsArgs(
            filters = TransactionFilter(
                keyword = extractQueryKeyword(userInput),
                amountMin = parseAmountBound(userInput, amountMinRegexes),
                amountMax = parseAmountBound(userInput, amountMaxRegexes),
            ),
            window = window,
            sortKey = sortKey,
            limit = limit.coerceIn(1, 100),
            startAtMillis = startAt,
            endAtMillis = endAt,
        )
    }

    private fun buildStatsArgsFromText(userInput: String): AgentToolArgs.QuerySpendingStatsArgs {
        val (window, startAt, endAt) = resolveWindowAndRange(userInput)
        val groupBy = inferStatsGroupBy(userInput, window)
        val metric = inferStatsMetric(userInput)
        val sortKey = if (metric == "frequency") "frequency_desc" else "value_desc"
        val defaultTopN = when (groupBy) {
            "day" -> 31
            "month" -> 12
            else -> 5
        }

        return AgentToolArgs.QuerySpendingStatsArgs(
            window = window,
            groupBy = groupBy,
            metric = metric,
            sortKey = sortKey,
            topN = (parseTopNHint(userInput) ?: defaultTopN).coerceIn(1, 50),
            startAtMillis = startAt,
            endAtMillis = endAt,
        )
    }

    private fun resolveWindowAndRange(userInput: String): Triple<String, Long?, Long?> {
        val now = System.currentTimeMillis()

        val explicitDayCount = parseRecentDayCount(userInput)
        if (explicitDayCount != null) {
            return when (explicitDayCount) {
                1 -> Triple("today", null, null)
                7 -> Triple("last7days", null, null)
                30 -> Triple("last30days", null, null)
                in 365..999 -> Triple("last12months", null, null)
                else -> {
                    val safeDays = explicitDayCount.coerceIn(2, 180)
                    val endAt = endOfDayTimestamp(now)
                    val startAt = startOfDayTimestamp(endAt - (safeDays - 1L) * oneDayMillis)
                    Triple("custom", startAt, endAt)
                }
            }
        }

        return when {
            userInput.contains("昨天") || userInput.contains("昨日") -> Triple("yesterday", null, null)
            userInput.contains("今天") || userInput.contains("今日") -> Triple("today", null, null)
            weekWindowHints.any { hint -> userInput.contains(hint) } -> Triple("last7days", null, null)
            monthWindowHints.any { hint -> userInput.contains(hint) } -> Triple("last30days", null, null)
            yearWindowHints.any { hint -> userInput.contains(hint) } -> Triple("last12months", null, null)
            else -> Triple("last30days", null, null)
        }
    }

    private fun inferStatsGroupBy(userInput: String, window: String): String {
        return when {
            merchantHints.any { hint -> userInput.contains(hint) } -> "merchant"
            timeSlotHints.any { hint -> userInput.contains(hint) } -> "timeslot"
            trendHints.any { hint -> userInput.contains(hint) } && (window == "last12months" || userInput.contains("年")) -> "month"
            trendHints.any { hint -> userInput.contains(hint) } -> "day"
            else -> "category"
        }
    }

    private fun inferStatsMetric(userInput: String): String {
        return when {
            frequencyHints.any { hint -> userInput.contains(hint) } -> "frequency"
            ratioHints.any { hint -> userInput.contains(hint) } -> "category_ratio"
            else -> "total_amount"
        }
    }

    private fun parseRecentDayCount(userInput: String): Int? {
        val digit = Regex("(?:最近|过去)\\s*(\\d{1,3})\\s*天").find(userInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (digit != null) return digit

        val chinese = Regex("(?:最近|过去)\\s*([一二两三四五六七八九十]+)\\s*天").find(userInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseChineseNumber)
        return chinese
    }

    private fun parseTopNHint(userInput: String): Int? {
        val topDigit = Regex("(?i)top\\s*(\\d{1,2})").find(userInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (topDigit != null) return topDigit

        val frontDigit = Regex("前\\s*(\\d{1,2})").find(userInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.toIntOrNull()
        if (frontDigit != null) return frontDigit

        val frontChinese = Regex("前([一二两三四五六七八九十])").find(userInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.let(::parseChineseNumber)
        return frontChinese
    }

    private fun parseChineseNumber(raw: String): Int? {
        return countNormalizer.parseStandaloneNumber(raw)
    }

    private fun parseAmountBound(userInput: String, patterns: List<Regex>): Double? {
        patterns.forEach { regex ->
            val value = regex.find(userInput)?.groupValues?.getOrNull(1)?.toDoubleOrNull()
            if (value != null) return value
        }
        return null
    }

    private fun extractQueryKeyword(userInput: String): String? {
        val explicit = Regex("关键词(?:是|为)?[:：]?\\s*([\\u4E00-\\u9FFFA-Za-z0-9]{2,16})")
            .find(userInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!explicit.isNullOrBlank()) return explicit

        val aboutMatch = Regex("关于([\\u4E00-\\u9FFFA-Za-z0-9]{2,16})").find(userInput)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        if (!aboutMatch.isNullOrBlank()) return aboutMatch

        return queryKeywordHints.firstOrNull { hint -> userInput.contains(hint) }
    }

    private fun buildQueryAssistantReply(
        userInput: String,
        args: AgentToolArgs.QueryTransactionsArgs,
        result: QueryToolCallResult<QueryTransactionsResult>,
    ): String {
        if (result.status == AgentToolStatus.FAILURE) {
            val reason = result.validationIssues.firstOrNull()?.message ?: "这次查询条件不完整。"
            return styleFormatter.formatQuery(
                structuredFacts = "我这次没完全查出来。",
                explainabilityLine = reason,
                caringLine = "你可以换个说法，比如“查最近一笔”或“查本周餐饮”。",
            )
        }

        val payload = result.result ?: return "我查了一下，暂时没有可展示的账单数据。"
        if (payload.items.isEmpty()) {
            return styleFormatter.formatQuery(
                structuredFacts = "这个时间段我还没找到匹配的账单。",
                explainabilityLine = "你可以换个关键词或时间范围再试试。",
                caringLine = "比如“查最近一周打车”会更容易命中。",
            )
        }

        val first = payload.items.first()
        val summary = when {
            args.limit == 1 && args.sortKey == "record_time_desc" -> {
                "我帮你找到最近一笔：${first.categoryName}${formatReceiptAmount(first.amount)}（${first.remark}）。"
            }

            args.sortKey == "amount_desc" -> {
                "这段时间花得最多的一笔是：${first.categoryName}${formatReceiptAmount(first.amount)}（${first.remark}）。"
            }

            !args.filters.keyword.isNullOrBlank() -> {
                "关于“${args.filters.keyword}”，我找到 ${payload.items.size} 笔，最靠前的是 ${first.categoryName}${formatReceiptAmount(first.amount)}（${first.remark}）。"
            }

            else -> {
                "我找到 ${payload.items.size} 笔相关账单，最靠前的是 ${first.categoryName}${formatReceiptAmount(first.amount)}（${first.remark}）。"
            }
        }

        val caringLine = if (userInput.contains("多少钱") || userInput.contains("多少")) {
            "要不要我再帮你按类别算一版总额，看看钱主要花在哪？"
        } else {
            "如果你愿意，我还能继续帮你把这段时间的重点变化拎出来。"
        }
        return styleFormatter.formatQuery(
            structuredFacts = summary,
            explainabilityLine = "",
            caringLine = caringLine,
        )
    }

    private fun buildStatsAssistantReply(
        userInput: String,
        args: AgentToolArgs.QuerySpendingStatsArgs,
        result: QueryToolCallResult<QuerySpendingStatsResult>,
    ): String {
        if (result.status == AgentToolStatus.FAILURE) {
            val reason = result.validationIssues.firstOrNull()?.message ?: "这次统计条件还不够完整。"
            return styleFormatter.formatStats(
                structuredFacts = "我这次没能顺利算出来。",
                explainabilityLine = reason,
                caringLine = "你可以直接说“统计过去一周花了多少钱”，我会按这个口径给你算。",
            )
        }

        val payload = result.result ?: return "我先试着统计了一下，但当前还没有可展示的数据。"
        if (payload.buckets.isEmpty()) {
            return styleFormatter.formatStats(
                structuredFacts = "这个范围内我还没有统计到可用数据。",
                explainabilityLine = "你可以换个时间窗口再试试。",
                caringLine = "比如“最近一个月花了多少钱”。",
            )
        }

        val top = payload.buckets.first()
        val totalAmount = payload.buckets.sumOf { it.value }
        val windowLabel = resolveFriendlyWindowLabel(args.window)
        val highlightedTopKey = "**${top.key}**"

        if (isTotalSpendQuestion(userInput)) {
            val summary = "${windowLabel}你总共花了 ${formatReceiptAmount(totalAmount)}。"
            val detail = "其中 ${highlightedTopKey} 最多，大约 ${formatReceiptAmount(top.value)}。"
            return styleFormatter.formatStats(
                structuredFacts = summary,
                explainabilityLine = detail,
                caringLine = "要不要我再按天拆开，看看哪几天花得更集中？",
            )
        }

        val summary = when {
            args.groupBy == "merchant" && args.metric == "frequency" -> {
                "${windowLabel}出现最频繁的是 ${highlightedTopKey}，一共 ${top.value.toInt()} 次。"
            }

            args.groupBy == "timeslot" && args.metric == "frequency" -> {
                "${windowLabel}消费最集中的时段是 ${highlightedTopKey}，出现 ${top.value.toInt()} 次。"
            }

            args.metric == "category_ratio" -> {
                val percent = String.format(Locale.CHINA, "%.1f%%", top.value * 100.0)
                "${windowLabel}占比最高的是 ${highlightedTopKey}，约 $percent。"
            }

            args.metric == "frequency" -> {
                "${windowLabel}出现最频繁的是 ${highlightedTopKey}，共 ${top.value.toInt()} 次。"
            }

            else -> {
                "${windowLabel}花得最多的是 ${highlightedTopKey}，金额约 ${formatReceiptAmount(top.value)}。"
            }
        }

        return styleFormatter.formatStats(
            structuredFacts = summary,
            explainabilityLine = "",
            caringLine = "我也可以继续把前几名一起列给你，方便你对比。",
        )
    }

    private fun buildInsightNoteTag(
        toolName: AgentToolName,
        resultJson: String,
    ): String? {
        val resultObject = runCatching { JSONObject(resultJson) }.getOrNull() ?: return null
        val payload = JSONObject().apply {
            put("toolName", toolName.name.lowercase(Locale.ROOT))
            put("result", resultObject)
        }
        return "<NOTE>$payload</NOTE>"
    }

    private suspend fun applyHumanizedLocalDelay(userInput: String) {
        if (!isLikelyAndroidRuntime()) return

        val minDelay = localReplyMinDelayMs
        val maxDelay = localReplyMaxDelayMs
        if (minDelay <= 0L || maxDelay <= 0L) return

        val safeMin = minOf(minDelay, maxDelay)
        val safeMax = maxOf(minDelay, maxDelay)
        val span = safeMax - safeMin
        val seed = abs(userInput.hashCode().toLong())
        val delayMs = safeMin + if (span == 0L) 0L else seed % (span + 1L)
        delay(delayMs)
    }

    private fun isLikelyAndroidRuntime(): Boolean {
        val vmName = System.getProperty("java.vm.name").orEmpty()
        return vmName.contains("Dalvik", ignoreCase = true) || vmName.contains("ART", ignoreCase = true)
    }

    private fun isTotalSpendQuestion(input: String): Boolean {
        val normalized = input.trim()
        return normalized.contains("花了多少钱") ||
            normalized.contains("总共花了多少") ||
            normalized.contains("总花费") ||
            normalized.contains("总支出")
    }

    private fun hasSemanticSpendQuerySignal(input: String): Boolean {
        val normalized = input.trim()
        if (normalized.isBlank()) return false

        val hasWindowHint = queryWindowRelaxedHints.any { hint -> normalized.contains(hint) }
        val hasSpendVerb = spendVerbQueryHints.any { hint -> normalized.contains(hint) }
        val hasAmountAsk = normalized.contains("多少") || spendQuestionHints.any { hint -> normalized.contains(hint) }
        return isTotalSpendQuestion(normalized) || (hasWindowHint && hasSpendVerb && hasAmountAsk)
    }

    private fun resolveFriendlyWindowLabel(window: String): String {
        return when (window) {
            "today" -> "今天"
            "yesterday" -> "昨天"
            "last7days" -> "过去一周"
            "last30days" -> "最近一个月"
            "last12months" -> "过去一年"
            else -> "这个时间段"
        }
    }

    private fun AgentToolArgs.QueryTransactionsArgs.toQueryArgsJson(): String {
        return JSONObject().apply {
            put("window", window)
            put("sortKey", sortKey)
            put("limit", limit)
            put("startAtMillis", startAtMillis)
            put("endAtMillis", endAtMillis)
            put(
                "filters",
                JSONObject().apply {
                    put("transactionId", filters.transactionId)
                    put("dateKeyword", filters.dateKeyword)
                    put("keyword", filters.keyword)
                    put("amountMin", filters.amountMin)
                    put("amountMax", filters.amountMax)
                },
            )
        }.toString()
    }

    private fun AgentToolArgs.QuerySpendingStatsArgs.toStatsArgsJson(): String {
        return JSONObject().apply {
            put("window", window)
            put("groupBy", groupBy)
            put("metric", metric)
            put("sortKey", sortKey)
            put("topN", topN)
            put("startAtMillis", startAtMillis)
            put("endAtMillis", endAtMillis)
        }.toString()
    }

    private fun startOfDayTimestamp(timestamp: Long): Long {
        return Calendar.getInstance().apply {
            timeInMillis = timestamp
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun endOfDayTimestamp(timestamp: Long): Long {
        return startOfDayTimestamp(timestamp) + oneDayMillis - 1L
    }

    private fun buildIdempotencyKey(userInput: String, drafts: List<AiReceiptDraft>): String {
        val draftSignature = drafts.joinToString(separator = "|") { draft ->
            listOf(
                draft.action,
                draft.amount?.toString().orEmpty(),
                draft.category.orEmpty(),
                draft.desc.orEmpty(),
                draft.recordTime.orEmpty(),
                draft.date.orEmpty(),
                draft.transactionId?.toString().orEmpty(),
            ).joinToString(separator = "#")
        }
        return (userInput.trim() + "::" + draftSignature).hashCode().toUInt().toString(16)
    }

    private fun buildToolSuccessJson(applied: AppliedTransaction): String {
        return "{\"transactionId\":${applied.transactionId},\"action\":\"${applied.action}\",\"type\":${applied.type}}"
    }

    private fun buildDeletePlanCacheKey(draft: AiReceiptDraft, userInput: String): String {
        return listOf(
            draft.transactionId?.toString().orEmpty(),
            draft.category.orEmpty(),
            draft.desc.orEmpty(),
            draft.amount?.toString().orEmpty(),
            draft.date.orEmpty(),
            draft.recordTime.orEmpty(),
            userInput,
        ).joinToString(separator = "|")
    }

    private suspend fun buildDeletePlan(
        draft: AiReceiptDraft,
        userInput: String,
    ): DeletePlan {
        val targets = resolveDeleteTargets(draft, userInput)
        val explicitTransactionId = draft.transactionId?.takeIf { it > 0L }
        val hasExplicitSingleIdTarget = explicitTransactionId != null &&
            targets.size == 1 &&
            targets.first().id == explicitTransactionId
        val requiresConfirmation = !hasExplicitSingleIdTarget
        val isConfirmed = isDeleteConfirmationInput(userInput)
        val previewMessage = buildDeletePreviewMessage(
            targets = targets,
            requiresConfirmation = requiresConfirmation,
            isConfirmed = isConfirmed,
        )

        return DeletePlan(
            targets = targets,
            requiresConfirmation = requiresConfirmation,
            isConfirmed = isConfirmed,
            previewMessage = previewMessage,
        )
    }

    private suspend fun resolveDeleteTargets(
        draft: AiReceiptDraft,
        userInput: String,
    ): List<TransactionEntity> {
        draft.transactionId?.takeIf { it > 0L }?.let { id ->
            return listOfNotNull(transactionDao.getTransactionById(id))
        }

        val recent = transactionDao.getRecentTransactions(limit = 200)
        if (recent.isEmpty()) return emptyList()

        val now = Calendar.getInstance()
        val parsedDate = parseDateFromText(userInput, now)
            ?: parseDateFromText(draft.date.orEmpty(), now)
        val categoryHint = draft.category?.takeIf { it.isNotBlank() }
            ?: inferCategoryFromText(userInput, type = 0)
        val amountHint = draft.amount?.let(::abs)
        val keywordHint = draft.desc
            ?.trim()
            ?.takeIf { it.length >= 2 }
            ?.takeUnless { hint ->
                deleteGenericKeywordHints.any { generic -> hint.contains(generic) }
            }
        val countHint = parseDeleteCountHint(userInput)

        val hasAnyFilter = parsedDate != null || !categoryHint.isNullOrBlank() || amountHint != null || !keywordHint.isNullOrBlank()

        var candidates = recent
        parsedDate?.let { date ->
            candidates = candidates.filter { tx -> isSameDate(tx.recordTimestamp, date) }
        }
        if (!categoryHint.isNullOrBlank()) {
            candidates = candidates.filter { tx ->
                tx.categoryName.contains(categoryHint, ignoreCase = true)
            }
        }
        amountHint?.let { expectedAmount ->
            candidates = candidates.filter { tx -> abs(tx.amount - expectedAmount) < 0.01 }
        }
        if (!keywordHint.isNullOrBlank()) {
            candidates = candidates.filter { tx ->
                tx.remark.contains(keywordHint, ignoreCase = true) ||
                    tx.categoryName.contains(keywordHint, ignoreCase = true)
            }
        }

        if (!hasAnyFilter) {
            val defaultCount = countHint?.coerceAtLeast(1) ?: 1
            return recent.take(defaultCount)
        }

        if (countHint != null && countHint > 0) {
            candidates = candidates.take(countHint)
        }

        return candidates
    }

    private fun buildDeletePreviewMessage(
        targets: List<TransactionEntity>,
        requiresConfirmation: Boolean,
        isConfirmed: Boolean,
    ): String {
        if (targets.isEmpty()) return "预览未命中可删除记录。"

        val head = targets.take(3).joinToString(separator = "、") { tx ->
            "${tx.categoryName}${formatReceiptAmount(tx.amount)}"
        }
        val suffix = if (targets.size > 3) "等${targets.size}笔" else "共${targets.size}笔"
        val base = "预览：将删除${targets.size}笔记录（${head}，${suffix}）。"

        return if (requiresConfirmation && !isConfirmed) {
            "$base 请回复“确认删除”继续。"
        } else {
            base
        }
    }

    private fun buildDeletePreviewJson(plan: DeletePlan): String {
        val targetsJson = JSONArray().apply {
            plan.targets.forEach { tx ->
                put(
                    JSONObject().apply {
                        put("transactionId", tx.id)
                        put("type", tx.type)
                        put("category", tx.categoryName)
                        put("amount", tx.amount)
                        put("recordTimestamp", tx.recordTimestamp)
                    },
                )
            }
        }

        return JSONObject().apply {
            put("targetCount", plan.targets.size)
            put("requiresConfirmation", plan.requiresConfirmation)
            put("isConfirmed", plan.isConfirmed)
            put("previewMessage", plan.previewMessage)
            put("targets", targetsJson)
        }.toString()
    }

    private fun buildDeleteToolSuccessJson(plan: DeletePlan): String {
        val deletedItems = JSONArray().apply {
            plan.targets.forEach { tx ->
                put(
                    JSONObject().apply {
                        put("transactionId", tx.id)
                        put("type", tx.type)
                        put("category", tx.categoryName)
                        put("amount", tx.amount)
                    },
                )
            }
        }

        return JSONObject().apply {
            put("status", "success")
            put("deletedCount", plan.targets.size)
            put("deletedItems", deletedItems)
            put("previewMessage", plan.previewMessage)
        }.toString()
    }

    private fun isDeleteConfirmationInput(userInput: String): Boolean {
        val normalized = userInput.lowercase(Locale.ROOT)
        return deleteConfirmHints.any { hint -> normalized.contains(hint) }
    }

    private fun parseDeleteCountHint(text: String): Int? {
        return countNormalizer.parseCountHint(text, maxValue = 50)
    }

    private fun PreviewActionItem.toDeleteDraft(): AiReceiptDraft {
        return AiReceiptDraft(
            isReceipt = true,
            action = action.ifBlank { ACTION_DELETE },
            amount = amount,
            category = category,
            desc = desc,
            recordTime = recordTime,
            date = date,
            transactionId = transactionId,
        )
    }

    private suspend fun tryCreateTransaction(
        draft: AiReceiptDraft,
        assistantText: String,
        userInput: String,
        rawAmount: Double,
    ): AppliedTransaction? {
        val amount = abs(rawAmount)
        if (amount <= 0.0) return null

        val type = resolveTransactionType(draft, rawAmount)
        val category = normalizeCategory(draft.category, type)
        val remark = draft.desc?.takeIf { it.isNotBlank() }
            ?: assistantText.take(24)

        val recordTimestamp = resolveRecordTimestamp(
            rawRecordTime = draft.recordTime,
            rawDate = draft.date,
            userInput = userInput,
        )
        val now = System.currentTimeMillis()

        val transactionId = transactionDao.insertTransaction(
            TransactionEntity(
                type = type,
                amount = amount,
                categoryName = category,
                categoryIcon = "",
                remark = remark,
                recordTimestamp = recordTimestamp,
                createdTimestamp = now,
            ),
        )

        return AppliedTransaction(
            transactionId = transactionId,
            type = type,
            action = ACTION_CREATE,
            draft = draft,
        )
    }

    private suspend fun tryUpdateTransaction(
        draft: AiReceiptDraft,
        assistantText: String,
        userInput: String,
        rawAmount: Double?,
    ): AppliedTransaction? {
        val target = resolveUpdateTargetTransaction(draft, userInput) ?: return null

        val amount = rawAmount?.let(::abs) ?: target.amount
        if (amount <= 0.0) return null

        val parsedType = rawAmount?.let { resolveTransactionType(draft, it) }
        val hasTypeCue = rawAmount?.let { hasTypeCue(userInput, draft, it) } == true
        val explicitTypeHint = inferTypeHint(userInput, draft)
        val type = when {
            hasTypeCue && parsedType != null -> parsedType
            explicitTypeHint != null -> explicitTypeHint
            else -> target.type
        }

        val category = when {
            !draft.category.isNullOrBlank() -> normalizeCategory(draft.category, type)
            else -> target.categoryName
        }

        val updatedRecordTimestamp = resolveRecordTimestampForUpdate(
            draft = draft,
            userInput = userInput,
            originalRecordTimestamp = target.recordTimestamp,
        )

        val remark = draft.desc?.takeIf { it.isNotBlank() }
            ?: target.remark.ifBlank { assistantText.take(24) }

        transactionDao.updateTransactionById(
            id = target.id,
            type = type,
            amount = amount,
            categoryName = category,
            categoryIcon = target.categoryIcon,
            remark = remark,
            recordTimestamp = updatedRecordTimestamp,
        )

        return AppliedTransaction(
            transactionId = target.id,
            type = type,
            action = ACTION_UPDATE,
            draft = draft,
        )
    }

    private suspend fun resolveUpdateTargetTransaction(
        draft: AiReceiptDraft,
        userInput: String,
    ): TransactionEntity? {
        draft.transactionId?.takeIf { it > 0L }?.let { id ->
            transactionDao.getTransactionById(id)?.let { return it }
        }

        val recent = transactionDao.getRecentTransactions(limit = 120)
        if (recent.isEmpty()) return null

        if (recentTargetHints.any { hint -> userInput.contains(hint) }) {
            return recent.firstOrNull()
        }

        val now = Calendar.getInstance()
        val parsedDate = parseDateFromText(userInput, now)
        val parsedTime = parseTimeFromText(userInput)
        val typeHint = inferTypeHint(userInput, draft)
        val categoryHint = when {
            !draft.category.isNullOrBlank() -> normalizeCategory(draft.category, typeHint ?: 0)
            else -> inferCategoryFromText(userInput, typeHint ?: 0)
        }

        val scored = recent.map { tx ->
            tx to scoreUpdateTarget(
                transaction = tx,
                parsedDate = parsedDate,
                parsedTime = parsedTime,
                categoryHint = categoryHint,
                typeHint = typeHint,
            )
        }

        val best = scored.maxWithOrNull(
            compareBy<Pair<TransactionEntity, Int>> { it.second }
                .thenByDescending { it.first.recordTimestamp },
        ) ?: return null

        val hasAnyCue = parsedDate != null || parsedTime != null || categoryHint != null || typeHint != null
        return if (!hasAnyCue || best.second > 0) best.first else null
    }

    private fun scoreUpdateTarget(
        transaction: TransactionEntity,
        parsedDate: ParsedDate?,
        parsedTime: ParsedTime?,
        categoryHint: String?,
        typeHint: Int?,
    ): Int {
        var score = 0

        if (parsedDate != null) {
            if (isSameDate(transaction.recordTimestamp, parsedDate)) score += 6
        } else {
            score += 1
        }

        if (!categoryHint.isNullOrBlank()) {
            if (transaction.categoryName == categoryHint) score += 5
        }

        if (typeHint != null && transaction.type == typeHint) {
            score += 2
        }

        if (parsedTime != null) {
            val txCal = Calendar.getInstance().apply { timeInMillis = transaction.recordTimestamp }
            val txMinutes = txCal.get(Calendar.HOUR_OF_DAY) * 60 + txCal.get(Calendar.MINUTE)
            val targetMinutes = parsedTime.hour * 60 + parsedTime.minute
            val minuteDiff = abs(txMinutes - targetMinutes)
            score += when {
                minuteDiff <= 60 -> 3
                minuteDiff <= 180 -> 1
                else -> 0
            }
        }

        return score
    }

    private fun isSameDate(timestamp: Long, date: ParsedDate): Boolean {
        val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
        return cal.get(Calendar.YEAR) == date.year &&
            cal.get(Calendar.MONTH) + 1 == date.month &&
            cal.get(Calendar.DAY_OF_MONTH) == date.day
    }

    private fun hasTypeCue(
        userInput: String,
        draft: AiReceiptDraft,
        rawAmount: Double,
    ): Boolean {
        if (rawAmount < 0) return true
        if (inferTypeHint(userInput, draft) != null) return true
        val hints = incomeKeywordHints + expenseKeywordHints
        return hints.any { userInput.contains(it) }
    }

    private fun inferTypeHint(
        userInput: String,
        draft: AiReceiptDraft,
    ): Int? {
        val categoryHint = draft.category.orEmpty()
        if (incomeKeywordHints.any { categoryHint.contains(it) || userInput.contains(it) }) return 1
        if (expenseKeywordHints.any { categoryHint.contains(it) || userInput.contains(it) }) return 0
        return null
    }

    private fun inferCategoryFromText(text: String, type: Int): String? {
        val hasCategoryCue = categoryKeywordHints.any { text.contains(it) }
        if (!hasCategoryCue) return null
        val normalized = normalizeCategory(text, type)
        return normalized.takeIf { it.isNotBlank() && it != "其他" }
    }

    private fun resolveRecordTimestampForUpdate(
        draft: AiReceiptDraft,
        userInput: String,
        originalRecordTimestamp: Long,
    ): Long {
        val now = Calendar.getInstance()
        parseDateTimeFromUserInput(userInput, now)?.let { return it }
        parseRecordTimeFromDraft(draft.recordTime, now)?.let { return it }
        parseDateFromDraft(draft.date, now)?.let { return it }
        return if (originalRecordTimestamp > 0L) {
            originalRecordTimestamp
        } else {
            currentTimestamp(now)
        }
    }

    private fun containsDateOrTimeCue(text: String): Boolean {
        if (dateOrTimeRegex.containsMatchIn(text)) return true
        return dateOrTimeKeywordHints.any { text.contains(it) }
    }

    private fun normalizeReceiptAction(
        draft: AiReceiptDraft,
        userInput: String,
    ): String {
        val rawAction = draft.action.trim().lowercase(Locale.ROOT)
        if (rawAction in setOf("delete", "remove", "erase", "drop")) return ACTION_DELETE
        if (rawAction in setOf("update", "modify", "edit", "correct", "fix")) return ACTION_UPDATE
        if (rawAction in setOf("create", "add", "record")) return ACTION_CREATE

        if (deleteIntentHints.any { userInput.contains(it) }) {
            return ACTION_DELETE
        }

        return if (updateIntentHints.any { userInput.contains(it) }) {
            ACTION_UPDATE
        } else {
            ACTION_CREATE
        }
    }

    private fun resolveTransactionType(draft: AiReceiptDraft, rawAmount: Double): Int {
        if (rawAmount < 0) return 0

        val text = listOfNotNull(draft.category, draft.desc).joinToString(" ")
        val incomeHints = listOf("工资", "收入", "奖金", "报销", "兼职", "红包", "收款")
        return if (incomeHints.any { text.contains(it) }) 1 else 0
    }

    private data class ParsedDate(
        val year: Int,
        val month: Int,
        val day: Int,
    )

    private data class ParsedTime(
        val hour: Int,
        val minute: Int,
    )

    private fun resolveRecordTimestamp(
        rawRecordTime: String?,
        rawDate: String?,
        userInput: String,
    ): Long {
        val now = Calendar.getInstance()

        parseDateTimeFromUserInput(userInput, now)?.let { return it }
        parseRecordTimeFromDraft(rawRecordTime, now)?.let { return it }
        parseDateFromDraft(rawDate, now)?.let { return it }

        return currentTimestamp(now)
    }

    private fun currentTimestamp(now: Calendar): Long {
        return buildTimestamp(currentDate(now), currentTime(now)) ?: now.timeInMillis
    }

    private fun currentDate(now: Calendar): ParsedDate {
        return ParsedDate(
            year = now.get(Calendar.YEAR),
            month = now.get(Calendar.MONTH) + 1,
            day = now.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun currentTime(now: Calendar): ParsedTime {
        return ParsedTime(
            hour = now.get(Calendar.HOUR_OF_DAY),
            minute = now.get(Calendar.MINUTE),
        )
    }

    private fun parseDateTimeFromUserInput(text: String, now: Calendar): Long? {
        val input = text.trim()
        if (input.isBlank()) return null

        val parsedDate = parseDateFromText(input, now)
        val parsedTime = parseTimeFromText(input)
        if (parsedDate == null && parsedTime == null) return null

        val datePart = parsedDate ?: ParsedDate(
            year = now.get(Calendar.YEAR),
            month = now.get(Calendar.MONTH) + 1,
            day = now.get(Calendar.DAY_OF_MONTH),
        )
        val timePart = parsedTime ?: ParsedTime(
            hour = now.get(Calendar.HOUR_OF_DAY),
            minute = now.get(Calendar.MINUTE),
        )

        return buildTimestamp(datePart, timePart)
    }

    private fun parseRecordTimeFromDraft(rawRecordTime: String?, now: Calendar): Long? {
        val value = rawRecordTime?.trim().orEmpty()
        if (value.isBlank()) return null

        parseDateTimeByPatterns(value, draftDateTimePatterns)?.let { return it }
        parseDateTimeFromUserInput(value, now)?.let { return it }
        return parseDateFromDraft(value, now)
    }

    private fun parseDateFromDraft(rawDate: String?, now: Calendar): Long? {
        val value = rawDate?.trim().orEmpty()
        if (value.isBlank()) return null

        parseDateTimeByPatterns(value, draftDateTimePatterns)?.let { return it }

        val parsedDate = parseDateByPatterns(value, draftDatePatterns)
            ?: parseDateFromText(value, now)
            ?: return null

        val timePart = ParsedTime(
            hour = now.get(Calendar.HOUR_OF_DAY),
            minute = now.get(Calendar.MINUTE),
        )
        return buildTimestamp(parsedDate, timePart)
    }

    private fun parseDateFromText(text: String, now: Calendar): ParsedDate? {
        temporalResolverV2.resolve(text, now)?.date?.let { resolved ->
            return ParsedDate(
                year = resolved.year,
                month = resolved.month,
                day = resolved.day,
            )
        }

        parseAbsoluteDateWithYear(text)?.let { return it }
        parseAbsoluteDateWithoutYear(text, now.get(Calendar.YEAR))?.let { return it }
        parseRelativeDate(text, now)?.let { return it }
        return null
    }

    private fun parseAbsoluteDateWithYear(text: String): ParsedDate? {
        val regex = Regex("(\\d{4})[-/.年](\\d{1,2})[-/.月](\\d{1,2})日?")
        val match = regex.find(text) ?: return null
        val year = match.groupValues[1].toIntOrNull() ?: return null
        val month = match.groupValues[2].toIntOrNull() ?: return null
        val day = match.groupValues[3].toIntOrNull() ?: return null
        return ParsedDate(year = year, month = month, day = day)
    }

    private fun parseAbsoluteDateWithoutYear(text: String, currentYear: Int): ParsedDate? {
        val regex = Regex("(\\d{1,2})月(\\d{1,2})日?")
        val match = regex.find(text) ?: return null
        val month = match.groupValues[1].toIntOrNull() ?: return null
        val day = match.groupValues[2].toIntOrNull() ?: return null
        return ParsedDate(year = currentYear, month = month, day = day)
    }

    private fun parseRelativeDate(text: String, now: Calendar): ParsedDate? {
        val offsetDays = when {
            text.contains("大前天") -> -3
            text.contains("前天") -> -2
            text.contains("昨天") || text.contains("昨日") || text.contains("昨晚") || text.contains("昨夜") -> -1
            text.contains("今天") || text.contains("今日") || text.contains("今晚") || text.contains("今早") || text.contains("今晨") -> 0
            text.contains("明天") || text.contains("明早") || text.contains("明晚") -> 1
            text.contains("后天") -> 2
            else -> return null
        }

        val resolved = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_YEAR, offsetDays)
        }
        return ParsedDate(
            year = resolved.get(Calendar.YEAR),
            month = resolved.get(Calendar.MONTH) + 1,
            day = resolved.get(Calendar.DAY_OF_MONTH),
        )
    }

    private fun parseTimeFromText(text: String): ParsedTime? {
        temporalResolverV2.resolve(text)?.time?.let { resolved ->
            return ParsedTime(
                hour = resolved.hour,
                minute = resolved.minute,
            )
        }

        val normalized = text.replace("：", ":")

        val colonMatch = Regex("(?<!\\d)(\\d{1,2}):(\\d{1,2})(?!\\d)").find(normalized)
        if (colonMatch != null) {
            val rawHour = colonMatch.groupValues[1].toIntOrNull() ?: return null
            val minute = colonMatch.groupValues[2].toIntOrNull() ?: return null
            if (minute !in 0..59 || rawHour !in 0..23) return null
            val hour = adjustHourByPeriodHint(rawHour, normalized)
            return ParsedTime(hour = hour, minute = minute)
        }

        val cnMatch = Regex("(?<!\\d)(\\d{1,2})\\s*(?:点|时)(?:\\s*(半|一刻|三刻|(\\d{1,2})\\s*分?))?").find(normalized)
        if (cnMatch != null) {
            val rawHour = cnMatch.groupValues[1].toIntOrNull() ?: return null
            if (rawHour !in 0..23) return null

            val minute = when {
                cnMatch.groupValues[2] == "半" -> 30
                cnMatch.groupValues[2] == "一刻" -> 15
                cnMatch.groupValues[2] == "三刻" -> 45
                cnMatch.groupValues[3].isNotBlank() -> cnMatch.groupValues[3].toIntOrNull() ?: return null
                else -> 0
            }
            if (minute !in 0..59) return null

            val hour = adjustHourByPeriodHint(rawHour, normalized)
            return ParsedTime(hour = hour, minute = minute)
        }

        resolveFuzzyTimeByHint(normalized)?.let { return it }

        val hasYesterdayHint = normalized.contains("昨天") || normalized.contains("昨日")
        if (hasYesterdayHint && !hasExplicitClockCue(normalized)) {
            return ParsedTime(hour = 12, minute = 0)
        }

        return null
    }

    private fun adjustHourByPeriodHint(hour: Int, text: String): Int {
        val morningHints = listOf("凌晨", "清晨", "早晨", "早上", "上午", "今早")
        val noonHints = listOf("中午", "午间")
        val afternoonHints = listOf("下午", "午后")
        val eveningHints = listOf("晚上", "傍晚", "夜里", "晚间", "今晚", "昨晚", "昨夜")

        val hasMorningHint = morningHints.any { text.contains(it) }
        val hasNoonHint = noonHints.any { text.contains(it) }
        val hasAfternoonHint = afternoonHints.any { text.contains(it) }
        val hasEveningHint = eveningHints.any { text.contains(it) }

        var normalizedHour = hour
        if (hasNoonHint && normalizedHour in 1..11) {
            normalizedHour = normalizedHour
        }
        if ((hasAfternoonHint || hasEveningHint) && normalizedHour in 1..11) {
            normalizedHour += 12
        }
        if (hasMorningHint && normalizedHour == 12) {
            normalizedHour = 0
        }
        if (hasNoonHint && normalizedHour == 12) {
            normalizedHour = 12
        }
        if (hasEveningHint && normalizedHour == 12) {
            normalizedHour = 0
        }

        return normalizedHour.coerceIn(0, 23)
    }

    private fun resolveFuzzyTimeByHint(text: String): ParsedTime? {
        val morningHints = listOf("早上", "早晨", "早餐", "早饭")
        if (morningHints.any { text.contains(it) }) return ParsedTime(hour = 8, minute = 0)

        val noonHints = listOf("中午", "午餐", "午饭")
        if (noonHints.any { text.contains(it) }) return ParsedTime(hour = 12, minute = 0)

        val afternoonHints = listOf("下午", "下午茶")
        if (afternoonHints.any { text.contains(it) }) return ParsedTime(hour = 15, minute = 0)

        val eveningHints = listOf("晚上", "傍晚", "晚餐", "晚饭", "昨晚", "今晚")
        if (eveningHints.any { text.contains(it) }) return ParsedTime(hour = 19, minute = 0)

        val lateNightHints = listOf("夜宵", "深夜", "半夜")
        if (lateNightHints.any { text.contains(it) }) return ParsedTime(hour = 23, minute = 0)

        return null
    }

    private fun hasExplicitClockCue(text: String): Boolean {
        if (Regex("(?<!\\d)\\d{1,2}:\\d{1,2}(?!\\d)").containsMatchIn(text)) return true
        if (Regex("(?<!\\d)\\d{1,2}\\s*(点|时)").containsMatchIn(text)) return true
        return false
    }

    private fun parseDateTimeByPatterns(value: String, patterns: List<String>): Long? {
        val normalized = value.trim()
        patterns.forEach { pattern ->
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
                sdf.parse(normalized)?.time
            }.getOrNull()
            if (parsed != null) return parsed
        }
        return null
    }

    private fun parseDateByPatterns(value: String, patterns: List<String>): ParsedDate? {
        val normalized = value.trim()
        patterns.forEach { pattern ->
            val parsed = runCatching {
                val sdf = SimpleDateFormat(pattern, Locale.CHINA).apply { isLenient = false }
                sdf.parse(normalized)
            }.getOrNull() ?: return@forEach

            val calendar = Calendar.getInstance().apply { time = parsed }
            return ParsedDate(
                year = calendar.get(Calendar.YEAR),
                month = calendar.get(Calendar.MONTH) + 1,
                day = calendar.get(Calendar.DAY_OF_MONTH),
            )
        }
        return null
    }

    private fun buildTimestamp(date: ParsedDate, time: ParsedTime): Long? {
        if (date.month !in 1..12 || date.day !in 1..31) return null
        if (time.hour !in 0..23 || time.minute !in 0..59) return null

        return runCatching {
            Calendar.getInstance().apply {
                isLenient = false
                set(Calendar.YEAR, date.year)
                set(Calendar.MONTH, date.month - 1)
                set(Calendar.DAY_OF_MONTH, date.day)
                set(Calendar.HOUR_OF_DAY, time.hour)
                set(Calendar.MINUTE, time.minute)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }.timeInMillis
        }.getOrNull()
    }

    private fun normalizeCategory(rawCategory: String?, type: Int): String {
        val input = rawCategory.orEmpty().trim()
        if (input.isBlank()) return if (type == 1) "收入" else "其他"

        val normalized = input
            .replace("类别", "")
            .replace("分类", "")
            .replace("类", "")
            .trim()

        if (normalized in canonicalExpenseCategories) return normalized

        if (type == 1) {
            val incomeHints = listOf("工资", "薪资", "奖金", "报销", "收款", "收入", "兼职")
            if (incomeHints.any { normalized.contains(it) }) return "收入"
        }

        val mapped = when {
            normalized.contains("餐饮") || normalized.contains("美食") ||
                normalized.contains("早餐") || normalized.contains("午餐") || normalized.contains("晚餐") ||
                normalized.contains("午饭") || normalized.contains("中饭") || normalized.contains("晚饭") ||
                normalized.contains("吃饭") || normalized.contains("吃中饭") ||
                normalized.contains("夜宵") || normalized.contains("宵夜") ||
                normalized.contains("饮品") || normalized.contains("奶茶") -> "餐饮美食"

            normalized.contains("交通") || normalized.contains("出行") || normalized.contains("打车") ||
                normalized.contains("地铁") || normalized.contains("公交") || normalized.contains("高铁") -> "交通出行"

            normalized.contains("购物") || normalized.contains("消费") || normalized.contains("网购") ||
                normalized.contains("服饰") || normalized.contains("数码") -> "购物消费"

            normalized.contains("居家") || normalized.contains("家居") || normalized.contains("房租") ||
                normalized.contains("水电") || normalized.contains("日用") -> "居家生活"

            normalized.contains("娱乐") || normalized.contains("休闲") || normalized.contains("电影") ||
                normalized.contains("游戏") || normalized.contains("旅游") -> "娱乐休闲"

            normalized.contains("医疗") || normalized.contains("健康") || normalized.contains("药") ||
                normalized.contains("医院") || normalized.contains("体检") -> "医疗健康"

            normalized.contains("人情") || normalized.contains("交际") || normalized.contains("礼") ||
                normalized.contains("社交") || normalized.contains("红包") -> "人情交际"

            else -> null
        }

        return mapped ?: if (type == 1) "收入" else "其他"
    }

    private fun resolveFallbackContextLimit(
        userInput: String,
        isCorrectionInput: Boolean,
    ): Int {
        if (isCorrectionInput) return 18
        val length = userInput.trim().length
        return when {
            length <= 12 -> 8
            length <= 24 -> 10
            length <= 40 -> 12
            else -> 14
        }
    }

    private fun buildGatewayRequestMessages(
        systemPrompt: String,
        recentMessages: List<ChatMessageEntity>,
    ): List<AiMessage> {
        val normalized = recentMessages.mapNotNull { message ->
            val role = if (message.role == "assistant" || message.role == "ai") "assistant" else "user"
            val content = stripReceiptPayload(message.content).trim()
            if (content.isBlank()) {
                null
            } else {
                AiMessage(role = role, content = content)
            }
        }

        val merged = mutableListOf<AiMessage>()
        normalized.forEach { message ->
            val last = merged.lastOrNull()
            if (last != null && last.role == message.role) {
                merged[merged.lastIndex] = last.copy(content = "${last.content}\n${message.content}")
            } else {
                merged += message
            }
        }

        val cappedTurns = merged.takeLast(12)
        return buildList {
            add(AiMessage(role = "system", content = systemPrompt))
            addAll(cappedTurns)
        }
    }

    private fun buildSystemPrompt(
        aiConfig: AiAssistantConfig,
        userName: String,
    ): String {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date())
        val nowDateTime = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA).format(Date())
        val toneText = when (aiConfig.tone.name) {
            "TSUNDERE" -> "傲娇毒舌"
            "RATIONAL" -> "理智管家"
            else -> "贴心治愈"
        }

        val toneGuide = when (aiConfig.tone) {
            AiTone.HEALING -> "语气温柔、带一点关心和鼓励，像会倾听的朋友。"
            AiTone.TSUNDERE -> "语气嘴硬心软，允许轻微吐槽，但不能攻击用户，不使用脏话。"
            AiTone.RATIONAL -> "语气专业、简洁、逻辑清晰，给出明确建议。"
        }

        return """
            你是一个名为${aiConfig.name}的AI记账管家，性格是${toneText}。用户的名字是${userName}。
                        今天日期是${today}，当前系统准确时间是${nowDateTime}。
            ${toneGuide}
                        你的任务是陪用户聊天，并在用户提到收支时完成记账动作。

                        【AI 可执行动作】
                        1. create：新增一条收支记录。
                        2. update：修改一条已有记录（金额/分类/时间/备注）。

                        【AI_Humanized 交互规则】
                        - 最小打扰：只有关键信息缺失时才追问，每次最多1个问题。
                        - 一次说清优先：信息足够时直接执行，不要反复确认。
                        - 可解释建议：建议场景只给1个事实+1个可执行动作。
                        - 纠错低成本：用户说“记错了/改成/不对/修改”时，优先理解为 update，而不是 create。
                        - 不要给用户起外号，不要臆造账单数据。
                        - 如果没有可用账单数据或工具结果，必须明确说明“当前无法直接查看到完整账本数据”，并引导用户补充条件；禁止编造金额、分类、条数。

                        普通聊天时，尽量像真人发消息，可以分成2到4句短消息，每句不超过40字，避免长篇大论。
            如果要连发多条独立消息，请使用双换行分隔（\n\n），不要用单换行硬拆句。
                        当识别到可执行记账信息时，先输出2到3句简短确认语气（分句自然），最后再输出一句执行确认，然后再附上 <DATA> JSON。

                        【修改动作识别规则】
                        - 用户包含“记错了、改成、改为、不对、修改、更正、把...改成”时，action 必须为 update。
                        - 示例1：“刚刚记错了，是15块钱” => action=update，amount=15。
                        - 示例2：“把昨天中午午餐的金额改成10块” => action=update，amount=10，category=餐饮美食，date/recordTime 按语义推算。
                        - 如果用户想修改但关键字段缺失（比如没说改成多少），只问一个问题，不要输出 <DATA>。

                        【时间感知规则】当前系统准确时间是：${nowDateTime}。在提取记账记录时：
                        1. 如果用户明确说明了时间（如“早上8点”“昨晚10点”），请结合当前时间推算出准确的 yyyy-MM-dd HH:mm，并写入 recordTime。
                        2. 如果用户提到模糊时段，按常识映射：早上8:00、中午12:00、下午15:00、晚上19:00、深夜23:00。
                        3. 如果用户没有提及具体时间（如“我刚买了一瓶水”），请直接使用上述系统当前的准确时间作为 recordTime。
                        4. 如果用户提到相对日期（如昨天/前天/大前天/今天/明天），你必须基于“今天日期”换算 date。
                        5. 如果用户没有明确提到具体日期，date 字段必须填写今天（${today}）。
                        6. date 必须与 recordTime 的日期部分保持一致。

            如果用户的话包含记账信息，你必须在回复最末尾严格输出 <DATA>...</DATA> JSON：
            {
              "isReceipt": true,
              "action": "create",
              "amount": 30.0,
              "category": "交通",
              "desc": "打车",
                            "recordTime": "${nowDateTime}",
                            "date": "${today}"
            }
                        注意：recordTime 字段必须始终存在，格式固定为 yyyy-MM-dd HH:mm。
            如果只是普通闲聊，不要输出 <DATA> 标签。
        """.trimIndent()
    }

    private fun resolveTemperature(tone: AiTone): Double {
        return when (tone) {
            AiTone.HEALING -> 0.55
            AiTone.TSUNDERE -> 0.70
            AiTone.RATIONAL -> 0.25
        }
    }

    private fun splitAssistantReply(text: String): List<String> {
        val normalized = text
            .replace("\r", "")
            .trim()

        if (normalized.isBlank()) return listOf("收到，我在。")

        val explicitParts = normalized
            .split(conversationDelimiterRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }

        // Keep a single continuous bubble unless caller explicitly separates paragraphs/messages.
        if (explicitParts.size <= 1) {
            return listOf(normalized)
        }

        val sourceParts = explicitParts
        val splitParts = buildList {
            sourceParts.forEach { source ->
                addAll(splitByMajorSentences(source))
            }
        }

        return splitParts
            .ifEmpty { listOf(normalized) }
            .take(3)
    }

    private fun splitByMajorSentences(text: String, maxLen: Int = 42): List<String> {
        val sentenceParts = text
            .split(majorSentenceRegex)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .ifEmpty { listOf(text.trim()) }

        if (sentenceParts.size == 1 && sentenceParts.first().length <= maxLen) {
            return sentenceParts
        }

        val merged = mutableListOf<String>()
        var buffer = ""
        sentenceParts.forEach { sentence ->
            if (buffer.isBlank()) {
                buffer = sentence
            } else if (buffer.length + sentence.length <= maxLen) {
                buffer += sentence
            } else {
                merged += buffer
                buffer = sentence
            }
        }

        if (buffer.isNotBlank()) {
            merged += buffer
        }

        return merged
    }

    private fun ensureReceiptConversationChunks(
        chunks: List<String>,
        draft: AiReceiptDraft?,
    ): List<String> {
        val normalized = chunks
            .map { it.trim() }
            .filter { it.isNotBlank() }

        val isHealthRelated = draft?.category.orEmpty().let { category ->
            category.contains("医疗") || category.contains("健康") || category.contains("药")
        }

        val opening = if (isHealthRelated) "怎么啦？" else "收到啦。"
        val caring = if (isHealthRelated) {
            "是什么方面的药噢，要记得按时吃。"
        } else {
            "我来帮你把这笔账整理好。"
        }
        val ending = if (isHealthRelated) {
            "已经记好了，希望快点好起来。"
        } else {
            "已经记好了。"
        }

        return when {
            normalized.size >= 3 -> normalized.take(3)
            normalized.size == 2 -> listOf(normalized[0], caring, normalized[1])
            normalized.size == 1 -> listOf(opening, normalized[0], ending)
            else -> listOf(opening, caring, ending)
        }
    }

    private fun resolveReceiptDrafts(
        parsedReceipts: List<AiReceiptDraft>,
        fallbackReceipts: List<AiReceiptDraft>,
    ): List<AiReceiptDraft> {
        val preferredFallback = fallbackReceipts.takeIf { it.size >= parsedReceipts.size && it.isNotEmpty() }
        return preferredFallback ?: parsedReceipts
    }

    private fun extractReceiptDraftsFromText(text: String): List<AiReceiptDraft> {
        val tagPayloads = findPayloads(text, "DATA") + findPayloads(text, "RECEIPT")
        val markdownPayloads = if (tagPayloads.isEmpty()) {
            findMarkdownReceiptPayloads(text)
        } else {
            emptyList()
        }
        val payloads = tagPayloads + markdownPayloads
        if (payloads.isEmpty()) return emptyList()
        return payloads.flatMap(::parseReceiptPayloadDrafts)
    }

    private fun sanitizeAssistantVisibleText(text: String): String {
        val stripped = stripReceiptPayload(text)
        val trimmedTagFragment = stripTrailingPayloadTagFragment(stripped)
        val trimmedFenceFragment = stripTrailingMarkdownFenceFragment(trimmedTagFragment)
        val withoutCodeBlocks = trimmedFenceFragment
            .replace(markdownAnyCodeBlockRegex, "")
            .replace(unclosedMarkdownAnyCodeBlockRegex, "")
        val withoutJsonSegments = stripLikelyJsonSegments(withoutCodeBlocks)
        val withoutTrailingJson = stripTrailingJsonFragment(withoutJsonSegments)
        return withoutTrailingJson
            .replace(jsonKeyLineRegex, "")
            .replace(orphanJsonPunctuationLineRegex, "")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
    }

    private fun stripLikelyJsonSegments(text: String): String {
        if (text.isBlank()) return text

        val ranges = mutableListOf<IntRange>()
        var cursor = 0
        while (cursor < text.length) {
            val current = text[cursor]
            if (current == '{' || current == '[') {
                val end = findBalancedJsonSegmentEnd(text, cursor)
                if (end > cursor) {
                    val candidate = text.substring(cursor, end + 1)
                    if (looksLikeJsonSegment(candidate)) {
                        ranges += cursor..end
                        cursor = end + 1
                        continue
                    }
                }
            }
            cursor += 1
        }

        if (ranges.isEmpty()) return text

        val builder = StringBuilder()
        var start = 0
        ranges.forEach { range ->
            if (range.first > start) {
                builder.append(text.substring(start, range.first))
            }
            start = range.last + 1
        }
        if (start < text.length) {
            builder.append(text.substring(start))
        }
        return builder.toString()
    }

    private fun stripTrailingJsonFragment(text: String): String {
        val lastObjectStart = text.lastIndexOf('{')
        val lastArrayStart = text.lastIndexOf('[')
        val start = maxOf(lastObjectStart, lastArrayStart)
        if (start < 0) return text

        if (findBalancedJsonSegmentEnd(text, start) >= start) {
            return text
        }

        val fragment = text.substring(start)
        return if (looksLikeJsonSegment(fragment) || jsonQuotedKeyRegex.containsMatchIn(fragment)) {
            text.substring(0, start).trimEnd()
        } else {
            text
        }
    }

    private fun findBalancedJsonSegmentEnd(text: String, startIndex: Int): Int {
        val stack = ArrayDeque<Char>()
        var inString = false
        var escaped = false

        for (index in startIndex until text.length) {
            val char = text[index]

            if (inString) {
                when {
                    escaped -> escaped = false
                    char == '\\' -> escaped = true
                    char == '"' -> inString = false
                }
                continue
            }

            when (char) {
                '"' -> inString = true
                '{', '[' -> stack.addLast(char)
                '}', ']' -> {
                    if (stack.isEmpty()) return -1
                    val open = stack.removeLast()
                    if (!isMatchingJsonBracket(open, char)) return -1
                    if (stack.isEmpty()) return index
                }
            }
        }

        return -1
    }

    private fun isMatchingJsonBracket(open: Char, close: Char): Boolean {
        return (open == '{' && close == '}') || (open == '[' && close == ']')
    }

    private fun looksLikeJsonSegment(segment: String): Boolean {
        val trimmed = segment.trim()
        if (trimmed.length < 2) return false

        val jsonShape = (trimmed.startsWith('{') && trimmed.endsWith('}')) ||
            (trimmed.startsWith('[') && trimmed.endsWith(']'))
        if (!jsonShape) return false

        val strictParsed = if (trimmed.startsWith('{')) {
            runCatching { JSONObject(trimmed) }.isSuccess
        } else {
            runCatching { JSONArray(trimmed) }.isSuccess
        }
        if (strictParsed) return true

        return jsonQuotedKeyRegex.containsMatchIn(trimmed) || jsonLooseKeyRegex.containsMatchIn(trimmed)
    }

    private fun stripTrailingPayloadTagFragment(text: String): String {
        val lastOpenBracket = text.lastIndexOf('<')
        if (lastOpenBracket < 0) return text

        val tail = text.substring(lastOpenBracket)
        if (tail.contains('>')) return text

        val lowerTail = tail.lowercase(Locale.ROOT)
        val hasPayloadPrefix = payloadTagFragmentPrefixes.any { prefix -> lowerTail.startsWith(prefix) }
        return if (hasPayloadPrefix) {
            text.substring(0, lastOpenBracket).trimEnd()
        } else {
            text
        }
    }

    private fun stripTrailingMarkdownFenceFragment(text: String): String {
        val fence = "```"
        val lastFenceIndex = text.lastIndexOf(fence)
        if (lastFenceIndex < 0) return text

        val fenceCount = fenceRegex.findAll(text).count()
        if (fenceCount % 2 == 0) return text
        return text.substring(0, lastFenceIndex).trimEnd()
    }

    private fun findMarkdownReceiptPayloads(text: String): List<String> {
        val payloads = mutableListOf<String>()

        markdownJsonCodeBlockRegex.findAll(text).forEach { match ->
            val rawPayload = match.groupValues.getOrNull(1).orEmpty()
            val normalized = normalizePotentialJsonPayload(rawPayload)
            if (looksLikeReceiptJson(normalized)) {
                payloads += normalized
            }
        }

        markdownInlineReceiptJsonRegex.findAll(text).forEach { match ->
            val normalized = normalizePotentialJsonPayload(match.value)
            if (looksLikeReceiptJson(normalized)) {
                payloads += normalized
            }
        }

        return payloads.distinct()
    }

    private fun normalizePotentialJsonPayload(raw: String): String {
        val trimmed = raw.trim()
        return if (trimmed.startsWith("json", ignoreCase = true)) {
            trimmed.removePrefix("json").removePrefix("JSON").trim()
        } else {
            trimmed
        }
    }

    private fun looksLikeReceiptJson(payload: String): Boolean {
        val normalized = payload.lowercase(Locale.ROOT)
        return normalized.contains("\"action\"") ||
            normalized.contains("\"isreceipt\"") ||
            normalized.contains("\"amount\"") ||
            normalized.contains("\"items\"") ||
            normalized.contains("\"successcount\"")
    }

    private fun stripReceiptPayload(text: String): String {
        return text
            .replace(dataPayloadRegex, "")
            .replace(receiptPayloadRegex, "")
            .replace(notePayloadRegex, "")
            .replace(thinkPayloadRegex, "")
            .replace(markdownJsonCodeBlockRegex, "")
            .replace(markdownInlineReceiptJsonRegex, "")
            .replace(unclosedPayloadStartRegex, "")
            .replace(unclosedMarkdownJsonCodeBlockRegex, "")
            .replace(leadingNullRegex, "")
            .replace(lineNullRegex, "")
            .replace(repeatedNullRegex, "")
            .trim()
    }

    private fun buildReceiptMetaTag(result: ApplyTransactionsResult): String {
        val primary = result.primaryAppliedTransaction
        val payload = JSONObject().apply {
            put("isReceipt", true)
            put("mode", if (result.appliedTransactions.size + result.failedTransactions.size > 1 || result.failedTransactions.isNotEmpty()) "batch" else "single")
            put("successCount", result.appliedTransactions.size)
            put("failureCount", result.failedTransactions.size)
            put("createCount", result.createCount)
            put("updateCount", result.updateCount)
            put("deleteCount", result.deleteCount)
            primary?.let { applied ->
                val typeValue = if (applied.type == 1) "income" else "expense"
                put("action", applied.action.ifBlank { ACTION_CREATE })
                applied.draft.amount?.let { put("amount", abs(it)) }
                applied.draft.category?.let { put("category", it) }
                applied.draft.desc?.let { put("desc", it) }
                applied.draft.recordTime?.let { put("recordTime", it) }
                applied.draft.date?.let { put("date", it) }
                put("type", typeValue)
            }
            put(
                "items",
                JSONArray().apply {
                    result.appliedTransactions.forEach { applied ->
                        put(
                            JSONObject().apply {
                                put("index", applied.index)
                                put("status", "success")
                                put("transactionId", applied.transactionId)
                                put("action", applied.action)
                                put("type", if (applied.type == 1) "income" else "expense")
                                applied.draft.amount?.let { put("amount", abs(it)) }
                                applied.draft.category?.let { put("category", it) }
                                applied.draft.desc?.let { put("desc", it) }
                                applied.draft.recordTime?.let { put("recordTime", it) }
                                applied.draft.date?.let { put("date", it) }
                            },
                        )
                    }
                    result.failedTransactions.forEach { failed ->
                        put(
                            JSONObject().apply {
                                put("index", failed.index)
                                put("status", "failed")
                                put("action", failed.draft.action.ifBlank { ACTION_CREATE })
                                put("reason", failed.reason)
                                failed.draft.amount?.let { put("amount", abs(it)) }
                                failed.draft.category?.let { put("category", it) }
                                failed.draft.desc?.let { put("desc", it) }
                                failed.draft.recordTime?.let { put("recordTime", it) }
                                failed.draft.date?.let { put("date", it) }
                            },
                        )
                    }
                },
            )
            put(
                "errors",
                JSONArray().apply {
                    result.errors.forEach { error ->
                        put(error)
                    }
                },
            )
        }
        return "<RECEIPT>${payload}</RECEIPT>"
    }

    private fun findPayload(text: String, tag: String): String? {
        return findPayloads(text, tag).firstOrNull()
    }

    private fun findPayloads(text: String, tag: String): List<String> {
        val regex = Regex("<$tag>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        return regex.findAll(text)
            .mapNotNull { it.groupValues.getOrNull(1)?.trim()?.takeIf { value -> value.isNotBlank() } }
            .toList()
    }

    private fun parseReceiptPayloadDrafts(payload: String): List<AiReceiptDraft> {
        return runCatching {
            val json = JSONObject(payload)
            val parentDraft = json.toReceiptDraft()
            val items = json.optJSONArray("items")
            if (items == null || items.length() == 0) {
                listOf(parentDraft)
            } else {
                buildList {
                    for (index in 0 until items.length()) {
                        val item = items.optJSONObject(index) ?: continue
                        add(item.toReceiptDraft(parentDraft))
                    }
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun JSONObject.toReceiptDraft(parent: AiReceiptDraft? = null): AiReceiptDraft {
        return AiReceiptDraft(
            isReceipt = if (has("isReceipt")) optBoolean("isReceipt", parent?.isReceipt ?: true) else parent?.isReceipt ?: true,
            action = optString("action").takeIf { it.isNotBlank() } ?: parent?.action ?: ACTION_CREATE,
            amount = if (has("amount") && !isNull("amount")) optDouble("amount") else parent?.amount,
            category = optString("category").takeIf { it.isNotBlank() } ?: parent?.category,
            desc = optString("desc").takeIf { it.isNotBlank() } ?: parent?.desc,
            recordTime = optString("recordTime").takeIf { it.isNotBlank() } ?: parent?.recordTime,
            date = optString("date").takeIf { it.isNotBlank() } ?: parent?.date,
            transactionId = if (has("transactionId") && !isNull("transactionId")) {
                optLong("transactionId").takeIf { it > 0L } ?: parent?.transactionId
            } else {
                parent?.transactionId
            },
        )
    }

    private fun shouldAppendApplySummary(
        result: ApplyTransactionsResult,
        totalDraftCount: Int,
    ): Boolean {
        if (!result.hasSuccess) return result.failedTransactions.isNotEmpty()
        if (result.failedTransactions.isNotEmpty()) return true
        if (totalDraftCount > 1) return true
        if (result.appliedTransactions.size > 1) return true
        return (result.createCount > 0 && result.updateCount > 0) || result.deleteCount > 0
    }

    private fun buildApplySummaryText(result: ApplyTransactionsResult): String {
        if (result.appliedTransactions.isEmpty() && result.failedTransactions.isEmpty()) return ""

        val summaryParts = mutableListOf<String>()
        if (result.appliedTransactions.isNotEmpty()) {
            summaryParts += "成功${result.appliedTransactions.size}笔"
        }
        if (result.failedTransactions.isNotEmpty()) {
            summaryParts += "失败${result.failedTransactions.size}笔"
        }

        val actionParts = mutableListOf<String>()
        if (result.createCount > 0) {
            actionParts += "新增${result.createCount}笔"
        }
        if (result.updateCount > 0) {
            actionParts += "修改${result.updateCount}笔"
        }
        if (result.deleteCount > 0) {
            actionParts += "删除${result.deleteCount}笔"
        }

        val failureText = result.failedTransactions
            .joinToString("\n") { "第${it.index}笔失败：${it.reason}" }

        return buildList {
            if (summaryParts.isNotEmpty()) {
                add("批量处理结果：${summaryParts.joinToString("，")}。")
            }
            if (actionParts.isNotEmpty()) {
                add("执行明细：${actionParts.joinToString("，")}。")
            }
            if (failureText.isNotBlank()) {
                add(failureText)
            }
        }.joinToString("\n")
    }

    private fun defaultReceiptMessage(result: ApplyTransactionsResult): String {
        if (!result.hasSuccess) return "我暂时没成功处理这些账单。"
        if (result.appliedTransactions.size == 1 && result.failedTransactions.isEmpty()) {
            return when (result.primaryAppliedTransaction?.action) {
                ACTION_UPDATE -> "已帮你修改这笔记录。"
                ACTION_DELETE -> "已为你删除这笔记录。"
                else -> "已为你记录这笔账单。"
            }
        }
        return "批量记账已处理完成。"
    }

    private fun mergeAssistantText(
        baseText: String,
        extraText: String?,
        fallbackText: String,
    ): String {
        val parts = buildList {
            baseText.trim().takeIf { it.isNotBlank() }?.let(::add)
            extraText?.trim()?.takeIf { it.isNotBlank() }?.let(::add)
        }
        return parts.joinToString("\n\n").ifBlank { fallbackText }
    }

    private fun buildTransactionBindingsPayload(appliedTransactions: List<AppliedTransaction>): String? {
        if (appliedTransactions.isEmpty()) return null
        return JSONArray().apply {
            appliedTransactions.forEach { applied ->
                put(
                    JSONObject().apply {
                        put("transactionId", applied.transactionId)
                        put("action", applied.action)
                    },
                )
            }
        }.toString()
    }

    private fun parseTransactionBindings(raw: String?): List<TransactionBinding> {
        val payload = raw?.trim().orEmpty()
        if (payload.isBlank()) return emptyList()
        return runCatching {
            val array = JSONArray(payload)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val transactionId = item.optLong("transactionId")
                    if (transactionId <= 0L) continue
                    add(
                        TransactionBinding(
                            transactionId = transactionId,
                            action = item.optString("action").ifBlank { ACTION_CREATE },
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun parseReceiptSummary(
        content: String,
        isReceipt: Boolean,
        linkedTransaction: TransactionEntity?,
        transactionBindings: List<TransactionBinding>,
        transactionById: Map<Long, TransactionEntity>,
        fallbackTimestamp: Long,
    ): AiChatReceiptSummary? {
        val payload = findPayload(content, "RECEIPT") ?: findPayload(content, "DATA")
        if (payload == null) {
            if (!isReceipt || linkedTransaction == null) return null
            return AiChatReceiptSummary(
                isBatch = false,
                successCount = 1,
                failureCount = 0,
                items = listOf(
                    AiChatReceiptItem(
                        index = 1,
                        status = RECEIPT_STATUS_SUCCESS,
                        action = ACTION_CREATE,
                        category = linkedTransaction.categoryName.ifBlank { "已识别" },
                        amount = formatReceiptAmount(linkedTransaction.amount),
                        desc = linkedTransaction.remark,
                        recordTimestamp = linkedTransaction.recordTimestamp,
                        isIncome = linkedTransaction.type == 1,
                        transactionId = linkedTransaction.id,
                    ),
                ),
                errors = emptyList(),
            )
        }

        val json = runCatching { JSONObject(payload) }.getOrNull() ?: return null
        val successBindings = transactionBindings.mapNotNull { binding ->
            transactionById[binding.transactionId]?.let { binding to it }
        }
        var successBindingCursor = 0
        val items = buildList {
            val rawItems = json.optJSONArray("items")
            if (rawItems != null && rawItems.length() > 0) {
                for (arrayIndex in 0 until rawItems.length()) {
                    val itemJson = rawItems.optJSONObject(arrayIndex) ?: continue
                    val status = normalizeReceiptStatus(itemJson.optString("status"))
                    val fallbackBinding = if (status == RECEIPT_STATUS_SUCCESS) {
                        successBindings.getOrNull(successBindingCursor)
                    } else {
                        null
                    }
                    if (status == RECEIPT_STATUS_SUCCESS && successBindingCursor < successBindings.size) {
                        successBindingCursor += 1
                    }
                    val explicitTransactionId = itemJson.optLong("transactionId").takeIf { it > 0L }
                    val transactionId = explicitTransactionId ?: fallbackBinding?.first?.transactionId
                    val resolvedTransaction = explicitTransactionId?.let(transactionById::get) ?: fallbackBinding?.second
                    add(
                        buildReceiptItem(
                            sourceJson = itemJson,
                            fallbackJson = json,
                            defaultIndex = arrayIndex + 1,
                            status = status,
                            linkedTransaction = resolvedTransaction,
                            fallbackTimestamp = fallbackTimestamp,
                            transactionId = transactionId,
                        ),
                    )
                }
            } else if (isReceipt || linkedTransaction != null) {
                add(
                    buildReceiptItem(
                        sourceJson = json,
                        fallbackJson = null,
                        defaultIndex = 1,
                        status = normalizeReceiptStatus(json.optString("status")),
                        linkedTransaction = linkedTransaction,
                        fallbackTimestamp = fallbackTimestamp,
                        transactionId = linkedTransaction?.id,
                    ),
                )
            }
        }
        if (items.isEmpty()) return null

        val successCount = if (json.has("successCount") && !json.isNull("successCount")) {
            json.optInt("successCount").coerceAtLeast(0)
        } else {
            items.count { it.status == RECEIPT_STATUS_SUCCESS }
        }
        val failureCount = if (json.has("failureCount") && !json.isNull("failureCount")) {
            json.optInt("failureCount").coerceAtLeast(0)
        } else {
            items.count { it.status == RECEIPT_STATUS_FAILED }
        }
        val isBatchMode = json.optString("mode").equals("batch", ignoreCase = true) ||
            items.size > 1 ||
            successCount > 1 ||
            failureCount > 0
        val errors = parseReceiptErrors(json)
            .ifEmpty { items.mapNotNull { it.failureReason }.distinct() }

        return AiChatReceiptSummary(
            isBatch = isBatchMode,
            successCount = successCount,
            failureCount = failureCount,
            items = items,
            errors = errors,
        )
    }

    private fun parseReceiptErrors(json: JSONObject): List<String> {
        val errors = json.optJSONArray("errors") ?: return emptyList()
        return buildList {
            for (index in 0 until errors.length()) {
                val value = errors.optString(index).trim()
                if (value.isNotBlank()) add(value)
            }
        }
    }

    private fun buildReceiptItem(
        sourceJson: JSONObject,
        fallbackJson: JSONObject?,
        defaultIndex: Int,
        status: String,
        linkedTransaction: TransactionEntity?,
        fallbackTimestamp: Long,
        transactionId: Long?,
    ): AiChatReceiptItem {
        return AiChatReceiptItem(
            index = sourceJson.optInt("index").takeIf { it > 0 } ?: defaultIndex,
            status = status,
            action = sourceJson.optString("action")
                .takeIf { it.isNotBlank() }
                ?: fallbackJson?.optString("action")?.takeIf { it.isNotBlank() }
                ?: ACTION_CREATE,
            category = sourceJson.optString("category")
                .takeIf { it.isNotBlank() }
                ?: fallbackJson?.optString("category")?.takeIf { it.isNotBlank() }
                ?: linkedTransaction?.categoryName
                ?: "已识别",
            amount = parseReceiptAmount(sourceJson)
                ?: fallbackJson?.let(::parseReceiptAmount)
                ?: linkedTransaction?.amount?.let(::formatReceiptAmount)
                ?: "",
            desc = sourceJson.optString("desc")
                .takeIf { it.isNotBlank() }
                ?: fallbackJson?.optString("desc")?.takeIf { it.isNotBlank() }
                ?: linkedTransaction?.remark
                ?: "",
            recordTimestamp = parseReceiptRecordTimestamp(sourceJson)
                ?: fallbackJson?.let(::parseReceiptRecordTimestamp)
                ?: linkedTransaction?.recordTimestamp
                ?: fallbackTimestamp,
            isIncome = parseReceiptType(sourceJson)
                ?: fallbackJson?.let(::parseReceiptType)
                ?: linkedTransaction?.type?.let { it == 1 },
            transactionId = transactionId,
            failureReason = sourceJson.optString("reason").takeIf { it.isNotBlank() }
                ?: fallbackJson?.let(::parseReceiptErrors)?.firstOrNull(),
        )
    }

    private fun normalizeReceiptStatus(rawStatus: String?): String {
        return when (rawStatus?.trim()?.lowercase(Locale.ROOT)) {
            RECEIPT_STATUS_FAILED, "failure", "error" -> RECEIPT_STATUS_FAILED
            else -> RECEIPT_STATUS_SUCCESS
        }
    }

    private fun parseReceiptAmount(json: JSONObject): String? {
        if (!json.has("amount") || json.isNull("amount")) return null
        return formatReceiptAmount(json.optDouble("amount"))
    }

    private fun formatReceiptAmount(amount: Double): String {
        return String.format(Locale.CHINA, "%.2f", abs(amount))
    }

    private fun parseReceiptType(json: JSONObject): Boolean? {
        val typeValue = json.optString("type").trim().lowercase(Locale.ROOT)
        val typeNumber = if (json.has("type") && !json.isNull("type")) {
            json.optInt("type", -1)
        } else {
            -1
        }
        return when {
            typeValue == "income" || typeValue == "1" -> true
            typeValue == "expense" || typeValue == "0" -> false
            typeNumber == 1 -> true
            typeNumber == 0 -> false
            else -> null
        }
    }

    private fun parseReceiptRecordTimestamp(json: JSONObject): Long? {
        parseRecordTimeFromDraft(json.optString("recordTime").takeIf { it.isNotBlank() }, Calendar.getInstance())?.let { return it }
        parseDateFromDraft(json.optString("date").takeIf { it.isNotBlank() }, Calendar.getInstance())?.let { return it }
        return null
    }

    private companion object {
        const val ACTION_CREATE = "create"
        const val ACTION_UPDATE = "update"
        const val ACTION_DELETE = "delete"
        const val RECEIPT_STATUS_SUCCESS = "success"
        const val RECEIPT_STATUS_FAILED = "failed"

        val updateIntentHints = listOf("记错", "改成", "改为", "不对", "修改", "更正")
        val deleteIntentHints = listOf("删除", "删掉", "删了", "移除", "清除", "去掉")
        val correctionIntentHints = listOf("不是这个意思", "你理解错了", "我不是要", "我说的是", "纠正", "不对")
        val deleteConfirmHints = listOf("确认删除", "确认", "确定", "继续删除", "删吧", "是的删除")
        val deleteGenericKeywordHints = listOf("最近", "全部", "所有", "账单", "记录", "删除", "删掉", "清空")
        val incomeKeywordHints = listOf("工资", "收入", "奖金", "报销", "收款", "进账")
        val expenseKeywordHints = listOf("花了", "花费", "支出", "消费", "买了", "付款", "付了")
        val writeIntentHints = updateIntentHints + deleteIntentHints + incomeKeywordHints + expenseKeywordHints + listOf("记账", "入账")
        val amountWithCurrencyRegex = Regex("(\\d+(?:\\.\\d+)?)\\s*(?:元|块|人民币|RMB|rmb|￥|¥)")
        val plainAmountRegex = Regex("(?<!\\d)(\\d{1,6}(?:\\.\\d{1,2})?)(?!\\d)")
        val transactionIdHintRegexes = listOf(
            Regex("(?:交易|账单|记录)\\s*(?:id|ID|Id|编号|号)?\\s*[:：#]?\\s*(\\d{1,18})"),
            Regex("(?:id|ID|Id|编号|记录号|账单号|交易号)\\s*[:：#]?\\s*(\\d{1,18})"),
        )

        val queryStrongIntentHints = listOf(
            "最近一笔", "最新一笔", "查询", "查一下", "看看记录", "最高", "花得最多",
            "最频繁", "统计", "分析", "占比", "趋势", "总吃同一家", "同一家", "top",
            "哪一类消费最多", "排行", "花了多少钱", "花了多少", "总花费", "总支出",
        )
        val querySoftIntentHints = listOf(
            "查下", "看下", "看一下", "看一眼", "查查", "查一查",
            "本月", "这个月", "上个月", "近7天", "近30天", "最近7天", "最近30天",
        )
        val queryWindowRelaxedHints = listOf("今天", "昨天", "本月", "这个月", "上个月", "近7天", "近30天", "最近7天", "最近30天", "最近一周", "最近一个月")
        val queryOverrideHints = listOf("最近一笔", "最高", "最频繁", "统计", "分析", "占比", "趋势", "同一家", "花了多少", "总共")
        val statsIntentHints = listOf("统计", "分析", "占比", "趋势", "最频繁", "同一家", "哪一类消费最多", "排行")
        val spendVerbQueryHints = listOf("花了", "花费", "支出", "消费", "花销", "用了")
        val spendQuestionHints = listOf("花了多少钱", "花了多少", "总共花了多少", "总花费", "总支出", "消费多少", "花费多少")
        val highestSpendingHints = listOf("最高", "花得最多", "最贵", "最大一笔")
        val recentOneHints = listOf("最近一笔", "最新一笔", "最后一笔")
        val weekWindowHints = listOf(
            "最近一周", "过去一周", "近一周", "这一周", "本周", "上周",
            "7天", "七天", "近7天", "最近7天", "过去7天", "近七天", "一周内",
        )
        val monthWindowHints = listOf("最近一个月", "过去一个月", "本月", "这个月", "上个月", "月度", "30天", "近30天", "最近30天")
        val yearWindowHints = listOf("最近一年", "过去一年", "年度", "12个月")
        val recentTargetHints = listOf("最近一笔", "最新一笔", "上一笔", "刚刚那笔", "刚才那笔", "刚刚", "刚才")
        val merchantHints = listOf("同一家", "商家", "店", "门店")
        val timeSlotHints = listOf("时段", "几点", "什么时候", "早上", "晚上", "中午")
        val trendHints = listOf("趋势", "变化", "走势")
        val frequencyHints = listOf("最频繁", "频繁", "次数", "几次", "总是", "同一家")
        val ratioHints = listOf("占比", "比例", "百分比")
        val writeSceneHints = listOf("打车", "地铁", "公交", "晚饭", "中饭", "午饭", "早餐", "吃饭", "吃中饭", "夜宵", "咖啡", "奶茶", "外卖", "买菜", "房租", "水电", "缴费", "交费")
        val queryKeywordHints = listOf(
            "餐饮", "奶茶", "咖啡", "打车", "地铁", "公交", "购物", "外卖", "房租", "工资", "账单",
            "瑞幸", "星巴克", "麦当劳", "肯德基",
        )
        val amountMinRegexes = listOf(
            Regex("(?:超过|大于|至少|不低于)\\s*(\\d+(?:\\.\\d+)?)"),
            Regex("(?:more than|>=)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        )
        val amountMaxRegexes = listOf(
            Regex("(?:低于|小于|不超过|至多)\\s*(\\d+(?:\\.\\d+)?)"),
            Regex("(?:less than|<=)\\s*(\\d+(?:\\.\\d+)?)", RegexOption.IGNORE_CASE),
        )
        val chineseDigitMap = mapOf(
            "零" to 0,
            "一" to 1,
            "二" to 2,
            "两" to 2,
            "三" to 3,
            "四" to 4,
            "五" to 5,
            "六" to 6,
            "七" to 7,
            "八" to 8,
            "九" to 9,
        )
        val categoryKeywordHints = listOf(
            "餐饮", "美食", "早餐", "午餐", "晚餐", "午饭", "中饭", "晚饭", "吃饭", "吃中饭", "夜宵", "宵夜", "饮品", "奶茶", "咖啡", "外卖",
            "交通", "出行", "打车", "地铁", "公交", "高铁",
            "购物", "网购", "服饰", "数码",
            "居家", "家居", "房租", "水电", "日用",
            "娱乐", "休闲", "电影", "旅游", "游戏",
            "医疗", "健康", "药", "医院", "体检",
            "人情", "交际", "礼", "社交", "红包",
            "收入", "工资", "奖金",
        )
        val dateOrTimeKeywordHints = listOf(
            "昨天", "前天", "大前天", "今天", "明天", "后天",
            "早上", "早晨", "中午", "下午", "晚上", "傍晚", "深夜", "半夜",
            "早餐", "午餐", "晚餐", "午饭", "晚饭",
        )
        val dateOrTimeRegex = Regex("\\d{1,2}:\\d{1,2}|\\d{1,2}\\s*(点|时)|\\d{1,2}月\\d{1,2}日|\\d{4}[-/.年]\\d{1,2}")

        val canonicalExpenseCategories = setOf(
            "餐饮美食",
            "交通出行",
            "购物消费",
            "居家生活",
            "娱乐休闲",
            "医疗健康",
            "人情交际",
            "其他",
        )
        val dataPayloadRegex = Regex("<DATA>.*?</DATA>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val receiptPayloadRegex = Regex("<RECEIPT>.*?</RECEIPT>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val notePayloadRegex = Regex("<NOTE>.*?</NOTE>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val thinkPayloadRegex = Regex("<THINK>.*?</THINK>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
        val markdownJsonCodeBlockRegex = Regex(
            "(?is)```(?:json)?\\s*(\\{[\\s\\S]*?\\})\\s*```",
            setOf(RegexOption.IGNORE_CASE),
        )
        val markdownInlineReceiptJsonRegex = Regex(
            "(?is)\\{\\s*\"(?:isReceipt|action|amount|category|recordTime|date|items|successCount|failureCount)\"[\\s\\S]*?\\}",
        )
        val unclosedPayloadStartRegex = Regex("(?is)<(?:DATA|RECEIPT|NOTE|THINK)>[\\s\\S]*$")
        val unclosedMarkdownJsonCodeBlockRegex = Regex("(?is)```(?:json)?[\\s\\S]*$")
        val markdownAnyCodeBlockRegex = Regex("(?is)```[\\s\\S]*?```")
        val unclosedMarkdownAnyCodeBlockRegex = Regex("(?is)```[\\s\\S]*$")
        val jsonQuotedKeyRegex = Regex("\"[^\"]+\"\\s*:")
        val jsonLooseKeyRegex = Regex("(?is)[\\{\\[,]\\s*[A-Za-z_\\u4E00-\\u9FFF][A-Za-z0-9_\\u4E00-\\u9FFF-]*\\s*:")
        val jsonKeyLineRegex = Regex("(?m)^\\s*\"[^\"]+\"\\s*:\\s*.*$")
        val orphanJsonPunctuationLineRegex = Regex("(?m)^\\s*[\\{\\}\\[\\],:]+\\s*$")
        val fenceRegex = Regex("```")
        val payloadTagFragmentPrefixes = listOf(
            "<d", "<da", "<dat", "<data",
            "<r", "<re", "<rec", "<rece", "<recei", "<receip", "<receipt",
            "<n", "<no", "<not", "<note",
            "<t", "<th", "<thi", "<thin", "<think",
        )
        val leadingNullRegex = Regex("(?is)^\\s*(?:null\\s*)+")
        val lineNullRegex = Regex("(?im)^\\s*null\\s*$")
        val repeatedNullRegex = Regex("(?i)(?:null\\s*){4,}")
        val conversationDelimiterRegex = Regex("\\n\\s*\\n+|<MSG>|\\|\\|\\|", setOf(RegexOption.IGNORE_CASE))
        val majorSentenceRegex = Regex("(?<=[。！？!?])")
        const val oneDayMillis = 24L * 60L * 60L * 1000L
        const val defaultPendingIntentTtlMillis = 5L * 60L * 1000L
        const val localReplyMinDelayMs = 520L
        const val localReplyMaxDelayMs = 980L
        const val highRiskDeleteCountThreshold = 3
        const val highRiskDeleteAmountThreshold = 300.0
        val draftDateTimePatterns = listOf(
            "yyyy-MM-dd HH:mm",
            "yyyy/MM/dd HH:mm",
            "yyyy.MM.dd HH:mm",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy/MM/dd HH:mm:ss",
            "yyyy.MM.dd HH:mm:ss",
            "yyyy-MM-dd'T'HH:mm",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy年M月d日 HH:mm",
            "yyyy年M月d日 H:mm",
        )
        val draftDatePatterns = listOf(
            "yyyy-MM-dd",
            "yyyy/MM/dd",
            "yyyy.MM.dd",
            "yyyy年M月d日",
        )
    }
}
