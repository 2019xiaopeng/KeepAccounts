package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiChatRecord
import com.qcb.keepaccounts.ui.model.ChatBackgroundPreset
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.model.ThemePalette
import com.qcb.keepaccounts.ui.theme.IncomeGreen
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import kotlinx.coroutines.delay
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private data class DemoMessage(
    val id: Long,
    val timestamp: Long,
    val role: String,
    val text: String,
    val isReceipt: Boolean = false,
    val receiptCategory: String = "",
    val receiptAmount: String = "",
    val receiptRemark: String = "",
    val receiptRecordTimestamp: Long? = null,
    val receiptIsIncome: Boolean = false,
)

private data class ParsedReceiptPayload(
    val amount: String,
    val category: String,
    val desc: String,
    val recordTimestamp: Long?,
    val isIncome: Boolean?,
)

@Composable
fun ChatScreen(
    aiConfig: AiAssistantConfig,
    userName: String,
    userAvatarUri: String?,
    palette: ThemePalette,
    paddingValues: PaddingValues = PaddingValues(0.dp),
    chatRecords: List<AiChatRecord>,
    isSending: Boolean,
    modifier: Modifier = Modifier,
    initialInput: String? = null,
    onConsumedInitialInput: () -> Unit = {},
    onSendMessage: (String) -> Unit = {},
    onDeleteMessage: (Long) -> Unit = {},
    onBack: (() -> Unit)? = null,
    onOpenAiSettings: () -> Unit = {},
    onOpenManualEntry: (ManualEntryPrefill) -> Unit = {},
) {
    val chronologicalMessages = remember(chatRecords) { chatRecords.map { it.toDemoMessage() } }
    val displayMessages = remember(chronologicalMessages) { chronologicalMessages.asReversed() }
    var inputText by rememberSaveable { mutableStateOf("") }
    var topTip by remember { mutableStateOf("") }
    val listState = rememberLazyListState()
    val showTypingDots = isSending && chronologicalMessages.lastOrNull()?.role == "user"
    var hasAutoScrolledOnce by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(topTip) {
        if (topTip.isNotBlank()) {
            delay(1800)
            topTip = ""
        }
    }

    LaunchedEffect(initialInput) {
        if (!initialInput.isNullOrBlank() && inputText.isBlank()) {
            inputText = initialInput
            topTip = "已从首页带入 AI 记账输入"
            onConsumedInitialInput()
        }
    }

    LaunchedEffect(displayMessages.firstOrNull()?.id, displayMessages.size, showTypingDots) {
        if (displayMessages.isEmpty() && !showTypingDots) return@LaunchedEffect

        if (!hasAutoScrolledOnce) {
            listState.scrollToItem(0)
            hasAutoScrolledOnce = true
        } else {
            listState.animateScrollToItem(0)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = chatBackgroundBrush(aiConfig.chatBackground, palette)),
    ) {
        if (!aiConfig.customChatBackgroundUri.isNullOrBlank()) {
            AsyncImage(
                model = aiConfig.customChatBackgroundUri,
                contentDescription = "chat-custom-background",
                contentScale = ContentScale.Crop,
                modifier = Modifier.matchParentSize(),
            )
        }

        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            palette.primary.copy(alpha = 0.16f),
                            Color.White.copy(alpha = 0.45f),
                            palette.backgroundLight.copy(alpha = 0.82f),
                        ),
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .consumeWindowInsets(paddingValues)
                .imePadding(),
        ) {
            ChatHeader(
                onBack = onBack,
                onOpenAiSettings = onOpenAiSettings,
                assistantName = aiConfig.name,
                assistantAvatar = aiConfig.avatar,
                assistantAvatarUri = aiConfig.avatarUri,
                palette = palette,
                lastMessageTimestamp = chronologicalMessages.lastOrNull()?.timestamp,
            )

            if (topTip.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glassCard(
                            shape = RoundedCornerShape(999.dp),
                            backgroundColor = Color.White.copy(alpha = 0.38f),
                            glowColor = palette.primaryDark.copy(alpha = 0.18f),
                        )
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.MoreHoriz,
                        contentDescription = "tip",
                        tint = WarmBrown.copy(alpha = 0.72f),
                        modifier = Modifier.size(14.dp),
                    )
                    Text(text = topTip, color = WarmBrown.copy(alpha = 0.85f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }

            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f),
                reverseLayout = true,
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (showTypingDots) {
                    item {
                        TypingRow(
                            assistantAvatar = aiConfig.avatar,
                            assistantAvatarUri = aiConfig.avatarUri,
                            palette = palette,
                        )
                    }
                }

                items(displayMessages, key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        assistantAvatar = aiConfig.avatar,
                        assistantAvatarUri = aiConfig.avatarUri,
                        userName = userName,
                        userAvatarUri = userAvatarUri,
                        palette = palette,
                        onDelete = {
                            onDeleteMessage(message.id)
                            topTip = "已删除这条回执"
                        },
                        onEdit = {
                            onOpenManualEntry(
                                ManualEntryPrefill(
                                    type = if (message.receiptIsIncome) "income" else "expense",
                                    category = message.receiptCategory,
                                    desc = message.receiptRemark,
                                    amount = message.receiptAmount,
                                    recordTimestamp = message.receiptRecordTimestamp ?: message.timestamp,
                                ),
                            )
                            topTip = "已跳转到手动记账，可继续修改"
                        },
                    )
                }
            }

            InputBar(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                input = inputText,
                onInputChange = { inputText = it },
                assistantHint = aiConfig.name + aiConfig.avatar,
                accentColor = palette.primaryDark,
                enabled = !isSending,
                onSend = {
                    if (isSending) return@InputBar
                    val userText = inputText.trim()
                    if (userText.isEmpty()) return@InputBar

                    inputText = ""
                    onSendMessage(userText)
                },
            )
        }
    }
}

private fun chatBackgroundBrush(preset: ChatBackgroundPreset, palette: ThemePalette): Brush {
    val colors = when (preset) {
        ChatBackgroundPreset.NONE -> listOf(
            palette.backgroundLight,
            palette.background,
            Color.White,
        )

        ChatBackgroundPreset.OCEAN -> listOf(
            Color(0xFFEFF7FF),
            Color(0xFFD7ECFF),
            Color(0xFFCFE5FF),
        )

        ChatBackgroundPreset.FOREST -> listOf(
            Color(0xFFF2FFF8),
            Color(0xFFDCF5E7),
            Color(0xFFCDECD8),
        )

        ChatBackgroundPreset.SUNSET -> listOf(
            Color(0xFFFFF4EF),
            Color(0xFFFFE6DA),
            Color(0xFFFFD7CC),
        )
    }
    return Brush.verticalGradient(colors = colors)
}

private fun AiChatRecord.toDemoMessage(): DemoMessage {
    val normalizedRole = if (role == "assistant") "ai" else role
    val payload = parseReceiptPayload(content)
    val pureText = stripReceiptPayload(content)
    val visibleText = pureText.ifBlank {
        if (isReceipt || payload != null) "已经帮主人记好啦，要好好照顾自己哦" else content.trim()
    }
    val parsedAmount = payload?.amount?.takeIf { it.isNotBlank() }
        ?: parseAmount(visibleText)
        ?: "0.00"
    val showReceipt = isReceipt || payload != null
    val resolvedIsIncome = when {
        receiptType != null -> receiptType == 1
        payload?.isIncome != null -> payload.isIncome
        payload?.category?.contains("收入") == true -> true
        else -> false
    }
    val resolvedReceiptTimestamp = if (showReceipt) {
        receiptRecordTimestamp ?: payload?.recordTimestamp ?: timestamp
    } else {
        null
    }
    return DemoMessage(
        id = id,
        timestamp = timestamp,
        role = normalizedRole,
        text = visibleText,
        isReceipt = showReceipt,
        receiptCategory = if (showReceipt) payload?.category?.ifBlank { "已识别" } ?: "已识别" else "",
        receiptAmount = parsedAmount,
        receiptRemark = if (showReceipt) payload?.desc?.ifBlank { visibleText } ?: visibleText else "",
        receiptRecordTimestamp = resolvedReceiptTimestamp,
        receiptIsIncome = showReceipt && resolvedIsIncome,
    )
}

private fun parseAmount(text: String): String? {
    val regex = Regex("(\\d+(?:\\.\\d{1,2})?)")
    return regex.find(text)?.groupValues?.get(1)
}

private fun parseReceiptPayload(text: String): ParsedReceiptPayload? {
    val payload = findPayload(text, "RECEIPT") ?: findPayload(text, "DATA") ?: return null
    return runCatching {
        val json = JSONObject(payload)
        val amount = if (json.has("amount") && !json.isNull("amount")) {
            String.format(Locale.CHINA, "%.2f", kotlin.math.abs(json.optDouble("amount")))
        } else {
            ""
        }
        val typeValue = json.optString("type").trim().lowercase(Locale.ROOT)
        val parsedTypeFromNumber = if (json.has("type") && !json.isNull("type")) {
            json.optInt("type", -1)
        } else {
            -1
        }
        val isIncome = when {
            typeValue == "income" || typeValue == "1" -> true
            typeValue == "expense" || typeValue == "0" -> false
            parsedTypeFromNumber == 1 -> true
            parsedTypeFromNumber == 0 -> false
            else -> null
        }
        ParsedReceiptPayload(
            amount = amount,
            category = json.optString("category").ifBlank { "已识别" },
            desc = json.optString("desc").ifBlank { "" },
            recordTimestamp = parsePayloadDateToTimestamp(json.optString("date")),
            isIncome = isIncome,
        )
    }.getOrNull()
}

private fun parsePayloadDateToTimestamp(rawDate: String?): Long? {
    val dateText = rawDate?.trim().orEmpty()
    if (dateText.isBlank()) return null

    return runCatching {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).apply { isLenient = false }
        sdf.parse(dateText)?.time
    }.getOrNull()
}

private fun stripReceiptPayload(text: String): String {
    return text
        .replace(dataPayloadRegex, "")
        .replace(receiptPayloadRegex, "")
        .replace(notePayloadRegex, "")
        .replace(thinkPayloadRegex, "")
        .replace(leadingNullRegex, "")
        .replace(lineNullRegex, "")
        .replace(repeatedNullRegex, "")
        .replace(Regex("\\n{3,}"), "\n\n")
        .trim()
}

private fun findPayload(text: String, tag: String): String? {
    val regex = Regex("<$tag>(.*?)</$tag>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
    return regex.find(text)?.groupValues?.getOrNull(1)?.trim()?.takeIf { it.isNotBlank() }
}

private val dataPayloadRegex = Regex("<DATA>.*?</DATA>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val receiptPayloadRegex = Regex("<RECEIPT>.*?</RECEIPT>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val notePayloadRegex = Regex("<NOTE>.*?</NOTE>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val thinkPayloadRegex = Regex("<THINK>.*?</THINK>", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.IGNORE_CASE))
private val leadingNullRegex = Regex("(?is)^\\s*(?:null\\s*)+")
private val lineNullRegex = Regex("(?im)^\\s*null\\s*$")
private val repeatedNullRegex = Regex("(?i)(?:null\\s*){4,}")

@Composable
private fun ChatHeader(
    onBack: (() -> Unit)?,
    onOpenAiSettings: () -> Unit,
    assistantName: String,
    assistantAvatar: String,
    assistantAvatarUri: String?,
    palette: ThemePalette,
    lastMessageTimestamp: Long?,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        palette.backgroundLight.copy(alpha = 0.98f),
                        palette.background.copy(alpha = 0.94f),
                        palette.background.copy(alpha = 0.88f),
                    ),
                ),
            )
            .statusBarsPadding()
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .appPressable { onBack?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "back",
                    tint = WarmBrown,
                    modifier = Modifier.size(20.dp),
                )
            }

            Text(
                text = "$assistantName${if (assistantAvatarUri.isNullOrBlank() && !assistantAvatar.startsWith("http")) assistantAvatar else ""}",
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 17.sp,
            )

            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .appPressable { onOpenAiSettings() },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.MoreHoriz,
                    contentDescription = "more",
                    tint = WarmBrown,
                    modifier = Modifier.size(20.dp),
                )
            }
        }

        Text(
            text = formatHeaderSubtitle(lastMessageTimestamp),
            color = WarmBrown.copy(alpha = 0.64f),
            fontWeight = FontWeight.Medium,
            fontSize = 11.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(palette.primaryDark.copy(alpha = 0.09f), RoundedCornerShape(999.dp)),
        )
    }
}

@Composable
private fun MessageRow(
    message: DemoMessage,
    assistantAvatar: String,
    assistantAvatarUri: String?,
    userName: String,
    userAvatarUri: String?,
    palette: ThemePalette,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val isUser = message.role == "user"
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 20.dp, topEnd = 4.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    } else {
        RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp)
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        if (!isUser) {
            AssistantAvatarBubble(
                assistantAvatar = assistantAvatar,
                assistantAvatarUri = assistantAvatarUri,
                palette = palette,
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .widthIn(max = 288.dp)
                    .clip(bubbleShape)
                    .background(if (isUser) palette.primaryDark.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.98f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                if (isUser) {
                    Text(
                        text = message.text,
                        color = Color.White,
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                    )
                } else {
                    SelectionContainer {
                        Text(
                            text = message.text,
                            color = WarmBrown,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                            lineHeight = 20.sp,
                        )
                    }
                }
            }

            if (message.isReceipt) {
                ReceiptCard(
                    message = message,
                    onDelete = onDelete,
                    onEdit = onEdit,
                )
            }
        }

        if (isUser) {
            UserAvatarBubble(
                name = userName,
                avatarUri = userAvatarUri,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AssistantAvatarBubble(
    assistantAvatar: String,
    assistantAvatarUri: String?,
    palette: ThemePalette,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(listOf(palette.primary, palette.primaryDark))),
        contentAlignment = Alignment.Center,
    ) {
        if (!assistantAvatarUri.isNullOrBlank()) {
            AsyncImage(
                model = assistantAvatarUri,
                contentDescription = "assistant-avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = assistantAvatar, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun UserAvatarBubble(
    name: String,
    avatarUri: String?,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFFF3D2C1)),
        contentAlignment = Alignment.Center,
    ) {
        if (!avatarUri.isNullOrBlank()) {
            AsyncImage(
                model = avatarUri,
                contentDescription = "user-avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text(text = name.take(1).ifBlank { "我" }, color = WarmBrown.copy(alpha = 0.9f), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
        }
    }
}

@Composable
private fun ReceiptCard(
    message: DemoMessage,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    var confirmDelete by rememberSaveable(message.id) { mutableStateOf(false) }
    val receiptDateTimestamp = message.receiptRecordTimestamp ?: message.timestamp
    val amountPrefix = if (message.receiptIsIncome) "+" else "-"
    val amountColor = if (message.receiptIsIncome) IncomeGreen else WatermelonRed

    Column(
        modifier = Modifier
            .padding(top = 8.dp)
            .widthIn(max = 264.dp)
            .glassCard(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color.White.copy(alpha = 0.80f),
                glowColor = MintGreen.copy(alpha = 0.18f),
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "🤍 已记账成功 🤍",
            color = MintGreen,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 13.sp,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        ReceiptRow(icon = Icons.Rounded.Category, label = "📁 分类", value = message.receiptCategory)
        ReceiptRow(icon = Icons.Rounded.AttachMoney, label = "💰 金额", value = "$amountPrefix${message.receiptAmount}", valueColor = amountColor)
        ReceiptRow(icon = Icons.Rounded.MoreHoriz, label = "📝 备注", value = message.receiptRemark)
        ReceiptRow(icon = Icons.Rounded.Today, label = "📅 日期", value = formatReceiptDate(receiptDateTimestamp))
        ReceiptRow(icon = Icons.Rounded.Schedule, label = "🕒 记录时间", value = formatReceiptTime(message.timestamp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            if (confirmDelete) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFFF0F0))
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "确认删除这条记录？", color = WatermelonRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(Color.White)
                                .appPressable { confirmDelete = false },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "✕", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                        Box(
                            modifier = Modifier
                                .size(22.dp)
                                .clip(CircleShape)
                                .background(WatermelonRed)
                                .appPressable {
                                    confirmDelete = false
                                    onDelete()
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = "✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFF3F4F6))
                        .appPressable { onEdit() }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "✏️ 修改", color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(999.dp))
                        .background(Color(0xFFFFF0F0))
                        .appPressable { confirmDelete = true }
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "🗑️ 删除", color = WatermelonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    icon: ImageVector,
    label: String,
    value: String,
    valueColor: Color = WarmBrown,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Row(horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = WarmBrownMuted.copy(alpha = 0.6f),
                modifier = Modifier.size(12.dp),
            )
            Text(text = label, color = WarmBrownMuted, fontWeight = FontWeight.Medium, fontSize = 12.sp)
        }
        SelectionContainer {
            Text(text = value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TypingRow(
    assistantAvatar: String,
    assistantAvatarUri: String?,
    palette: ThemePalette,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatarBubble(
            assistantAvatar = assistantAvatar,
            assistantAvatarUri = assistantAvatarUri,
            palette = palette,
            modifier = Modifier.padding(end = 8.dp),
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(Color.White.copy(alpha = 0.98f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            JumpingDots(color = palette.primaryDark)
        }
    }
}

@Composable
private fun JumpingDots(color: Color = MintGreen) {
    val infinite = rememberInfiniteTransition(label = "loading")
    val dot1 = infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot1",
    )
    val dot2 = infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, delayMillis = 140, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot2",
    )
    val dot3 = infinite.animateFloat(
        initialValue = 0.25f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, delayMillis = 280, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "dot3",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Dot(dot1.value, color)
        Dot(dot2.value, color)
        Dot(dot3.value, color)
    }
}

@Composable
private fun Dot(alpha: Float, color: Color) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .alpha(alpha)
            .background(color),
    )
}

@Composable
private fun InputBar(
    modifier: Modifier = Modifier,
    input: String,
    onInputChange: (String) -> Unit,
    assistantHint: String,
    accentColor: Color,
    enabled: Boolean,
    onSend: () -> Unit,
) {
    Row(
        modifier = modifier
            .glassCard(
                shape = RoundedCornerShape(24.dp),
                backgroundColor = Color.White.copy(alpha = 0.74f),
                glowColor = accentColor.copy(alpha = 0.18f),
            )
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = {
                Text(
                    text = "发送消息给 $assistantHint...",
                    color = WarmBrownMuted.copy(alpha = 0.95f),
                    fontSize = 13.sp,
                )
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
            ),
            singleLine = false,
            minLines = 1,
            maxLines = 4,
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 44.dp, max = 112.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(Color.White.copy(alpha = 0.90f)),
        )

        Box(
            modifier = Modifier
                .size(44.dp)
                .clip(CircleShape)
                .alpha(if (enabled) 1f else 0.5f)
                .background(brush = Brush.linearGradient(listOf(accentColor, accentColor.copy(alpha = 0.82f))))
                .appPressable(enabled = enabled) { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "send",
                tint = Color.White,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

private fun formatHeaderSubtitle(timestamp: Long?): String {
    if (timestamp == null) return "开始聊聊吧"

    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = timestamp }
    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) &&
        now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)

    return if (sameDay) {
        "今天 ${SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))}"
    } else {
        SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
    }
}

private fun formatReceiptDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}

private fun formatReceiptTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
}
