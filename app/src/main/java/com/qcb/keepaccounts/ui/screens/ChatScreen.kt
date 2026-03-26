package com.qcb.keepaccounts.ui.screens

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
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted

data class ChatBubbleModel(
    val id: Long,
    val role: String,
    val content: String,
)

private val demoMessages = listOf(
    ChatBubbleModel(1, "user", "今天中午牛肉粉丝汤 22 元"),
    ChatBubbleModel(2, "assistant", "已经帮你记下啦，今天也要记得喝水哦。"),
    ChatBubbleModel(3, "assistant", "如果下午有咖啡，也可以一起记给我。"),
)

@Composable
fun ChatScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFFDDF7EA),
                        Color(0xFFCCEFE2),
                        Color(0xFFEFF8F4),
                    ),
                ),
            ),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(180.dp)
                .alpha(0.35f)
                .background(
                    brush = Brush.radialGradient(listOf(MintGreen, Color.Transparent)),
                    shape = CircleShape,
                ),
        )

        Column(modifier = Modifier.fillMaxSize()) {
            ChatTopBar()

            LazyColumn(
                modifier = Modifier.weight(1f),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(demoMessages, key = { it.id }) { msg ->
                    ChatBubbleItem(message = msg)
                }
                item {
                    TypingBubble()
                }
                item { Spacer(modifier = Modifier.height(74.dp)) }
            }
        }

        InputBar(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        )
    }
}

@Composable
private fun ChatTopBar() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp, vertical = 10.dp)
            .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.2f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "←", color = WarmBrown, fontWeight = FontWeight.Bold)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "Nanami🌊", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
            Text(text = "今天 16:51", color = WarmBrownMuted)
        }
        Text(text = "⋯", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ChatBubbleItem(message: ChatBubbleModel) {
    val isUser = message.role == "user"
    val bubbleShape = if (isUser) {
        RoundedCornerShape(topStart = 24.dp, topEnd = 6.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
    } else {
        RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp)
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
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.82f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🌊")
            }
        }

        Box(
            modifier = Modifier
                .shadow(6.dp, bubbleShape, spotColor = MintGreen.copy(alpha = 0.2f))
                .clip(bubbleShape)
                .background(if (isUser) MintGreen.copy(alpha = 0.95f) else Color.White.copy(alpha = 0.93f))
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            Text(text = message.content, color = WarmBrown, fontWeight = FontWeight.Medium)
        }
    }
}

@Composable
private fun TypingBubble() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            modifier = Modifier
                .padding(end = 8.dp)
                .size(34.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.82f)),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🌊")
        }

        Box(
            modifier = Modifier
                .shadow(6.dp, RoundedCornerShape(24.dp), spotColor = MintGreen.copy(alpha = 0.22f))
                .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 24.dp, bottomStart = 24.dp, bottomEnd = 24.dp))
                .background(Color.White.copy(alpha = 0.93f))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            JumpingDots()
        }
    }
}

@Composable
private fun JumpingDots() {
    val infinite = rememberInfiniteTransition(label = "typing")
    val a = infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "a",
    )
    val b = infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, delayMillis = 120, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "b",
    )
    val c = infinite.animateFloat(
        initialValue = 0.35f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(650, delayMillis = 240, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "c",
    )

    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
        Dot(alpha = a.value)
        Dot(alpha = b.value)
        Dot(alpha = c.value)
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
private fun InputBar(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .glassCard(shape = RoundedCornerShape(26.dp), glowColor = MintGreen.copy(alpha = 0.22f))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(text = "🎤")
        Box(
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(999.dp))
                .background(Color.White.copy(alpha = 0.74f))
                .padding(horizontal = 12.dp, vertical = 8.dp),
        ) {
            Text(text = "发送消息给 Nanami🌊...", color = WarmBrownMuted)
        }
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(brush = Brush.linearGradient(listOf(MintGreen, Color(0xFF88D4B4)))),
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "➤", color = Color.White)
        }
    }
}
