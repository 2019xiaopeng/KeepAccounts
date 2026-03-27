package com.qcb.keepaccounts.ui.screens

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.AttachMoney
import androidx.compose.material.icons.rounded.Category
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Mic
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.ChatBackgroundPreset
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import kotlinx.coroutines.delay

private data class DemoMessage(
    val id: Long,
    val role: String,
    val text: String,
    val isReceipt: Boolean = false,
    val receiptCategory: String = "",
    val receiptAmount: String = "",
    val receiptRemark: String = "",
)

private val initialChatMessages = listOf(
    DemoMessage(id = 1, role = "user", text = "牛肉粉丝汤 22"),
    DemoMessage(id = 2, role = "ai", text = "已经帮你记在账上了"),
    DemoMessage(id = 3, role = "ai", text = "喝点热汤对肠胃很好，慢点喝别烫到"),
    DemoMessage(id = 4, role = "ai", text = "吃饱之后稍微站起来走动一下"),
    DemoMessage(
        id = 5,
        role = "ai",
        text = "如果眼睛觉得累了，就放下手机休息一会",
        isReceipt = true,
        receiptCategory = "餐饮",
        receiptAmount = "22",
        receiptRemark = "牛肉粉丝汤",
    ),
)

@Composable
fun ChatScreen(
    aiConfig: AiAssistantConfig,
    userName: String,
    modifier: Modifier = Modifier,
    initialInput: String? = null,
    onConsumedInitialInput: () -> Unit = {},
    onBack: (() -> Unit)? = null,
    onOpenManualEntry: (ManualEntryPrefill) -> Unit = {},
) {
    val messages = remember { mutableStateListOf<DemoMessage>().apply { addAll(initialChatMessages) } }
    var inputText by remember { mutableStateOf("") }
    var isTyping by remember { mutableStateOf(false) }
    var topTip by remember { mutableStateOf("") }

    LaunchedEffect(initialInput) {
        if (!initialInput.isNullOrBlank() && inputText.isBlank()) {
            inputText = initialInput
            topTip = "已从首页带入 AI 记账输入"
            onConsumedInitialInput()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(brush = chatBackgroundBrush(aiConfig.chatBackground)),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            MintGreen.copy(alpha = 0.12f),
                            Color.White.copy(alpha = 0.45f),
                            Color(0xFFEFF8F6).copy(alpha = 0.86f),
                        ),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(
                onBack = onBack,
                assistantName = aiConfig.name,
                assistantAvatar = aiConfig.avatar,
            )

            if (topTip.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .glassCard(
                            shape = RoundedCornerShape(999.dp),
                            backgroundColor = Color.White.copy(alpha = 0.38f),
                            glowColor = MintGreen.copy(alpha = 0.18f),
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
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        assistantAvatar = aiConfig.avatar,
                        userName = userName,
                        onDelete = {
                            messages.removeAll { it.id == message.id }
                            topTip = "已删除这条回执"
                        },
                        onEdit = {
                            onOpenManualEntry(
                                ManualEntryPrefill(
                                    type = "expense",
                                    category = message.receiptCategory,
                                    desc = message.receiptRemark,
                                    amount = message.receiptAmount,
                                ),
                            )
                            topTip = "已跳转到手动记账，可继续修改"
                        },
                    )
                }

                if (isTyping) {
                    item { TypingRow(assistantAvatar = aiConfig.avatar) }
                }

                item { Spacer(modifier = Modifier.height(112.dp)) }
            }
        }

        InputBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
            input = inputText,
            onInputChange = { inputText = it },
            assistantName = aiConfig.name,
            onSend = {
                val userText = inputText.trim()
                if (userText.isEmpty() || isTyping) return@InputBar

                inputText = ""
                messages.add(
                    DemoMessage(
                        id = System.currentTimeMillis(),
                        role = "user",
                        text = userText,
                    ),
                )
                isTyping = true
            },
        )
    }

    LaunchedEffect(isTyping) {
        if (!isTyping) return@LaunchedEffect

        delay(700)
        val latestUser = messages.lastOrNull { it.role == "user" }?.text.orEmpty()
        val amount = parseAmount(latestUser)
        val aiReply = if (amount != null) {
            DemoMessage(
                id = System.currentTimeMillis() + 1,
                role = "ai",
                text = "收到啦，已经帮你记下这笔账。今天也要照顾好自己。",
                isReceipt = true,
                receiptCategory = "餐饮",
                receiptAmount = amount,
                receiptRemark = latestUser,
            )
        } else {
            DemoMessage(
                id = System.currentTimeMillis() + 1,
                role = "ai",
                text = "我在呢，你可以直接说“午饭 26”这种格式，我会自动记账。",
            )
        }
        messages.add(aiReply)
        topTip = if (amount != null) "已识别金额并生成回执" else topTip
        isTyping = false
    }
}

private fun chatBackgroundBrush(preset: ChatBackgroundPreset): Brush {
    val colors = when (preset) {
        ChatBackgroundPreset.NONE -> listOf(
            Color(0xFFF6FFFC),
            Color(0xFFEAF8F4),
            Color(0xFFE0F3EE),
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

private fun parseAmount(text: String): String? {
    val regex = Regex("(\\d+(?:\\.\\d{1,2})?)")
    return regex.find(text)?.groupValues?.get(1)
}

@Composable
private fun ChatHeader(
    onBack: (() -> Unit)?,
    assistantName: String,
    assistantAvatar: String,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .glassCard(
                shape = RoundedCornerShape(bottomStart = 18.dp, bottomEnd = 18.dp, topStart = 0.dp, topEnd = 0.dp),
                backgroundColor = Color.White.copy(alpha = 0.40f),
                glowColor = MintGreen.copy(alpha = 0.16f),
            )
            .padding(horizontal = 14.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.78f))
                .clickable { onBack?.invoke() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "back",
                tint = WarmBrown,
                modifier = Modifier.size(20.dp),
            )
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "$assistantName${if (assistantAvatar.startsWith("http")) "" else assistantAvatar}",
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
            Text(text = "今天 16:51", color = WarmBrown.copy(alpha = 0.62f), fontSize = 10.sp)
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.78f)),
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
}

@Composable
private fun MessageRow(
    message: DemoMessage,
    assistantAvatar: String,
    userName: String,
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
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(if (isUser) MintGreen.copy(alpha = 0.86f) else Color.White.copy(alpha = 0.98f))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text = message.text, color = WarmBrown, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp)
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
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun AssistantAvatarBubble(
    assistantAvatar: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(brush = Brush.linearGradient(listOf(MintGreen, Color(0xFF88D4B4)))),
        contentAlignment = Alignment.Center,
    ) {
        if (assistantAvatar.startsWith("http://") || assistantAvatar.startsWith("https://")) {
            AsyncImage(
                model = assistantAvatar,
                contentDescription = "assistant-avatar",
                modifier = Modifier
                    .fillMaxSize()
                    .clip(CircleShape),
            )
        } else {
            Text(text = assistantAvatar, color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }
    }
}

@Composable
private fun UserAvatarBubble(
    name: String,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(Color(0xFFF3D2C1)),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = name.take(1).ifBlank { "我" }, color = WarmBrown.copy(alpha = 0.9f), fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun ReceiptCard(
    message: DemoMessage,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
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
        ReceiptRow(icon = Icons.Rounded.AttachMoney, label = "💰 金额", value = "-${message.receiptAmount}", valueColor = WatermelonRed)
        ReceiptRow(icon = Icons.Rounded.MoreHoriz, label = "📝 备注", value = message.receiptRemark)
        ReceiptRow(icon = Icons.Rounded.Today, label = "📅 日期", value = "2026-03-25")
        ReceiptRow(icon = Icons.Rounded.Schedule, label = "🕒 记录时间", value = "今天 14:08")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFF3F4F6))
                    .clickable { onEdit() }
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
                    .clickable { onDelete() }
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(text = "🗑️ 删除", color = WatermelonRed, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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
        Text(text = value, color = valueColor, fontWeight = FontWeight.Bold, fontSize = 12.sp)
    }
}

@Composable
private fun TypingRow(assistantAvatar: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        AssistantAvatarBubble(
            assistantAvatar = assistantAvatar,
            modifier = Modifier.padding(end = 8.dp),
        )

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(Color.White.copy(alpha = 0.98f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            JumpingDots()
        }
    }
}

@Composable
private fun JumpingDots() {
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
        Dot(dot1.value)
        Dot(dot2.value)
        Dot(dot3.value)
    }
}

@Composable
private fun Dot(alpha: Float) {
    Box(
        modifier = Modifier
            .size(8.dp)
            .clip(CircleShape)
            .alpha(alpha)
            .background(MintGreen),
    )
}

@Composable
private fun InputBar(
    modifier: Modifier = Modifier,
    input: String,
    onInputChange: (String) -> Unit,
    assistantName: String,
    onSend: () -> Unit,
) {
    Row(
        modifier = modifier
            .padding(horizontal = 10.dp, vertical = 8.dp)
            .glassCard(
                shape = RoundedCornerShape(topStart = 22.dp, topEnd = 22.dp, bottomStart = 18.dp, bottomEnd = 18.dp),
                backgroundColor = Color.White.copy(alpha = 0.74f),
                glowColor = MintGreen.copy(alpha = 0.18f),
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = Icons.Rounded.Mic,
            contentDescription = "mic",
            tint = WarmBrown.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
        Box(modifier = Modifier.weight(1f)) {
            TextField(
                value = input,
                onValueChange = onInputChange,
                placeholder = {
                    Text(text = "发送消息给 $assistantName...", color = WarmBrownMuted, fontSize = 13.sp)
                },
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xFFF3F4F6),
                    unfocusedContainerColor = Color(0xFFF3F4F6),
                    focusedIndicatorColor = Color.Transparent,
                    unfocusedIndicatorColor = Color.Transparent,
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(999.dp)),
            )
            Icon(
                imageVector = Icons.Rounded.Image,
                contentDescription = "image",
                tint = WarmBrown.copy(alpha = 0.4f),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 12.dp)
                    .size(18.dp),
            )
        }
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
                .background(brush = Brush.linearGradient(listOf(MintGreen, Color(0xFF88D4B4))))
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Rounded.Send,
                contentDescription = "send",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}
