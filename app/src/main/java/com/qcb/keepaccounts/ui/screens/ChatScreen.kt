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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    modifier: Modifier = Modifier,
    initialInput: String? = null,
    onConsumedInitialInput: () -> Unit = {},
    onBack: (() -> Unit)? = null,
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
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1C3B60),
                        Color(0xFF193352),
                        Color(0xFF162A44),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .matchParentSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x801A365D),
                            Color(0xCC1A365D),
                        ),
                    ),
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ChatHeader(onBack = onBack)

            if (topTip.isNotBlank()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 8.dp)
                        .background(Color.White.copy(alpha = 0.16f), RoundedCornerShape(999.dp))
                        .padding(horizontal = 12.dp, vertical = 5.dp),
                ) {
                    Text(text = topTip, color = Color.White, fontWeight = FontWeight.Bold)
                }
            }

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    MessageRow(
                        message = message,
                        onDelete = {
                            messages.removeAll { it.id == message.id }
                            topTip = "已删除这条回执"
                        },
                        onEdit = {
                            inputText = "${message.receiptRemark} ${message.receiptAmount}".trim()
                            topTip = "已填充到输入框，可继续修改后发送"
                        },
                    )
                }

                if (isTyping) {
                    item {
                        TypingRow()
                    }
                }

                item { Spacer(modifier = Modifier.height(92.dp)) }
            }
        }

        InputBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth(),
            input = inputText,
            onInputChange = { inputText = it },
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
                text = "收到啦，已经帮你记下这笔账。今天也要照顾好自己 🌿",
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

private fun parseAmount(text: String): String? {
    val regex = Regex("(\\d+(?:\\.\\d{1,2})?)")
    return regex.find(text)?.groupValues?.get(1)
}

@Composable
private fun ChatHeader(onBack: (() -> Unit)?) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.10f))
            .padding(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "←",
            color = Color.White,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onBack?.invoke() },
        )
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Nanami🌊", color = Color.White, fontWeight = FontWeight.ExtraBold)
            Text(text = "今天 16:51", color = Color.White.copy(alpha = 0.6f))
        }
        Text(text = "⋯", color = Color.White, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun MessageRow(
    message: DemoMessage,
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
            Box(
                modifier = Modifier
                    .padding(end = 8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF2D5C92)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🌊")
            }
        }

        Column(horizontalAlignment = if (isUser) Alignment.End else Alignment.Start) {
            Box(
                modifier = Modifier
                    .shadow(6.dp, bubbleShape)
                    .clip(bubbleShape)
                    .background(if (isUser) MintGreen else Color.White)
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            ) {
                Text(text = message.text, color = WarmBrown, fontWeight = FontWeight.Medium)
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
            Box(
                modifier = Modifier
                    .padding(start = 8.dp)
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF3D2C1)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🐶")
            }
        }
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
            .widthIn(max = 260.dp)
            .clip(RoundedCornerShape(24.dp))
            .background(Color.White.copy(alpha = 0.95f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "🤍 已记账成功 🤍", color = MintGreen, fontWeight = FontWeight.ExtraBold)

        ReceiptRow(label = "📁 分类", value = message.receiptCategory)
        ReceiptRow(label = "💰 金额", value = "-${message.receiptAmount}", valueColor = WatermelonRed)
        ReceiptRow(label = "📝 备注", value = message.receiptRemark)
        ReceiptRow(label = "📅 日期", value = "2026-03-25")
        ReceiptRow(label = "🕒 记录时间", value = "今天 14:08")

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFF3F4F6))
                    .clickable { onEdit() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "✏️ 修改", color = WarmBrown, fontWeight = FontWeight.Bold)
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color(0xFFFFF0F0))
                    .clickable { onDelete() }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🗑️ 删除", color = WatermelonRed, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ReceiptRow(
    label: String,
    value: String,
    valueColor: Color = WarmBrown,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(text = label, color = WarmBrownMuted, fontWeight = FontWeight.Medium)
        Text(text = value, color = valueColor, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun TypingRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(36.dp)
                .clip(CircleShape)
                .background(Color(0xFF2D5C92)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🌊")
        }

        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 20.dp, bottomStart = 20.dp, bottomEnd = 20.dp))
                .background(Color.White)
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
    onSend: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(Color.White.copy(alpha = 0.9f))
            .padding(horizontal = 10.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "🎤")
        TextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = { Text(text = "发送消息给 Nanami🌊...", color = WarmBrownMuted) },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color(0xFFF3F4F6),
                unfocusedContainerColor = Color(0xFFF3F4F6),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp)),
        )
        Text(text = "🖼️", color = WarmBrown.copy(alpha = 0.4f))
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(brush = Brush.linearGradient(listOf(MintGreen, Color(0xFF88D4B4))))
                .clickable { onSend() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "➤", color = Color.White)
        }
    }
}
