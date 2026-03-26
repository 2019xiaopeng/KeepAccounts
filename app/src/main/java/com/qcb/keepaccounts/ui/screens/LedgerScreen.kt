package com.qcb.keepaccounts.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.PeachIncome
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonPink
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel

private data class CalendarCell(
    val date: Int,
    val income: Int,
    val expense: Int,
)

private data class RankItem(
    val id: Int,
    val icon: String,
    val name: String,
    val amount: Int,
    val percent: Int,
    val color: Color,
)

private val rankItems = listOf(
    RankItem(1, "☕", "餐饮美食", 1250, 45, WatermelonRed),
    RankItem(2, "🚗", "交通出行", 580, 21, MintGreen),
    RankItem(3, "🛍️", "购物消费", 450, 16, Color(0xFFFFD3B6)),
    RankItem(4, "🏠", "居家生活", 320, 12, Color(0xFFDCEDC1)),
    RankItem(5, "🎮", "娱乐休闲", 180, 6, Color(0xFFFFAAA5)),
)

@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    var viewMode by remember { mutableStateOf("stats") }
    var statsPeriod by remember { mutableStateOf("monthly") }
    var selectedDate by remember { mutableIntStateOf(15) }

    val calendarData = remember {
        List(31) { index ->
            val d = index + 1
            CalendarCell(
                date = d,
                income = if (d % 7 == 0 || d % 11 == 0) 80 + d * 3 else 0,
                expense = if (d % 2 == 0 || d % 5 == 0) 20 + d * 2 else 0,
            )
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            SegmentedToggle(
                leftText = "📅 日历记账",
                rightText = "📊 统计报表",
                leftSelected = viewMode == "calendar",
                onLeftClick = { viewMode = "calendar" },
                onRightClick = { viewMode = "stats" },
            )
        }

        item {
            AnimatedContent(
                targetState = viewMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(220)) togetherWith
                        fadeOut(animationSpec = tween(180))
                },
                label = "ledgerMode",
            ) { mode ->
                if (mode == "calendar") {
                    CalendarMode(
                        data = calendarData,
                        selectedDate = selectedDate,
                        onSelectDate = { selectedDate = it },
                    )
                } else {
                    StatsMode(
                        statsPeriod = statsPeriod,
                        onStatsPeriodChange = { statsPeriod = it },
                    )
                }
            }
        }

        item { Spacer(modifier = Modifier.height(88.dp)) }
    }
}

@Composable
private fun CalendarMode(
    data: List<CalendarCell>,
    selectedDate: Int,
    onSelectDate: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(32.dp), glowColor = MintGreen.copy(alpha = 0.22f))
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(text = "◀", color = WarmBrown.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                    Text(text = "2026年 3月", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                    Text(text = "▶", color = WarmBrown.copy(alpha = 0.6f), fontWeight = FontWeight.Bold)
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("日", "一", "二", "三", "四", "五", "六").forEach { day ->
                        Text(text = day, color = WarmBrown.copy(alpha = 0.42f), fontWeight = FontWeight.Bold)
                    }
                }

                val rows = data.chunked(7)
                rows.forEach { row ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        row.forEach { cell ->
                            val selected = selectedDate == cell.date
                            Column(
                                modifier = Modifier
                                    .width(42.dp)
                                    .background(
                                        color = if (selected) MintGreen.copy(alpha = 0.22f) else Color.Transparent,
                                        shape = RoundedCornerShape(16.dp),
                                    )
                                    .clickable { onSelectDate(cell.date) }
                                    .padding(vertical = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(1.dp),
                            ) {
                                Text(
                                    text = cell.date.toString(),
                                    color = if (selected) WarmBrown else WarmBrown.copy(alpha = 0.8f),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (cell.income > 0) "+${cell.income}" else " ",
                                    color = Color(0xFF22C55E),
                                    fontWeight = FontWeight.Bold,
                                )
                                Text(
                                    text = if (cell.expense > 0) "-${cell.expense}" else " ",
                                    color = WatermelonRed,
                                    fontWeight = FontWeight.Bold,
                                )
                            }
                        }
                        if (row.size < 7) {
                            repeat(7 - row.size) {
                                Spacer(modifier = Modifier.width(42.dp))
                            }
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .glassCard(shape = RoundedCornerShape(28.dp), glowColor = MintGreen.copy(alpha = 0.16f))
                .padding(14.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(text = "3月${selectedDate}日 明细", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                    Text(text = "共 3 笔", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                }
                DailyItem(icon = "☕", name = "星巴克", time = "12:30", amount = "-¥30.00")
                DailyItem(icon = "🚗", name = "打车", time = "09:15", amount = "-¥28.50")
                DailyItem(icon = "💰", name = "稿费", time = "15:00", amount = "+¥120.00", isIncome = true)
            }
        }
    }
}

@Composable
private fun DailyItem(
    icon: String,
    name: String,
    time: String,
    amount: String,
    isIncome: Boolean = false,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = icon)
            }
            Column {
                Text(text = name, color = WarmBrown, fontWeight = FontWeight.Bold)
                Text(text = time, color = WarmBrownMuted)
            }
        }
        Text(
            text = amount,
            color = if (isIncome) MintGreen else WatermelonRed,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}

@Composable
private fun StatsMode(
    statsPeriod: String,
    onStatsPeriodChange: (String) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SegmentedToggle(
            leftText = "月度",
            rightText = "年度",
            leftSelected = statsPeriod == "monthly",
            onLeftClick = { onStatsPeriodChange("monthly") },
            onRightClick = { onStatsPeriodChange("yearly") },
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MiniArrow("<")
            Text(text = "2026年 3月 🌿", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
            MiniArrow(">")
        }

        OverviewCard()
        TrendCard()
        DonutCard()
        RankingCard()
    }
}

@Composable
private fun MiniArrow(text: String) {
    Box(
        modifier = Modifier
            .size(30.dp)
            .glassCard(shape = RoundedCornerShape(999.dp), glowColor = MintGreen.copy(alpha = 0.1f))
            .background(Color.White.copy(alpha = 0.65f), CircleShape)
            .clickable { },
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = WarmBrown.copy(alpha = 0.7f), fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun OverviewCard() {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp), glowColor = MintGreen.copy(alpha = 0.2f))
            .padding(horizontal = 10.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        OverviewItem("总支出", "4798.25", WatermelonRed)
        VerticalLine()
        OverviewItem("总收入", "11228.59", PeachIncome)
        VerticalLine()
        OverviewItem("结余", "6430.34", WarmBrown)
    }
}

@Composable
private fun VerticalLine() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(36.dp)
            .background(WarmBrown.copy(alpha = 0.12f)),
    )
}

@Composable
private fun OverviewItem(title: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = title, color = WarmBrownMuted, fontWeight = FontWeight.Bold)
        Text(text = value, color = color, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun TrendCard() {
    val chartValues = remember {
        listOf(100f, 105f, 95f, 60f, 60f, 10f, 85f, 88f, 90f, 50f, 70f, 85f, 60f, 60f)
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(32.dp), glowColor = MintGreen.copy(alpha = 0.2f))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "每日趋势 📈", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(999.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    StatChip("支出", true)
                    StatChip("收入", false)
                    StatChip("结余", false)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp)
                    .background(Color.White.copy(alpha = 0.18f), RoundedCornerShape(18.dp))
                    .padding(8.dp),
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val maxY = 110f
                    val minY = 0f
                    val width = size.width
                    val height = size.height
                    val stepX = if (chartValues.size > 1) width / (chartValues.size - 1) else 0f

                    // grid
                    repeat(5) { i ->
                        val y = height * i / 4f
                        drawLine(
                            color = WarmBrown.copy(alpha = 0.12f),
                            start = Offset(0f, y),
                            end = Offset(width, y),
                            strokeWidth = 1f,
                        )
                    }

                    val path = Path()
                    chartValues.forEachIndexed { index, value ->
                        val x = stepX * index
                        val yRatio = (value - minY) / (maxY - minY)
                        val y = height - yRatio * height
                        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                    }

                    drawPath(
                        path = path,
                        brush = Brush.horizontalGradient(listOf(WatermelonRed, WatermelonPink)),
                        style = Stroke(width = 4f, cap = StrokeCap.Round),
                    )
                }
            }
        }
    }
}

@Composable
private fun StatChip(text: String, selected: Boolean) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) PeachIncome else Color.Transparent,
                shape = RoundedCornerShape(999.dp),
            )
            .padding(horizontal = 10.dp, vertical = 4.dp),
    ) {
        Text(
            text = text,
            color = if (selected) Color.White else WarmBrown.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun DonutCard() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(32.dp), glowColor = MintGreen.copy(alpha = 0.18f))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(text = "分类排行榜 🍰", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
            }

            Box(contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(180.dp)) {
                    val stroke = Stroke(width = 26f, cap = StrokeCap.Round)
                    val diameter = size.minDimension
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)

                    drawArc(
                        color = Color(0xFFF0FDF4),
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = stroke,
                    )

                    var start = -90f
                    val parts = listOf(
                        45f to WatermelonRed,
                        21f to MintGreen,
                        16f to Color(0xFFFFD3B6),
                        12f to Color(0xFFDCEDC1),
                    )
                    parts.forEach { (percent, color) ->
                        val sweep = 360f * percent / 100f
                        drawArc(
                            color = color,
                            startAngle = start,
                            sweepAngle = sweep,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = stroke,
                        )
                        start += sweep
                    }
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(text = "总支出", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                    Text(text = "4798.25", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                }
            }
        }
    }
}

@Composable
private fun RankingCard() {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(28.dp), glowColor = MintGreen.copy(alpha = 0.16f))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rankItems.forEach { item ->
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.7f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(text = item.icon)
                        }
                        Text(text = item.name, color = WarmBrown, fontWeight = FontWeight.Bold)
                    }
                    Column(horizontalAlignment = Alignment.End) {
                        Text(text = "¥${item.amount}", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                        Text(text = "${item.percent}%", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                    }
                }

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(7.dp)
                        .background(Color(0xFFF3F4F6), RoundedCornerShape(999.dp)),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(item.percent / 100f)
                            .height(7.dp)
                            .background(item.color, RoundedCornerShape(999.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun SegmentedToggle(
    leftText: String,
    rightText: String,
    leftSelected: Boolean,
    onLeftClick: () -> Unit,
    onRightClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(Color.White.copy(alpha = 0.58f), RoundedCornerShape(999.dp))
            .padding(4.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .background(
                    color = if (leftSelected) Color.White else Color.Transparent,
                    shape = RoundedCornerShape(999.dp),
                )
                .clickable { onLeftClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = leftText,
                color = if (leftSelected) WarmBrown else WarmBrown.copy(alpha = 0.56f),
                fontWeight = FontWeight.Bold,
            )
        }
        Box(
            modifier = Modifier
                .background(
                    color = if (leftSelected) Color.Transparent else Color.White,
                    shape = RoundedCornerShape(999.dp),
                )
                .clickable { onRightClick() }
                .padding(horizontal = 14.dp, vertical = 8.dp),
        ) {
            Text(
                text = rightText,
                color = if (leftSelected) WarmBrown.copy(alpha = 0.56f) else WarmBrown,
                fontWeight = FontWeight.Bold,
            )
        }
    }
}
