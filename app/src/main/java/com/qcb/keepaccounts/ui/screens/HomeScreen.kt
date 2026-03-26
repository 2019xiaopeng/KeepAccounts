package com.qcb.keepaccounts.ui.screens

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
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
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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
        title = "今天 ${todayMMDD()}",
        summary = "支出 ¥58.50",
        records = listOf(
            ActivityRecord(icon = "☕", category = "餐饮美食", desc = "星巴克拿铁", time = "12:30", amount = "-¥ 30.00"),
            ActivityRecord(icon = "🚗", category = "交通出行", desc = "滴滴出行", time = "09:15", amount = "-¥ 28.50"),
        ),
    ),
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    onAiRecordClick: () -> Unit,
    onManualRecordClick: () -> Unit,
    onViewAllClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var searchExpanded by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val sourceSections = remember(transactions) {
        val mapped = mapTransactionsToSections(transactions)
        if (mapped.isEmpty()) demoSections else mapped
    }

    val sections = remember(sourceSections, searchQuery) {
        if (searchQuery.isBlank()) {
            sourceSections
        } else {
            val key = searchQuery.trim().lowercase(Locale.getDefault())
            sourceSections.mapNotNull { section ->
                val filteredRecords = section.records.filter { record ->
                    record.category.lowercase(Locale.getDefault()).contains(key) ||
                        record.desc.lowercase(Locale.getDefault()).contains(key) ||
                        record.time.lowercase(Locale.getDefault()).contains(key)
                }
                if (filteredRecords.isNotEmpty()) {
                    section.copy(records = filteredRecords)
                } else {
                    null
                }
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 96.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HomeHeader(
                searchExpanded = searchExpanded,
                onToggleSearch = {
                    searchExpanded = !searchExpanded
                    if (!searchExpanded) {
                        searchQuery = ""
                    }
                },
            )
        }

        item {
            AnimatedVisibility(visible = searchExpanded) {
                SearchBar(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    onClear = { searchQuery = "" },
                )
            }
        }

        item {
            BudgetCard(transactions = transactions)
        }

        item {
            ActionButtons(
                onAiRecordClick = onAiRecordClick,
                onManualRecordClick = onManualRecordClick,
            )
        }

        item {
            RecentHeader(onViewAllClick = onViewAllClick)
        }

        if (sections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(24.dp), glowColor = MintGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(text = "没有找到相关账单，试试其他关键词", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                }
            }
        } else {
            items(sections) { section ->
                DaySectionCard(section)
            }
        }
    }
}

@Composable
private fun HomeHeader(
    searchExpanded: Boolean,
    onToggleSearch: () -> Unit,
) {
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
                .clickable { onToggleSearch() },
            contentAlignment = Alignment.Center,
        ) {
            Text(text = if (searchExpanded) "✖" else "🔍")
        }
    }
}

@Composable
private fun SearchBar(
    value: String,
    onValueChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.15f))
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(text = "🔎")
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.weight(1f),
            placeholder = {
                Text(text = "搜索账单、备注、分类", color = WarmBrownMuted)
            },
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                disabledContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
            singleLine = true,
        )
        if (value.isNotBlank()) {
            Text(
                text = "清空",
                color = WarmBrown.copy(alpha = 0.65f),
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onClear() },
            )
        }
    }
}

@Composable
private fun BudgetCard(transactions: List<TransactionEntity>) {
    val monthExpense = remember(transactions) {
        val now = Calendar.getInstance()
        val year = now.get(Calendar.YEAR)
        val month = now.get(Calendar.MONTH)
        transactions.filter { tx ->
            if (tx.type != 0) return@filter false
            val cal = Calendar.getInstance().apply { timeInMillis = tx.recordTimestamp }
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        }.sumOf { it.amount }
    }

    val budgetTotal = 4000.0
    val usedRatio = (monthExpense / budgetTotal).coerceIn(0.0, 1.0)
    val remain = (budgetTotal - monthExpense).coerceAtLeast(0.0)

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
                    Text(
                        text = "¥${money(monthExpense)}",
                        color = WarmBrown,
                        fontWeight = FontWeight.ExtraBold,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(text = "剩余", color = WarmBrown.copy(alpha = 0.55f), fontWeight = FontWeight.Medium)
                    Text(text = "¥ ${money(remain)}", color = MintGreen, fontWeight = FontWeight.Bold)
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
                        .fillMaxWidth(usedRatio.toFloat())
                        .height(10.dp)
                        .background(
                            brush = Brush.horizontalGradient(listOf(WatermelonRed, WatermelonPink)),
                            shape = RoundedCornerShape(999.dp),
                        ),
                )
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    text = "已用 ${(usedRatio * 100).toInt()}%",
                    color = WarmBrown.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Medium,
                )
                Text(text = "总计 ¥${money(budgetTotal)}", color = WarmBrown.copy(alpha = 0.55f), fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onAiRecordClick: () -> Unit,
    onManualRecordClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = "💬",
            text = "跟 Nanami 记账",
            brush = Brush.linearGradient(listOf(MintGreen.copy(alpha = 0.9f), MintGreenSoft.copy(alpha = 0.9f))),
            onClick = onAiRecordClick,
        )
        ActionButton(
            modifier = Modifier.weight(1f),
            icon = "✍️",
            text = "手动记一笔",
            brush = Brush.linearGradient(listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.85f))),
            onClick = onManualRecordClick,
        )
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    icon: String,
    text: String,
    brush: Brush,
    onClick: () -> Unit,
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
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 14.dp, horizontal = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = icon)
        Text(text = text, color = WarmBrown, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun RecentHeader(onViewAllClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "最近动态 🍃", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
        Text(
            text = "查看全部",
            color = WarmBrown.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
            modifier = Modifier.clickable { onViewAllClick() },
        )
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

private fun mapTransactionsToSections(transactions: List<TransactionEntity>): List<DaySection> {
    if (transactions.isEmpty()) return emptyList()

    val dayKeyFormat = SimpleDateFormat("yyyyMMdd", Locale.CHINA)
    val dayDisplayFormat = SimpleDateFormat("MM月dd日", Locale.CHINA)
    val timeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)

    val grouped = transactions.groupBy { dayKeyFormat.format(Date(it.recordTimestamp)) }
        .toSortedMap(reverseOrder())

    val todayKey = dayKeyFormat.format(Date())
    val yesterdayCalendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, -1) }
    val yesterdayKey = dayKeyFormat.format(yesterdayCalendar.time)

    return grouped.map { (dayKey, list) ->
        val dayDate = Date(list.first().recordTimestamp)
        val title = when (dayKey) {
            todayKey -> "今天 ${dayDisplayFormat.format(dayDate)}"
            yesterdayKey -> "昨天 ${dayDisplayFormat.format(dayDate)}"
            else -> dayDisplayFormat.format(dayDate)
        }

        val expense = list.filter { it.type == 0 }.sumOf { it.amount }
        val income = list.filter { it.type == 1 }.sumOf { it.amount }
        val summary = if (income > expense) {
            "收入 ¥${money(income)}"
        } else {
            "支出 ¥${money(expense)}"
        }

        val records = list.sortedByDescending { it.recordTimestamp }.map { tx ->
            val isIncome = tx.type == 1
            ActivityRecord(
                icon = tx.categoryIcon,
                category = tx.categoryName,
                desc = tx.remark,
                time = timeFormat.format(Date(tx.recordTimestamp)),
                amount = (if (isIncome) "+¥ " else "-¥ ") + money(tx.amount),
                isIncome = isIncome,
            )
        }

        DaySection(title = title, summary = summary, records = records)
    }
}

private fun money(value: Double): String {
    return String.format(Locale.CHINA, "%,.2f", value)
}

private fun todayMMDD(): String {
    return SimpleDateFormat("MM月dd日", Locale.CHINA).format(Date())
}
