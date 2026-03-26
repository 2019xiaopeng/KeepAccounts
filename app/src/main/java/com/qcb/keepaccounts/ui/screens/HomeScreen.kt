package com.qcb.keepaccounts.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.MintGreenSoft
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonPink
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val monthlyBudget = 4000.0
    val expense = transactions.filter { it.type == 0 }.sumOf { it.amount }
    val remaining = (monthlyBudget - expense).coerceAtLeast(0.0)
    val progress = if (monthlyBudget == 0.0) 0f else (expense / monthlyBudget).toFloat().coerceIn(0f, 1f)

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HomeHeader() }

        item {
            BudgetCard(
                monthlyBudget = monthlyBudget,
                expense = expense,
                remaining = remaining,
                progress = progress,
            )
        }

        item { QuickActionRow() }

        item {
            Text(
                text = "最近动态 🍃",
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        if (transactions.isEmpty()) {
            item { EmptyHintCard() }
        } else {
            items(items = transactions, key = { it.id }) { transaction ->
                ActivityCard(transaction = transaction)
            }
        }

        item { Spacer(modifier = Modifier.height(84.dp)) }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .glassCard(shape = CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(Color.White.copy(alpha = 0.95f), MintGreenSoft),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🌊")
            }

            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(text = "Nanami", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                Text(text = "营业中 ✨", color = WarmBrownMuted, fontWeight = FontWeight.Medium)
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .glassCard(shape = CircleShape)
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔍")
        }
    }
}

@Composable
private fun BudgetCard(
    monthlyBudget: Double,
    expense: Double,
    remaining: Double,
    progress: Float,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(32.dp))
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(120.dp)
                .blur(30.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(MintGreen.copy(alpha = 0.65f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(text = "本月预算 🎯", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                    Text(
                        text = "¥${"%.2f".format(monthlyBudget)}",
                        color = WarmBrown,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(text = "剩余", color = WarmBrownMuted, fontWeight = FontWeight.Medium)
                    Text(
                        text = "¥${"%.2f".format(remaining)}",
                        color = MintGreen,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(11.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(Color.White.copy(alpha = 0.7f)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .height(11.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(WatermelonRed, WatermelonPink),
                            ),
                        ),
                )
            }

            Text(
                text = "已用 ${(progress * 100f).roundToInt()}% · 支出 ¥${"%.2f".format(expense)}",
                color = WarmBrownMuted,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}

@Composable
private fun QuickActionRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionPillButton(
            modifier = Modifier.weight(1f),
            icon = "💬",
            text = "跟 Nanami 记账",
            gradient = Brush.linearGradient(listOf(MintGreen.copy(alpha = 0.9f), MintGreenSoft.copy(alpha = 0.9f))),
        )

        ActionPillButton(
            modifier = Modifier.weight(1f),
            icon = "✍️",
            text = "手动记一笔",
            gradient = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color(0xFFF6FFF9))),
        )
    }
}

@Composable
private fun ActionPillButton(
    modifier: Modifier,
    icon: String,
    text: String,
    gradient: Brush,
) {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.6f, stiffness = 380f),
        label = "pillScale",
    )

    Column(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .glassCard(shape = RoundedCornerShape(28.dp), glowColor = MintGreen.copy(alpha = 0.3f))
            .background(brush = gradient, shape = RoundedCornerShape(28.dp))
            .clickable(interactionSource = interactionSource, indication = null) { }
            .padding(vertical = 14.dp, horizontal = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = icon)
        Text(text = text, color = WarmBrown, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun EmptyHintCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp))
            .padding(16.dp),
    ) {
        Text(text = "还没有账单，正在准备样例数据...", color = WarmBrownMuted)
    }
}

@Composable
private fun ActivityCard(transaction: TransactionEntity) {
    val isIncome = transaction.type == 1
    val amountPrefix = if (isIncome) "+" else "-"
    val amountColor = if (isIncome) MintGreen else WatermelonRed

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp), glowColor = MintGreen.copy(alpha = 0.22f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.7f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text = transaction.categoryIcon)
                    }
                    Column {
                        Text(text = transaction.categoryName, color = WarmBrown, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatTime(transaction.recordTimestamp),
                            color = WarmBrownMuted,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Text(
                    text = "$amountPrefix¥${"%.2f".format(transaction.amount)}",
                    color = amountColor,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            if (transaction.remark.isNotBlank()) {
                HorizontalDivider(color = WarmBrown.copy(alpha = 0.1f))
                Text(text = transaction.remark, color = WarmBrownMuted, fontWeight = FontWeight.Medium)
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    val localTime = Instant.ofEpochMilli(timestamp).atZone(ZoneId.systemDefault()).toLocalTime()
    return localTime.format(timeFormatter)
}
