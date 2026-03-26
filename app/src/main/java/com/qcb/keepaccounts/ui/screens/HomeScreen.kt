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
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.MintGreenSoft
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonPink
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel

private data class ActivityRecord(
    val icon: String,
    val category: String,
    val desc: String,
    val time: String,
    val amount: String,
    val isIncome: Boolean = false,
)

private data class DaySection(
    val title: String,
    val summary: String,
    val records: List<ActivityRecord>,
)

private val demoSections = listOf(
    DaySection(
        title = "今天 03月26日",
        summary = "支出 ¥58.50",
        records = listOf(
            ActivityRecord(icon = "☕", category = "餐饮美食", desc = "星巴克拿铁", time = "12:30", amount = "-¥ 30.00"),
            ActivityRecord(icon = "🚗", category = "交通出行", desc = "滴滴出行", time = "09:15", amount = "-¥ 28.50"),
        ),
    ),
    DaySection(
        title = "昨天 03月25日",
        summary = "收入 ¥155.00",
        records = listOf(
            ActivityRecord(icon = "🍰", category = "餐饮美食", desc = "好利来蛋糕", time = "18:20", amount = "-¥ 45.00"),
            ActivityRecord(icon = "💰", category = "兼职收入", desc = "画稿尾款", time = "15:00", amount = "+¥ 200.00", isIncome = true),
        ),
    ),
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { HomeHeader() }
        item { BudgetCard() }
        item { ActionButtons() }
        item { RecentHeader() }

        items(demoSections) { section ->
            DaySectionCard(section)
        }
    }
}

@Composable
private fun HomeHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .glassCard(shape = CircleShape, glowColor = MintGreen.copy(alpha = 0.25f))
                    .background(
                        brush = Brush.radialGradient(
                            listOf(Color.White.copy(alpha = 0.95f), MintGreenSoft),
                        ),
                        shape = CircleShape,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "🌊")
            }
            Column {
                Text(text = "Nanami", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                Text(text = "营业中 ✨", color = WarmBrown.copy(alpha = 0.6f), fontWeight = FontWeight.Medium)
            }
        }

        Box(
            modifier = Modifier
                .size(44.dp)
                .glassCard(shape = CircleShape, glowColor = MintGreen.copy(alpha = 0.14f))
                .clickable { },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = "🔍")
        }
    }
}

@Composable
private fun BudgetCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(32.dp), glowColor = MintGreen.copy(alpha = 0.25f))
            .padding(18.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(130.dp)
                .blur(26.dp)
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
                Column {
                    Text(text = "本月预算 🎯", color = WarmBrown.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
                    Text(text = "¥2,450.00", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "剩余", color = WarmBrown.copy(alpha = 0.55f), fontWeight = FontWeight.Medium)
                    Text(text = "¥ 1,550.00", color = MintGreen, fontWeight = FontWeight.Bold)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(10.dp)
                    .background(color = Color.White.copy(alpha = 0.65f), shape = RoundedCornerShape(999.dp)),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(0.36f)
                        .height(10.dp)
                        .background(
                            brush = Brush.horizontalGradient(listOf(WatermelonRed, WatermelonPink)),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(text = "已用 36%", color = WarmBrown.copy(alpha = 0.55f), fontWeight = FontWeight.Medium)
                Text(text = "总计 ¥4,000", color = WarmBrown.copy(alpha = 0.55f), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ActionButtons() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = "💬",
            text = "跟 Nanami 记账",
            brush = Brush.linearGradient(listOf(MintGreen.copy(alpha = 0.9f), MintGreenSoft.copy(alpha = 0.9f))),
        )
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = "✍️",
            text = "手动记一笔",
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.85f))),
        )
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: String,
    text: String,
    brush: Brush,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.96f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "actionScale",
    )

    Column(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .glassCard(shape = RoundedCornerShape(28.dp), glowColor = MintGreen.copy(alpha = 0.22f))
            .background(brush = brush, shape = RoundedCornerShape(28.dp))
            .clickable(interactionSource = interaction, indication = null) { }
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = icon)
        Text(text = text, color = WarmBrown, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RecentHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "最近动态 🍃", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
        Text(text = "查看全部", color = WarmBrown.copy(alpha = 0.5f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun DaySectionCard(section: DaySection) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .background(Color.White.copy(alpha = 0.55f), shape = RoundedCornerShape(999.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
            ) {
                Text(text = section.title, color = WarmBrown.copy(alpha = 0.65f), fontWeight = FontWeight.Bold)
            }
            Text(text = section.summary, color = WarmBrown.copy(alpha = 0.55f), fontWeight = FontWeight.Bold)
        }

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            section.records.forEach { record ->
                ActivityItem(record)
            }
        }
    }
}

@Composable
private fun ActivityItem(record: ActivityRecord) {
    val amountColor = if (record.isIncome) MintGreen else WatermelonRed

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp), glowColor = MintGreen.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .glassCard(shape = RoundedCornerShape(16.dp), glowColor = Color.Transparent)
                    .background(
                        brush = Brush.linearGradient(listOf(Color.White, Color(0xFFF0FDF4))),
                        shape = RoundedCornerShape(16.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = record.icon)
            }

            Column {
                Text(text = record.category, color = WarmBrown, fontWeight = FontWeight.Bold)
                Text(
                    text = "${record.desc} · ${record.time}",
                    color = WarmBrownMuted,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Text(text = record.amount, color = amountColor, fontWeight = FontWeight.ExtraBold)
    }
}
