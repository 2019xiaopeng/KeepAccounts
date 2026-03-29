package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ShowChart
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.DateRange
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qcb.keepaccounts.ui.components.CollapsibleTopBar
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.ui.components.ThemedSegmentedToggle
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.components.rememberTopBarCollapseProgress
import com.qcb.keepaccounts.ui.format.formatCurrency
import com.qcb.keepaccounts.ui.format.formatSignedCurrency
import com.qcb.keepaccounts.ui.icons.resolveCategoryIcon
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.theme.canonicalCategoryName
import com.qcb.keepaccounts.ui.theme.categoryRankColor
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

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

private data class TrendChartData(
    val labels: List<String>,
    val values: List<Double>,
)

private enum class LedgerViewMode { CALENDAR, STATS }
private enum class StatsPeriod { MONTH, YEAR }
private enum class TrendMetric { EXPENSE, INCOME, BALANCE }
private enum class RankType { EXPENSE, INCOME }
private enum class RecordSortMode { TIME, AMOUNT }

@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    ledgerCurrency: String,
    defaultLedgerName: String,
    monthlyBudget: Double,
    onEditRecord: (ManualEntryPrefill) -> Unit = {},
    onDeleteRecord: (Long) -> Unit = {},
    accentColor: Color = MintGreen,
    modifier: Modifier = Modifier,
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()
    val topBarProgress = rememberTopBarCollapseProgress(listState)
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
        val weekOffset = firstDayCalendar.get(Calendar.DAY_OF_WEEK) - 1
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
    val periodOpeningBalance = if (statsPeriod == StatsPeriod.MONTH) monthlyBudget else monthlyBudget * 12.0
    val totalExpense = scopeTransactions.filter { it.type == 0 }.sumOf { it.amount }
    val totalIncome = scopeTransactions.filter { it.type == 1 }.sumOf { it.amount }
    val totalBalance = periodOpeningBalance + totalIncome - totalExpense

    val categoryExpense = remember(scopeTransactions) { categoryStats(scopeTransactions.filter { it.type == 0 }) }
    val categoryIncome = remember(scopeTransactions) { categoryStats(scopeTransactions.filter { it.type == 1 }) }
    val rankList = if (rankType == RankType.EXPENSE) categoryExpense else categoryIncome
    val trendChartData = remember(scopeTransactions, statsPeriod, year, month, trendMetric, periodOpeningBalance) {
        buildTrendChartData(
            statsPeriod = statsPeriod,
            year = year,
            month = month,
            transactions = scopeTransactions,
            metric = trendMetric,
            openingBalance = periodOpeningBalance,
        )
    }

    val sortedRecords = remember(transactions, recordSortMode) {
        when (recordSortMode) {
            RecordSortMode.TIME -> transactions.sortedByDescending { it.recordTimestamp }
            RecordSortMode.AMOUNT -> transactions.sortedByDescending { it.amount }
        }
    }
    val pageSize = 5
    val totalPages = remember(sortedRecords) { (sortedRecords.size + pageSize - 1) / pageSize }
    val safePage = currentPage.coerceIn(0, (totalPages - 1).coerceAtLeast(0))
    val pageRecords = remember(sortedRecords, safePage) {
        sortedRecords.drop(safePage * pageSize).take(pageSize)
    }

    val ledgerBgTop = lerp(accentColor, Color.White, 0.76f)
    val ledgerBgMid = lerp(accentColor, Color.White, 0.88f)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        ledgerBgTop,
                        ledgerBgMid,
                        Color(0xFFFFFFFF),
                    ),
                ),
            ),
    ) {
        CollapsibleTopBar(
            title = "账本 📒",
            subtitle = "$defaultLedgerName · 记录与统计",
            progress = topBarProgress,
            modifier = Modifier
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 16.dp, top = 8.dp, end = 16.dp, bottom = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ThemedSegmentedToggle(
                    options = listOf("日历记账", "统计报表"),
                    icons = listOf(Icons.Rounded.DateRange, Icons.AutoMirrored.Rounded.ShowChart),
                    selectedIndex = viewMode.ordinal,
                    onSelectedChange = { index ->
                        viewMode = if (index == 0) LedgerViewMode.CALENDAR else LedgerViewMode.STATS
                    },
                    accentColor = accentColor,
                )
            }
        }

        item {
            AnimatedContent(
                targetState = viewMode.ordinal,
                transitionSpec = {
                    fadeIn(animationSpec = tween(120)) togetherWith
                        fadeOut(animationSpec = tween(90))
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
                            ledgerCurrency = ledgerCurrency,
                            accentColor = accentColor,
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
                                        recordTimestamp = tx.recordTimestamp,
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
                        trendLabels = trendChartData.labels,
                        trendValues = trendChartData.values,
                        monthlyBudget = monthlyBudget,
                        balanceOpening = periodOpeningBalance,
                        ledgerCurrency = ledgerCurrency,
                        categoryExpense = categoryExpense,
                        rankType = rankType,
                        onRankTypeChange = { rankType = it },
                        rankList = rankList,
                        accentColor = accentColor,
                    )
                }
            }
        }

        item {
            RecordPagerSection(
                sortMode = recordSortMode,
                onSortModeChange = {
                    recordSortMode = it
                    currentPage = 0
                },
                records = pageRecords,
                page = safePage,
                totalPages = totalPages,
                ledgerCurrency = ledgerCurrency,
                accentColor = accentColor,
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
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {

        Row(modifier = Modifier.fillMaxWidth()) {
            listOf("日", "一", "二", "三", "四", "五", "六").forEach { label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(18.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = WarmBrown.copy(alpha = 0.66f),
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp,
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        cells.chunked(7).forEach { week ->
            Row(modifier = Modifier.fillMaxWidth()) {
                week.forEach { cell ->
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                    if (cell.day == null) {
                        Spacer(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        )
                    } else {
                        val selected = cell.day == selectedDay
                        DayCellView(
                            cell = cell,
                            selected = selected,
                            modifier = Modifier.fillMaxWidth(),
                            onClick = { onSelectDay(cell.day) },
                        )
                    }
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(
        modifier = modifier
            .height(58.dp)
            .padding(horizontal = 0.dp)
            .appPressable { onClick() },
        verticalArrangement = Arrangement.spacedBy(0.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(26.dp)
                .background(
                    color = if (selected) Color(0xFFF7A652) else Color.Transparent,
                    shape = CircleShape,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = cell.day.toString(),
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 16.sp,
                lineHeight = 16.sp,
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (cell.expense > 0) "-${trimNumber(cell.expense)}" else "",
                color = WatermelonRed,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(11.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (cell.income > 0) "+${trimNumber(cell.income)}" else "",
                color = MintGreen,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                lineHeight = 10.sp,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun ArrowButton(icon: ImageVector, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(Color.White.copy(alpha = 0.9f), CircleShape)
            .appPressable { onClick() },
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
    ledgerCurrency: String,
    accentColor: Color,
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
    val headerAmount = if (dayIncome >= dayExpense) {
        formatSignedCurrency(ledgerCurrency, dayIncome, true)
    } else {
        formatSignedCurrency(ledgerCurrency, dayExpense, false)
    }
    val headerColor = if (dayIncome >= dayExpense) accentColor else WatermelonRed

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
                        .appPressable { onToggleExpand(tx.id) }
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
                            text = formatSignedCurrency(ledgerCurrency, tx.amount, isIncome),
                            color = if (isIncome) accentColor else WatermelonRed,
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
                                            .appPressable { onCancelDelete() },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Text(text = "✕", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                                    }
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .background(WatermelonRed, CircleShape)
                                            .appPressable {
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
                                            .appPressable { onEdit(tx) },
                                    )
                                    Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "delete",
                                        tint = WatermelonRed.copy(alpha = 0.85f),
                                        modifier = Modifier
                                            .size(16.dp)
                                            .appPressable { onBeginDelete(tx.id) },
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
    trendLabels: List<String>,
    trendValues: List<Double>,
    monthlyBudget: Double,
    balanceOpening: Double,
    ledgerCurrency: String,
    categoryExpense: List<CategoryStat>,
    rankType: RankType,
    onRankTypeChange: (RankType) -> Unit,
    rankList: List<CategoryStat>,
    accentColor: Color,
) {
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        ThemedSegmentedToggle(
            options = listOf("月度", "年度"),
            selectedIndex = statsPeriod.ordinal,
            onSelectedChange = { index ->
                onStatsPeriodChange(if (index == 0) StatsPeriod.MONTH else StatsPeriod.YEAR)
            },
            accentColor = accentColor,
            modifier = Modifier.align(Alignment.CenterHorizontally),
            textSizeSp = 14,
            horizontalPadding = 24.dp,
        )

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            ArrowButton(icon = Icons.Rounded.ChevronLeft, onClick = onPrev)
            AnimatedContent(
                targetState = statsPeriod.ordinal,
                transitionSpec = {
                    fadeIn(animationSpec = tween(120)) togetherWith
                        fadeOut(animationSpec = tween(90))
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
                fadeIn(animationSpec = tween(120)) togetherWith
                    fadeOut(animationSpec = tween(90))
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
                    StatsItem("总支出", formatCurrency(ledgerCurrency, totalExpense), WatermelonRed)
                    DividerV()
                    StatsItem("总收入", formatCurrency(ledgerCurrency, totalIncome), accentColor)
                    DividerV()
                    StatsItem("结余", formatCurrency(ledgerCurrency, totalBalance), WarmBrown)
                }

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(
                            elevation = 12.dp,
                            shape = RoundedCornerShape(32.dp),
                            ambientColor = Color(0x1F000000),
                            spotColor = Color(0x1F000000),
                        )
                        .background(Color.White.copy(alpha = 0.95f), RoundedCornerShape(32.dp))
                        .padding(horizontal = 14.dp, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(text = "每日趋势", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            Text(text = "📈", fontSize = 16.sp)
                        }
                        ThemedSegmentedToggle(
                            options = listOf("支出", "收入", "结余"),
                            selectedIndex = trendMetric.ordinal,
                            onSelectedChange = { index ->
                                onTrendMetricChange(
                                    when (index) {
                                        0 -> TrendMetric.EXPENSE
                                        1 -> TrendMetric.INCOME
                                        else -> TrendMetric.BALANCE
                                    },
                                )
                            },
                            accentColor = accentColor,
                            textSizeSp = 12,
                            horizontalPadding = 14.dp,
                        )
                    }
                    AnimatedContent(
                        targetState = trendMetric.ordinal,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(120)) togetherWith
                                fadeOut(animationSpec = tween(90))
                        },
                        label = "trendMetricSwitch",
                    ) {
                        TrendChart(
                            labels = trendLabels,
                            values = trendValues,
                            metric = trendMetric,
                            axisBaseline = if (trendMetric == TrendMetric.BALANCE) {
                                balanceOpening
                            } else {
                                monthlyBudget
                            },
                            accentColor = accentColor,
                        )
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
                        DonutChart(
                            categoryExpense = categoryExpense,
                            totalExpense = totalExpense,
                            ledgerCurrency = ledgerCurrency,
                        )
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
                            Text(text = formatCurrency(ledgerCurrency, item.amount), color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp)
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
                        ThemedSegmentedToggle(
                            options = listOf("支出", "收入"),
                            selectedIndex = rankType.ordinal,
                            onSelectedChange = { index ->
                                onRankTypeChange(if (index == 0) RankType.EXPENSE else RankType.INCOME)
                            },
                            accentColor = accentColor,
                            textSizeSp = 13,
                            horizontalPadding = 16.dp,
                        )
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        rankList.forEach {
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                Text(text = it.name, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(
                                    text = formatSignedCurrency(
                                        ledgerCurrency,
                                        it.amount,
                                        rankType == RankType.INCOME,
                                    ),
                                    color = if (rankType == RankType.EXPENSE) WatermelonRed else accentColor,
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
private fun TrendChart(
    labels: List<String>,
    values: List<Double>,
    metric: TrendMetric,
    axisBaseline: Double,
    accentColor: Color,
) {
    val shownLabels = if (labels.isEmpty()) listOf("-") else labels
    val shownValues = if (values.isEmpty()) listOf(0.0) else values
    val valueMax = shownValues.maxOrNull()?.coerceAtLeast(0.0) ?: 0.0
    val axisMax = maxOf(axisBaseline.coerceAtLeast(1.0), valueMax, 1.0)
    val yLabels = listOf(axisMax, axisMax * 0.75, axisMax * 0.5, axisMax * 0.25, 0.0)
    val lineColor = when (metric) {
        TrendMetric.EXPENSE -> WatermelonRed
        TrendMetric.INCOME -> accentColor
        TrendMetric.BALANCE -> WarmBrown
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(220.dp)
            .background(Color(0xFFF7F8FA), RoundedCornerShape(22.dp))
            .padding(start = 8.dp, top = 10.dp, end = 8.dp, bottom = 8.dp),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 34.dp, top = 8.dp, end = 6.dp, bottom = 24.dp),
        ) {
            repeat(5) { index ->
                val y = size.height * index / 4f
                drawLine(
                    color = Color(0xFFDADDE1),
                    start = Offset(0f, y),
                    end = Offset(size.width, y),
                    strokeWidth = 1f,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 8f), 0f),
                    cap = StrokeCap.Round,
                )
            }

            val points = shownValues.mapIndexed { index, value ->
                val x = if (shownValues.size == 1) {
                    size.width / 2f
                } else {
                    size.width * index / (shownValues.lastIndex.toFloat())
                }
                val ratio = (value / axisMax).toFloat().coerceIn(0f, 1f)
                val y = size.height * (1f - ratio)
                Offset(x, y)
            }

            if (points.size >= 2) {
                for (i in 0 until points.lastIndex) {
                    drawLine(
                        color = lineColor,
                        start = points[i],
                        end = points[i + 1],
                        strokeWidth = 5f,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 8.dp, bottom = 24.dp)
                .height(164.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start,
        ) {
            yLabels.forEach { label ->
                Text(
                    text = formatAxisLabel(label),
                    color = WarmBrownMuted.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 34.dp, end = 6.dp),
            horizontalArrangement = if (shownLabels.size == 1) Arrangement.Center else Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            shownLabels.forEach { label ->
                Text(
                    text = label,
                    color = WarmBrownMuted.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun DonutChart(
    categoryExpense: List<CategoryStat>,
    totalExpense: Double,
    ledgerCurrency: String,
) {
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
            Text(text = formatCurrency(ledgerCurrency, totalExpense), color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 20.sp)
        }
    }
}

@Composable
private fun RecordPagerSection(
    sortMode: RecordSortMode,
    onSortModeChange: (RecordSortMode) -> Unit,
    records: List<TransactionEntity>,
    page: Int,
    totalPages: Int,
    ledgerCurrency: String,
    accentColor: Color,
    onPrevPage: () -> Unit,
    onNextPage: () -> Unit,
) {
    val visibleSlotCount = 5
    val recordRowHeight = 68.dp
    val fixedListHeight = recordRowHeight * visibleSlotCount
    val canGoPrev = page > 0
    val canGoNext = page < totalPages - 1

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(28.dp), backgroundColor = Color.White.copy(alpha = 0.78f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(text = "账本明细", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)

        RecordSortModeToggle(
            sortMode = sortMode,
            onSortModeChange = onSortModeChange,
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        AnimatedContent(
            targetState = page,
            transitionSpec = {
                fadeIn(animationSpec = tween(120)) togetherWith
                    fadeOut(animationSpec = tween(90))
            },
            label = "recordPageSwitch",
        ) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fixedListHeight)
                    .background(Color.White.copy(alpha = 0.65f), RoundedCornerShape(18.dp))
                    .padding(horizontal = 10.dp),
                userScrollEnabled = false,
            ) {
                items(count = visibleSlotCount, key = { slotIndex -> records.getOrNull(slotIndex)?.id ?: "record-slot-$slotIndex" }) { slotIndex ->
                    val record = records.getOrNull(slotIndex)

                    if (record == null) {
                        if (records.isEmpty() && slotIndex == visibleSlotCount / 2) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(recordRowHeight),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    text = "暂无记录",
                                    color = WarmBrownMuted,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 12.sp,
                                    textAlign = TextAlign.Center,
                                )
                            }
                        } else {
                            Spacer(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(recordRowHeight),
                            )
                        }
                    } else {
                        val isIncome = record.type == 1
                        val subtitle = record.remark.takeIf { it.isNotBlank() }
                            ?.let { "$it · ${formatDate(record.recordTimestamp)}" }
                            ?: formatDate(record.recordTimestamp)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(recordRowHeight)
                                .background(Color.White.copy(alpha = 0.86f), RoundedCornerShape(14.dp)),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .padding(start = 10.dp, end = 8.dp),
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    text = record.categoryName,
                                    color = WarmBrown,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Text(
                                    text = subtitle,
                                    color = WarmBrownMuted,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            Text(
                                text = formatSignedCurrency(ledgerCurrency, record.amount, isIncome),
                                color = if (isIncome) accentColor else WatermelonRed,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp,
                                modifier = Modifier.padding(end = 10.dp),
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PaginationButton(text = "上一页", enabled = canGoPrev, onClick = onPrevPage)
            Text(
                text = "${page + 1} / ${totalPages.coerceAtLeast(1)}",
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
            )
            PaginationButton(text = "下一页", enabled = canGoNext, onClick = onNextPage)
        }
    }
}

@Composable
private fun RecordSortModeToggle(
    sortMode: RecordSortMode,
    onSortModeChange: (RecordSortMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    val selectedIndex = if (sortMode == RecordSortMode.TIME) 0 else 1
    val options = listOf("按时间排序", "按金额排序")
    val containerShape = RoundedCornerShape(999.dp)

    BoxWithConstraints(
        modifier = modifier
            .width(220.dp)
            .height(36.dp)
            .background(Color(0xFFF3F4F6), containerShape),
    ) {
        val optionWidth = maxWidth / options.size

        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .offset(x = optionWidth * selectedIndex)
                .width(optionWidth)
                .height(32.dp)
                .background(Color(0xFFFFA726), containerShape),
        )

        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            options.forEachIndexed { index, label ->
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .appPressable {
                            onSortModeChange(
                                if (index == 0) RecordSortMode.TIME else RecordSortMode.AMOUNT,
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        color = if (index == selectedIndex) Color.White else WarmBrownMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun PaginationButton(text: String, enabled: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .background(
                color = if (enabled) Color(0xFFFFA726) else Color(0xFFE5E7EB),
                shape = RoundedCornerShape(999.dp),
            )
            .appPressable(enabled = enabled) { onClick() }
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = text,
            color = if (enabled) Color.White else WarmBrownMuted,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
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
    }
}

private fun buildTrendChartData(
    statsPeriod: StatsPeriod,
    year: Int,
    month: Int,
    transactions: List<TransactionEntity>,
    metric: TrendMetric,
    openingBalance: Double,
): TrendChartData {
    return if (statsPeriod == StatsPeriod.MONTH) {
        val daysInMonth = Calendar.getInstance().apply { set(year, month, 1) }.getActualMaximum(Calendar.DAY_OF_MONTH)
        val bucketSize = kotlin.math.ceil(daysInMonth / 6.0).toInt().coerceAtLeast(1)
        val byDay = transactions.groupBy { tx ->
            Calendar.getInstance().apply { timeInMillis = tx.recordTimestamp }.get(Calendar.DAY_OF_MONTH)
        }

        val labels = mutableListOf<String>()
        val values = mutableListOf<Double>()
        var runningBalance = openingBalance

        var startDay = 1
        while (startDay <= daysInMonth) {
            val endDay = minOf(daysInMonth, startDay + bucketSize - 1)
            val bucket = (startDay..endDay).flatMap { day -> byDay[day].orEmpty() }
            labels += String.format(Locale.CHINA, "%02d-%02d", month + 1, startDay)
            if (metric == TrendMetric.BALANCE) {
                val delta = metricValue(bucket, TrendMetric.BALANCE)
                runningBalance += delta
                values += runningBalance.coerceAtLeast(0.0)
            } else {
                values += metricValue(bucket, metric).coerceAtLeast(0.0)
            }
            startDay = endDay + 1
        }
        TrendChartData(labels = labels, values = values)
    } else {
        val byMonth = transactions.groupBy { tx ->
            Calendar.getInstance().apply { timeInMillis = tx.recordTimestamp }.get(Calendar.MONTH)
        }
        val labels = mutableListOf<String>()
        val values = mutableListOf<Double>()
        var runningBalance = openingBalance

        for (startMonth in 0..11 step 2) {
            val endMonth = minOf(11, startMonth + 1)
            val bucket = (startMonth..endMonth).flatMap { monthIndex -> byMonth[monthIndex].orEmpty() }
            labels += String.format(Locale.CHINA, "%02d-%02d月", startMonth + 1, endMonth + 1)
            if (metric == TrendMetric.BALANCE) {
                val delta = metricValue(bucket, TrendMetric.BALANCE)
                runningBalance += delta
                values += runningBalance.coerceAtLeast(0.0)
            } else {
                values += metricValue(bucket, metric).coerceAtLeast(0.0)
            }
        }
        TrendChartData(labels = labels, values = values)
    }
}

private fun formatAxisLabel(value: Double): String {
    return when {
        value >= 10_000.0 -> "${trimNumber(value / 10_000.0)}w"
        value >= 1_000.0 -> "${trimNumber(value / 1_000.0)}k"
        else -> trimNumber(value)
    }
}

private fun categoryStats(list: List<TransactionEntity>): List<CategoryStat> {
    val grouped = list.groupBy { canonicalCategoryName(it.categoryName.ifBlank { "其他" }) }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .toList()
        .sortedByDescending { it.second }
        .take(10)

    val total = grouped.sumOf { it.second }.coerceAtLeast(0.01)

    return grouped.map { (name, amount) ->
        CategoryStat(
            name = name,
            amount = amount,
            percent = ((amount / total) * 100).toInt().coerceIn(0, 100),
            color = categoryRankColor(name),
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
