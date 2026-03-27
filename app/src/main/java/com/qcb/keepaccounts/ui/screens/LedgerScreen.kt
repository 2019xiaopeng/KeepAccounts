package com.qcb.keepaccounts.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.PieChart
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.icons.resolveCategoryIcon
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.PeachIncome
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.random.Random

private data class DayCell(
    val day: Int?,
    val income: Double = 0.0,
    val expense: Double = 0.0,
)

private data class CategoryStat(
    val name: String,
    val amount: Double,
    val percent: Int,
    val color: Color,
    val icon: ImageVector,
)

private enum class LedgerViewMode { CALENDAR, STATS }
private enum class StatsPeriod { MONTH, YEAR }
private enum class TrendMetric { EXPENSE, INCOME, BALANCE }
private enum class RankType { EXPENSE, INCOME }
private enum class RecordSortMode { TIME, AMOUNT }

private data class MockRecord(
    val id: Int,
    val category: String,
    val remark: String,
    val amount: Double,
    val timestamp: Long,
    val isIncome: Boolean,
)

@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    onEditRecord: (ManualEntryPrefill) -> Unit = {},
    onDeleteRecord: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    var viewMode by rememberSaveable { mutableStateOf(LedgerViewMode.CALENDAR) }
    var statsPeriod by rememberSaveable { mutableStateOf(StatsPeriod.MONTH) }
    var trendMetric by rememberSaveable { mutableStateOf(TrendMetric.EXPENSE) }
    var rankType by rememberSaveable { mutableStateOf(RankType.EXPENSE) }
    var recordSortMode by rememberSaveable { mutableStateOf(RecordSortMode.TIME) }
    var currentPage by rememberSaveable { mutableIntStateOf(0) }
    var selectedDay by rememberSaveable { mutableIntStateOf(Calendar.getInstance().get(Calendar.DAY_OF_MONTH)) }
    var expandedRecordId by rememberSaveable { mutableLongStateOf(-1L) }
    var confirmDeleteId by rememberSaveable { mutableLongStateOf(-1L) }
    val currentDate = remember { Calendar.getInstance() }
    var dateVersion by rememberSaveable { mutableIntStateOf(0) }

    val month = currentDate.get(Calendar.MONTH)
    val year = currentDate.get(Calendar.YEAR)
    val daysInMonth = remember(dateVersion) { Calendar.getInstance().apply { set(year, month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH) }
    if (selectedDay > daysInMonth) selectedDay = daysInMonth

    val monthTransactions = remember(transactions, year, month) {
        transactions.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.recordTimestamp }
            cal.get(Calendar.YEAR) == year && cal.get(Calendar.MONTH) == month
        }
    }
    val yearTransactions = remember(transactions, year) {
        transactions.filter { tx ->
            val cal = Calendar.getInstance().apply { timeInMillis = tx.recordTimestamp }
            cal.get(Calendar.YEAR) == year
        }
    }

    val dailyMap = remember(monthTransactions) {
        monthTransactions.groupBy { Calendar.getInstance().apply { timeInMillis = it.recordTimestamp }.get(Calendar.DAY_OF_MONTH) }
            .mapValues { (_, list) ->
                val expense = list.filter { it.type == 0 }.sumOf { it.amount }
                val income = list.filter { it.type == 1 }.sumOf { it.amount }
                income to expense
            }
    }

    val dayRecords = remember(monthTransactions, selectedDay) {
        monthTransactions.filter {
            val cal = Calendar.getInstance().apply { timeInMillis = it.recordTimestamp }
            cal.get(Calendar.DAY_OF_MONTH) == selectedDay
        }.sortedByDescending { it.recordTimestamp }
    }

    val calendarCells = remember(dateVersion, dailyMap) {
        val firstDayCalendar = Calendar.getInstance().apply { set(year, month, 1) }
        val weekOffset = ((firstDayCalendar.get(Calendar.DAY_OF_WEEK) + 5) % 7)
        val cells = mutableListOf<DayCell>()
        repeat(weekOffset) { cells.add(DayCell(day = null)) }
        for (day in 1..daysInMonth) {
            val values = dailyMap[day]
            cells.add(DayCell(day = day, income = values?.first ?: 0.0, expense = values?.second ?: 0.0))
        }
        while (cells.size % 7 != 0) cells.add(DayCell(day = null))
        cells
    }

    val scopeTransactions = if (statsPeriod == StatsPeriod.MONTH) monthTransactions else yearTransactions
    val totalExpense = scopeTransactions.filter { it.type == 0 }.sumOf { it.amount }
    val totalIncome = scopeTransactions.filter { it.type == 1 }.sumOf { it.amount }
    val totalBalance = totalIncome - totalExpense

    val trendPoints = remember(scopeTransactions, statsPeriod, trendMetric, dateVersion) {
        if (statsPeriod == StatsPeriod.MONTH) {
            (1..daysInMonth).map { day ->
                val list = monthTransactions.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.recordTimestamp }
                    cal.get(Calendar.DAY_OF_MONTH) == day
                }
                metricValue(list, trendMetric).toFloat()
            }
        } else {
            (0..11).map { m ->
                val list = yearTransactions.filter {
                    val cal = Calendar.getInstance().apply { timeInMillis = it.recordTimestamp }
                    cal.get(Calendar.MONTH) == m
                }
                metricValue(list, trendMetric).toFloat()
            }
        }
    }

    val categoryExpense = remember(scopeTransactions) { categoryStats(scopeTransactions.filter { it.type == 0 }) }
    val categoryIncome = remember(scopeTransactions) { categoryStats(scopeTransactions.filter { it.type == 1 }) }
    val rankList = if (rankType == RankType.EXPENSE) categoryExpense else categoryIncome
    val trendXAxisLabels = remember(statsPeriod) {
        if (statsPeriod == StatsPeriod.MONTH) {
            listOf("1日", "5日", "10日", "15日", "20日", "25日")
        } else {
            listOf("1月", "3月", "5月", "7月", "9月", "11月")
        }
    }

    val mockRecords = remember {
        buildMockRecords()
    }
    val sortedMockRecords = remember(mockRecords, recordSortMode) {
        when (recordSortMode) {
            RecordSortMode.TIME -> mockRecords.sortedByDescending { it.timestamp }
            RecordSortMode.AMOUNT -> mockRecords.sortedByDescending { it.amount }
        }
    }
    val pageSize = 10
    val totalPages = remember(sortedMockRecords) { (sortedMockRecords.size + pageSize - 1) / pageSize }
    val safePage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val pageRecords = remember(sortedMockRecords, safePage) {
        sortedMockRecords.drop(safePage * pageSize).take(pageSize)
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "账本 📒",
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 22.sp,
                modifier = Modifier.statusBarsPadding(),
            )
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
            ) {
                Row(
                    modifier = Modifier
                        .glassCard(shape = RoundedCornerShape(999.dp), backgroundColor = Color.White.copy(alpha = 0.56f))
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    TopToggle(
                        selected = viewMode == LedgerViewMode.CALENDAR,
                        iconText = "📅",
                        text = "日历记账",
                    ) { viewMode = LedgerViewMode.CALENDAR }
                    TopToggle(
                        selected = viewMode == LedgerViewMode.STATS,
                        iconText = "📊",
                        text = "统计报表",
                    ) { viewMode = LedgerViewMode.STATS }
                }
            }
        }

        item {
            AnimatedContent(
                targetState = viewMode.ordinal,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth },
                        animationSpec = tween(300),
                    ) + fadeIn(tween(300)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth },
                            animationSpec = tween(300),
                        ) + fadeOut(tween(300))
                },
                label = "ledgerModeSwitch",
            ) { modeIndex ->
                if (modeIndex == LedgerViewMode.CALENDAR.ordinal) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        CalendarPeriodHeader(
                            year = year,
                            month = month,
                            onPrev = {
                                currentDate.add(Calendar.MONTH, -1)
                                dateVersion += 1
                                selectedDay = 1
                            },
                            onNext = {
                                currentDate.add(Calendar.MONTH, 1)
                                dateVersion += 1
                                selectedDay = 1
                            },
                        )

                        CalendarPanel(
                            cells = calendarCells,
                            selectedDay = selectedDay,
                            onSelectDay = { selectedDay = it },
                        )

                        DailyRecordsCard(
                            month = month + 1,
                            day = selectedDay,
                            records = dayRecords,
                            expandedRecordId = expandedRecordId,
                            confirmDeleteId = confirmDeleteId,
                            onToggleExpand = { id ->
                                if (expandedRecordId == id) {
                                    expandedRecordId = -1L
                                    confirmDeleteId = -1L
                                } else {
                                    expandedRecordId = id
                                    confirmDeleteId = -1L
                                }
                            },
                            onEdit = { tx ->
                                val isIncome = tx.type == 1
                                onEditRecord(
                                    ManualEntryPrefill(
                                        type = if (isIncome) "income" else "expense",
                                        category = tx.categoryName,
                                        desc = tx.remark,
                                        amount = String.format(Locale.CHINA, "%.2f", tx.amount),
                                    ),
                                )
                            },
                            onDelete = { id -> onDeleteRecord(id) },
                            onBeginDelete = { id -> confirmDeleteId = id },
                            onCancelDelete = { confirmDeleteId = -1L },
                        )
                    }
                } else {
                    StatsPanel(
                        statsPeriod = statsPeriod,
                        onStatsPeriodChange = {
                            statsPeriod = it
                            currentPage = 0
                        },
                        year = year,
                        month = month,
                        onPrev = {
                            if (statsPeriod == StatsPeriod.MONTH) currentDate.add(Calendar.MONTH, -1) else currentDate.add(Calendar.YEAR, -1)
                            dateVersion += 1
                        },
                        onNext = {
                            if (statsPeriod == StatsPeriod.MONTH) currentDate.add(Calendar.MONTH, 1) else currentDate.add(Calendar.YEAR, 1)
                            dateVersion += 1
                        },
                        totalExpense = totalExpense,
                        totalIncome = totalIncome,
                        totalBalance = totalBalance,
                        trendMetric = trendMetric,
                        onTrendMetricChange = { trendMetric = it },
                        trendPoints = trendPoints,
                        xLabels = trendXAxisLabels,
                        categoryExpense = categoryExpense,
                        rankType = rankType,
                        onRankTypeChange = { rankType = it },
                        rankList = rankList,
                    )
                }
            }
        }

        item {
            MockRecordPagerSection(
                sortMode = recordSortMode,
                onSortModeChange = {
                    recordSortMode = it
                    currentPage = 0
                },
                records = pageRecords,
                page = safePage,
                totalPages = totalPages,
                onPrevPage = {
                    if (safePage > 0) currentPage = safePage - 1
                },
                onNextPage = {
                    if (safePage < totalPages - 1) currentPage = safePage + 1
                },
            )
        }

    }
}

@Composable
private fun TopToggle(
    selected: Boolean,
    iconText: String,
    text: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(
                color = if (selected) MintGreen.copy(alpha = 0.9f) else Color.Transparent,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (iconText.isNotBlank()) {
            Text(text = iconText, fontSize = 12.sp)
            Spacer(modifier = Modifier.width(4.dp))
        }
        Text(
            text = text,
            color = if (selected) WarmBrown else WarmBrown.copy(alpha = 0.62f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun CalendarPeriodHeader(
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        ArrowButton(icon = Icons.Rounded.ChevronLeft, onClick = onPrev)
        Text(text = "${year}年 ${month + 1}月", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 19.sp)
        ArrowButton(icon = Icons.Rounded.ChevronRight, onClick = onNext)
    }
}

@Composable
private fun CalendarPanel(
    cells: List<DayCell>,
    selectedDay: Int,
    onSelectDay: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(24.dp), backgroundColor = Color.White.copy(alpha = 0.72f))
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("一", "二", "三", "四", "五", "六", "日").forEach {
                Text(text = it, color = WarmBrown.copy(alpha = 0.66f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }
        }

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                week.forEach { cell ->
                    if (cell.day == null) {
                        Spacer(
                            modifier = Modifier
                                .width(38.dp)
                                .height(54.dp),
                        )
                    } else {
                        val selected = cell.day == selectedDay
                        DayCellView(
                            cell = cell,
                            selected = selected,
                            onClick = { onSelectDay(cell.day) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DayCellView(
    cell: DayCell,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .width(38.dp)
            .height(54.dp)
            .clickable { onClick() },
        verticalArrangement = Arrangement.spacedBy(2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .background(
                    color = if (selected) Color(0xFFF8A85C) else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.day.toString(),
                color = if (selected) Color.White else WarmBrown,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
            )
        }
        Text(
            text = if (cell.expense > 0) "-${trimNumber(cell.expense)}" else "",
            color = WatermelonRed,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
        Text(
            text = if (cell.income > 0) "+${trimNumber(cell.income)}" else "",
            color = MintGreen,
            fontWeight = FontWeight.Bold,
            fontSize = 10.sp,
        )
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color.White.copy(alpha = 0.9f), CircleShape)
            .clickable { onClick() },
        contentAlignment = Alignment.Center,
    ) {
        Icon(imageVector = icon, contentDescription = null, tint = WarmBrown.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
    }
}

@Composable
private fun DailyRecordsCard(
    month: Int,
    day: Int,
    records: List<TransactionEntity>,
    expandedRecordId: Long,
    confirmDeleteId: Long,
    onToggleExpand: (Long) -> Unit,
    onEdit: (TransactionEntity) -> Unit,
    onDelete: (Long) -> Unit,
    onBeginDelete: (Long) -> Unit,
    onCancelDelete: () -> Unit,
) {
    val dayExpense = records.filter { it.type == 0 }.sumOf { it.amount }
    val dayIncome = records.filter { it.type == 1 }.sumOf { it.amount }
    val headerAmount = if (dayIncome >= dayExpense) "+¥${money(dayIncome)}" else "-¥${money(dayExpense)}"
    val headerColor = if (dayIncome >= dayExpense) MintGreen else WatermelonRed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(28.dp), backgroundColor = Color.White.copy(alpha = 0.78f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(text = "${month}月${day}日 记录", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            Text(text = if (records.isEmpty()) "暂无" else headerAmount, color = headerColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        }

        if (records.isEmpty()) {
            Text(text = "这一天没有记账记录哦", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        } else {
            records.forEach { tx ->
                val isIncome = tx.type == 1
                val expanded = expandedRecordId == tx.id
                val confirming = confirmDeleteId == tx.id
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(22.dp))
                        .clickable { onToggleExpand(tx.id) }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .background(Color.White, CircleShape),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    imageVector = resolveCategoryIcon(tx.categoryName, tx.remark),
                                    contentDescription = tx.categoryName,
                                    tint = WarmBrown,
                                    modifier = Modifier.size(20.dp),
                                )
                            }
                            Column {
                                Text(text = tx.categoryName, color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                                Text(text = formatLedgerTime(tx.recordTimestamp), color = WarmBrownMuted, fontSize = 12.sp)
                            }
                        }
                        Text(
                            text = (if (isIncome) "+¥ " else "-¥ ") + money(tx.amount),
                            color = if (isIncome) MintGreen else WatermelonRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 17.sp,
                        )
                    }

                    if (tx.remark.isNotBlank()) {
                        Text(
                            text = tx.remark,
                            color = WarmBrownMuted,
                            fontWeight = FontWeight.Medium,
                            fontSize = 12.sp,
                        )
                    }

                    if (expanded) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            if (confirming) {
                                Row(
                                    modifier = Modifier
                                        .background(Color(0xFFFFF0F0), RoundedCornerShape(999.dp))
                                        .padding(horizontal = 8.dp, vertical = 4.dp),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text(text = "确认删除?", color = WatermelonRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(Color.White, CircleShape)
                                            .clickable { onCancelDelete() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = "✕", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(WatermelonRed, CircleShape)
                                            .clickable {
                                                onDelete(tx.id)
                                                onCancelDelete()
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = "✓", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                }
                            } else {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "edit",
                                        tint = WarmBrown.copy(alpha = 0.55f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onEdit(tx) },
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "delete",
                                        tint = WatermelonRed.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .clickable { onBeginDelete(tx.id) },
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsPanel(
    statsPeriod: StatsPeriod,
    onStatsPeriodChange: (StatsPeriod) -> Unit,
    year: Int,
    month: Int,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    totalExpense: Double,
    totalIncome: Double,
    totalBalance: Double,
    trendMetric: TrendMetric,
    onTrendMetricChange: (TrendMetric) -> Unit,
    trendPoints: List<Float>,
    xLabels: List<String>,
    categoryExpense: List<CategoryStat>,
    rankType: RankType,
    onRankTypeChange: (RankType) -> Unit,
    rankList: List<CategoryStat>,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
        ) {
            Row(
                modifier = Modifier
                    .glassCard(shape = RoundedCornerShape(999.dp), backgroundColor = Color.White.copy(alpha = 0.7f))
                    .padding(horizontal = 6.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                TopToggle(
                    selected = statsPeriod == StatsPeriod.MONTH,
                    iconText = "",
                    text = "月度",
                ) { onStatsPeriodChange(StatsPeriod.MONTH) }
                TopToggle(
                    selected = statsPeriod == StatsPeriod.YEAR,
                    iconText = "",
                    text = "年度",
                ) { onStatsPeriodChange(StatsPeriod.YEAR) }
            }
        }

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ArrowButton(icon = Icons.Rounded.ChevronLeft, onClick = onPrev)
            AnimatedContent(
                targetState = statsPeriod.ordinal,
                transitionSpec = {
                    slideInHorizontally(
                        initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth },
                        animationSpec = tween(300),
                    ) + fadeIn(tween(300)) togetherWith
                        slideOutHorizontally(
                            targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth },
                            animationSpec = tween(300),
                        ) + fadeOut(tween(300))
                },
                label = "statsPeriodTitle",
            ) { periodIndex ->
                Text(
                    text = if (periodIndex == StatsPeriod.MONTH.ordinal) "${year}年 ${month + 1}月 🍃" else "${year}年 🍃",
                    color = WarmBrown,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 20.sp,
                )
            }
            ArrowButton(icon = Icons.Rounded.ChevronRight, onClick = onNext)
        }

        AnimatedContent(
            targetState = statsPeriod.ordinal,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth },
                    animationSpec = tween(300),
                ) + fadeIn(tween(300)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth },
                        animationSpec = tween(300),
                    ) + fadeOut(tween(300))
            },
            label = "statsBodySwitch",
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(24.dp), backgroundColor = Color.White.copy(alpha = 0.8f))
                        .padding(horizontal = 10.dp, vertical = 14.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    StatsItem("总支出", money(totalExpense), WatermelonRed)
                    DividerV()
                    StatsItem("总收入", money(totalIncome), PeachIncome)
                    DividerV()
                    StatsItem("结余", money(totalBalance), WarmBrown)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(30.dp), backgroundColor = Color.White.copy(alpha = 0.78f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.AutoMirrored.Rounded.ShowChart, contentDescription = null, tint = WarmBrown.copy(alpha = 0.74f), modifier = Modifier.size(16.dp))
                            Text(text = "每日趋势 📈", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        }
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(999.dp))
                                .padding(2.dp),
                        ) {
                            MetricChip("支出", trendMetric == TrendMetric.EXPENSE) { onTrendMetricChange(TrendMetric.EXPENSE) }
                            MetricChip("收入", trendMetric == TrendMetric.INCOME) { onTrendMetricChange(TrendMetric.INCOME) }
                            MetricChip("结余", trendMetric == TrendMetric.BALANCE) { onTrendMetricChange(TrendMetric.BALANCE) }
                        }
                    }
                    AnimatedContent(
                        targetState = trendMetric.ordinal,
                        transitionSpec = {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth },
                                animationSpec = tween(300),
                            ) + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth },
                                    animationSpec = tween(300),
                                ) + fadeOut(tween(300))
                        },
                        label = "trendMetricSwitch",
                    ) {
                        LineChart(points = trendPoints, metric = trendMetric, xLabels = xLabels)
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(30.dp), backgroundColor = Color.White.copy(alpha = 0.78f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Rounded.PieChart, contentDescription = null, tint = WarmBrown.copy(alpha = 0.72f), modifier = Modifier.size(16.dp))
                        Text(text = "分类排行榜 🍰", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
                        DonutChart(categoryExpense = categoryExpense, totalExpense = totalExpense)
                    }
                    categoryExpense.forEach { item ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .background(Color.White, CircleShape),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(imageVector = item.icon, contentDescription = item.name, tint = WarmBrown, modifier = Modifier.size(16.dp))
                                }
                                Text(text = item.name, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "${item.percent}%", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            }
                            Text(text = "¥ ${money(item.amount)}", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(6.dp)
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(999.dp)),
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(item.percent / 100f)
                                    .height(6.dp)
                                    .background(item.color, RoundedCornerShape(999.dp)),
                            )
                        }
                    }
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(28.dp), backgroundColor = Color.White.copy(alpha = 0.76f))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text(text = "明细排行榜 🏆", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                        Row(
                            modifier = Modifier
                                .background(Color(0xFFF3F4F6), RoundedCornerShape(999.dp))
                                .padding(2.dp),
                        ) {
                            MetricChip("支出", rankType == RankType.EXPENSE) { onRankTypeChange(RankType.EXPENSE) }
                            MetricChip("收入", rankType == RankType.INCOME) { onRankTypeChange(RankType.INCOME) }
                        }
                    }
                    AnimatedContent(
                        targetState = rankType.ordinal,
                        transitionSpec = {
                            slideInHorizontally(
                                initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth },
                                animationSpec = tween(300),
                            ) + fadeIn(tween(300)) togetherWith
                                slideOutHorizontally(
                                    targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth },
                                    animationSpec = tween(300),
                                ) + fadeOut(tween(300))
                        },
                        label = "rankTypeSwitch",
                    ) { rankIndex ->
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            rankList.forEach {
                                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                    Text(text = it.name, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                    Text(
                                        text = (if (rankIndex == RankType.EXPENSE.ordinal) "-¥ " else "+¥ ") + money(it.amount),
                                        color = if (rankIndex == RankType.EXPENSE.ordinal) WatermelonRed else MintGreen,
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 15.sp,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StatsItem(label: String, value: String, color: Color) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 12.sp)
        Text(text = value, color = color, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun DividerV() {
    Box(
        modifier = Modifier
            .width(1.dp)
            .height(44.dp)
            .background(WarmBrown.copy(alpha = 0.14f)),
    )
}

@Composable
private fun MetricChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) Color(0xFFF4C86A) else Color.Transparent,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 11.dp, vertical = 5.dp),
    ) {
        Text(
            text = text,
            color = if (selected) WarmBrown else WarmBrown.copy(alpha = 0.56f),
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun LineChart(points: List<Float>, metric: TrendMetric, xLabels: List<String>) {
    val strokeColor = when (metric) {
        TrendMetric.EXPENSE -> WatermelonRed
        TrendMetric.INCOME -> Color(0xFF37A56B)
        TrendMetric.BALANCE -> Color(0xFF5A6EE0)
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.4f), RoundedCornerShape(18.dp))
            .padding(horizontal = 8.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(modifier = Modifier.fillMaxWidth().height(164.dp)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
            repeat(4) { i ->
                val y = size.height * i / 3f
                drawLine(
                    color = WarmBrown.copy(alpha = 0.10f),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                )
            }

            if (points.isNotEmpty()) {
                val maxValue = max(points.maxOrNull() ?: 0f, 1f)
                val stepX = if (points.size > 1) size.width / (points.size - 1) else 0f
                val linePath = Path()
                val fillPath = Path()

                points.forEachIndexed { index, value ->
                    val x = stepX * index
                    val y = size.height - (value / maxValue) * size.height
                    if (index == 0) {
                        linePath.moveTo(x, y)
                        fillPath.moveTo(x, size.height)
                        fillPath.lineTo(x, y)
                    } else {
                        linePath.lineTo(x, y)
                        fillPath.lineTo(x, y)
                    }
                    if (index == points.lastIndex) {
                        fillPath.lineTo(x, size.height)
                        fillPath.close()
                    }
                }

                drawPath(
                    path = fillPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(strokeColor.copy(alpha = 0.24f), Color.Transparent),
                    ),
                )
                drawPath(path = linePath, color = strokeColor, style = Stroke(width = 4f, cap = StrokeCap.Round))
            }
        }
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            xLabels.forEach { label ->
                Text(
                    text = label,
                    color = WarmBrown.copy(alpha = 0.42f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 10.sp,
                )
            }
        }
    }
}

@Composable
private fun DonutChart(categoryExpense: List<CategoryStat>, totalExpense: Double) {
    Box(contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(200.dp)) {
            val stroke = Stroke(width = 24f, cap = StrokeCap.Round)
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

            var startAngle = -90f
            categoryExpense.forEach {
                val sweep = 360f * (it.percent / 100f)
                drawArc(
                    color = it.color,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = stroke,
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = "总支出", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            Text(text = money(totalExpense), color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun MockRecordPagerSection(
    sortMode: RecordSortMode,
    onSortModeChange: (RecordSortMode) -> Unit,
    records: List<MockRecord>,
    page: Int,
    totalPages: Int,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(28.dp), backgroundColor = Color.White.copy(alpha = 0.78f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "账本明细", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SortChip(
                text = "按时间排序",
                selected = sortMode == RecordSortMode.TIME,
                onClick = { onSortModeChange(RecordSortMode.TIME) },
            )
            SortChip(
                text = "按金额排序",
                selected = sortMode == RecordSortMode.AMOUNT,
                onClick = { onSortModeChange(RecordSortMode.AMOUNT) },
            )
        }

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                slideInHorizontally(
                    initialOffsetX = { fullWidth -> if (targetState > initialState) fullWidth else -fullWidth },
                    animationSpec = tween(300),
                ) + fadeIn(tween(300)) togetherWith
                    slideOutHorizontally(
                        targetOffsetX = { fullWidth -> if (targetState > initialState) -fullWidth else fullWidth },
                        animationSpec = tween(300),
                    ) + fadeOut(tween(300))
            },
            label = "mockPageSwitch",
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(380.dp)
                    .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(records, key = { record -> record.id }) { record ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(14.dp))
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(text = record.category, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(text = "${record.remark} · ${formatDate(record.timestamp)}", color = WarmBrownMuted, fontSize = 11.sp)
                        }
                        Text(
                            text = (if (record.isIncome) "+¥ " else "-¥ ") + money(record.amount),
                            color = if (record.isIncome) MintGreen else WatermelonRed,
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 14.sp,
                        )
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaginationButton(text = "上一页", enabled = page > 0, onClick = onPrevPage)
            Text(
                text = "${page + 1} / ${totalPages.coerceAtLeast(1)}",
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            PaginationButton(text = "下一页", enabled = page < totalPages - 1, onClick = onNextPage)
        }
    }
}

@Composable
private fun SortChip(text: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) MintGreen.copy(alpha = 0.24f) else Color.White.copy(alpha = 0.72f),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (selected) WarmBrown else WarmBrown.copy(alpha = 0.6f),
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

@Composable
private fun PaginationButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                color = if (enabled) Color.White.copy(alpha = 0.9f) else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(999.dp),
            )
            .clickable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) WarmBrown else WarmBrownMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}

private fun buildMockRecords(): List<MockRecord> {
    val now = System.currentTimeMillis()
    val categories = listOf("餐饮美食", "交通出行", "购物消费", "居家生活", "娱乐休闲", "医疗健康")
    return List(50) { index ->
        val amount = Random(index + 9).nextDouble(18.0, 580.0)
        val isIncome = index % 7 == 0
        MockRecord(
            id = index + 1,
            category = categories[index % categories.size],
            remark = if (isIncome) "模拟收入${index + 1}" else "模拟支出${index + 1}",
            amount = amount,
            timestamp = now - index * 6L * 60L * 60L * 1000L,
            isIncome = isIncome,
        )
    }
}

private fun formatDate(timestamp: Long): String {
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}

private fun metricValue(list: List<TransactionEntity>, metric: TrendMetric): Double {
    val expense = list.filter { it.type == 0 }.sumOf { it.amount }
    val income = list.filter { it.type == 1 }.sumOf { it.amount }
    return when (metric) {
        TrendMetric.EXPENSE -> expense
        TrendMetric.INCOME -> income
        TrendMetric.BALANCE -> income - expense
    }.coerceAtLeast(0.0)
}

private fun categoryStats(list: List<TransactionEntity>): List<CategoryStat> {
    val grouped = list.groupBy { it.categoryName.ifBlank { "其他" } }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(10)

    val total = grouped.sumOf { it.second }.coerceAtLeast(0.01)
    val colors = listOf(
        Color(0xFF1D2A52),
        Color(0xFF3D4470),
        MintGreen,
        Color(0xFFFFC75F),
        Color(0xFFFF7F9D),
        Color(0xFF6A9CFF),
        Color(0xFF8DD6A5),
        Color(0xFFFF9F68),
        Color(0xFF9FA0A6),
        Color(0xFF6E6F75),
    )

    return grouped.mapIndexed { index, (name, amount) ->
        CategoryStat(
            name = name,
            amount = amount,
            percent = ((amount / total) * 100).toInt().coerceIn(0, 100),
            color = colors[index % colors.size],
            icon = resolveCategoryIcon(name, ""),
        )
    }
}

private fun money(value: Double): String = String.format(Locale.CHINA, "%,.2f", value)

private fun trimNumber(value: Double): String {
    val text = String.format(Locale.CHINA, "%.2f", value)
    return text.trimEnd('0').trimEnd('.')
}

private fun formatLedgerTime(timestamp: Long): String {
    return SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(timestamp))
}
