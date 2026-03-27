package com.qcb.keepaccounts.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.icons.resolveCategoryIcon
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.theme.MintGreen
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
    val id: Long,
    val icon: ImageVector,
    val category: String,
    val desc: String,
    val time: String,
    val amount: String,
    val amountRaw: String,
    val isIncome: Boolean = false,
)

private data class DaySection(
    val title: String,
    val summary: String,
    val records: List<ActivityRecord>,
)

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    assistantName: String,
    assistantAvatar: String,
    onSearchClick: () -> Unit,
    onAiRecordClick: () -> Unit,
    onManualRecordClick: () -> Unit,
    onViewAllClick: () -> Unit,
    onEditRecord: (ManualEntryPrefill) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var expandedRecordId by rememberSaveable { mutableLongStateOf(-1L) }
    var confirmDeleteRecordId by rememberSaveable { mutableLongStateOf(-1L) }

    val transactions by viewModel.transactions.collectAsStateWithLifecycle()
    val sections = remember(transactions) { mapTransactionsToSections(transactions) }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 88.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            HomeHeader(
                assistantName = assistantName,
                onOpenSearchPage = onSearchClick,
            )
        }

        item { BudgetCard(transactions = transactions) }

        item {
            ActionButtons(
                assistantName = assistantName,
                onAiRecordClick = onAiRecordClick,
                onManualRecordClick = onManualRecordClick,
            )
        }

        item { RecentHeader(onViewAllClick = onViewAllClick) }

        if (sections.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(24.dp), glowColor = MintGreen.copy(alpha = 0.15f))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "暂无账单，去记一笔吧",
                        color = WarmBrownMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                    )
                }
            }
        } else {
            items(sections) { section ->
                DaySectionCard(
                    section = section,
                    expandedRecordId = expandedRecordId,
                    confirmDeleteRecordId = confirmDeleteRecordId,
                    onToggleExpand = { id ->
                        if (expandedRecordId == id) {
                            expandedRecordId = -1L
                            confirmDeleteRecordId = -1L
                        } else {
                            expandedRecordId = id
                            confirmDeleteRecordId = -1L
                        }
                    },
                    onBeginDelete = { id -> confirmDeleteRecordId = id },
                    onCancelDelete = { confirmDeleteRecordId = -1L },
                    onConfirmDelete = { id ->
                        onDeleteRecord(id)
                        if (expandedRecordId == id) expandedRecordId = -1L
                        if (confirmDeleteRecordId == id) confirmDeleteRecordId = -1L
                    },
                    onEditRecord = { prefill ->
                        expandedRecordId = -1L
                        confirmDeleteRecordId = -1L
                        onEditRecord(prefill)
                    },
                )
            }
        }
    }
}

@Composable
private fun HomeHeader(
    assistantName: String,
    onOpenSearchPage: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
            Text(
                text = "$assistantName🌊营业中 ✨",
                color = Color(0xFF2B211E),
                fontWeight = FontWeight.ExtraBold,
                fontSize = 33.sp,
            )
            Text(
                text = "劳动最光荣 💼",
                color = WarmBrown.copy(alpha = 0.62f),
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp,
            )
        }

        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .background(Color.White.copy(alpha = 0.92f))
                .clickable { onOpenSearchPage() },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = "search-page",
                tint = Color(0xFF3B3531),
                modifier = Modifier.size(24.dp),
            )
        }
    }
}

@Composable
private fun AssistantAvatar(assistantAvatar: String) {
    Box(
        modifier = Modifier
            .size(46.dp)
            .glassCard(shape = CircleShape, glowColor = MintGreen.copy(alpha = 0.24f))
            .background(
                brush = Brush.radialGradient(
                    listOf(Color.White.copy(alpha = 0.96f), MintGreen.copy(alpha = 0.35f)),
                ),
                shape = CircleShape,
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (assistantAvatar.startsWith("http://") || assistantAvatar.startsWith("https://")) {
            AsyncImage(
                model = assistantAvatar,
                contentDescription = "assistant-avatar",
                modifier = Modifier.size(38.dp),
            )
        } else {
            Text(text = assistantAvatar, fontSize = 22.sp)
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

    val budgetTotal = 2000.0
    val usedRatio = (monthExpense / budgetTotal).coerceIn(0.0, 1.0)
    val remain = budgetTotal - monthExpense

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(30.dp), glowColor = MintGreen.copy(alpha = 0.2f))
            .padding(horizontal = 18.dp, vertical = 16.dp),
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(130.dp)
                .blur(28.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(MintGreen.copy(alpha = 0.62f), Color.Transparent),
                    ),
                    shape = CircleShape,
                ),
        )

        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(text = "本月预算 🎯", color = Color(0xFF2B211E), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(text = "已用", color = WarmBrown.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                Text(text = "剩余", color = WarmBrown.copy(alpha = 0.6f), fontWeight = FontWeight.Bold, fontSize = 12.sp)
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "¥${money(monthExpense)}",
                    color = WatermelonPink,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
                Text(
                    text = "¥${money(remain)}",
                    color = WatermelonRed,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 16.sp,
                )
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .background(color = Color.White.copy(alpha = 0.72f), shape = RoundedCornerShape(999.dp)),
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
                    text = "0",
                    color = WarmBrown.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
                Text(
                    text = "总预算: ¥${money(budgetTotal)}",
                    color = WarmBrown.copy(alpha = 0.55f),
                    fontWeight = FontWeight.Medium,
                    fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    assistantName: String,
    onAiRecordClick: () -> Unit,
    onManualRecordClick: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ActionButton(
            modifier = Modifier.weight(1f),
            text = "💬 和${assistantName}聊天",
            textColor = Color(0xFF58BDB4),
            onClick = onAiRecordClick,
        )
        ActionButton(
            modifier = Modifier.weight(1f),
            text = "✍️ 手动记账",
            textColor = Color(0xFF2C2320),
            onClick = onManualRecordClick,
        )
    }
}

@Composable
private fun ActionButton(
    modifier: Modifier,
    text: String,
    textColor: Color,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.965f else 1f,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 380f),
        label = "actionScale",
    )

    Box(
        modifier = modifier
            .graphicsLayer(scaleX = scale, scaleY = scale)
            .glassCard(shape = RoundedCornerShape(999.dp), glowColor = MintGreen.copy(alpha = 0.18f))
            .background(color = Color.White.copy(alpha = 0.95f), shape = RoundedCornerShape(999.dp))
            .clickable(interactionSource = interaction, indication = null) { onClick() }
            .padding(vertical = 14.dp, horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = text, color = textColor, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
    }
}

@Composable
private fun RecentHeader(onViewAllClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onViewAllClick() },
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = "最近动态 🍃", color = Color(0xFF2B211E), fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
    }
}

@Composable
private fun DaySectionCard(
    section: DaySection,
    expandedRecordId: Long,
    confirmDeleteRecordId: Long,
    onToggleExpand: (Long) -> Unit,
    onBeginDelete: (Long) -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: (Long) -> Unit,
    onEditRecord: (ManualEntryPrefill) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(26.dp), glowColor = MintGreen.copy(alpha = 0.12f))
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(text = section.title, color = Color(0xFF2B211E), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
            Text(text = section.summary, color = WatermelonRed, fontWeight = FontWeight.Bold, fontSize = 14.sp)
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color(0x11000000), RoundedCornerShape(99.dp)),
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.records.forEachIndexed { index, record ->
                ActivityItem(
                    record = record,
                    isExpanded = expandedRecordId == record.id,
                    isConfirmingDelete = confirmDeleteRecordId == record.id,
                    onToggle = { onToggleExpand(record.id) },
                    onEdit = {
                        onEditRecord(
                            ManualEntryPrefill(
                                type = if (record.isIncome) "income" else "expense",
                                category = record.category,
                                desc = record.desc,
                                amount = record.amountRaw,
                            ),
                        )
                    },
                    onDelete = { onBeginDelete(record.id) },
                    onCancelDelete = onCancelDelete,
                    onConfirmDelete = { onConfirmDelete(record.id) },
                )
                if (index != section.records.lastIndex) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(1.dp)
                            .background(Color(0x0F000000), RoundedCornerShape(99.dp)),
                    )
                }
            }
        }
    }
}

@Composable
private fun ActivityItem(
    record: ActivityRecord,
    isExpanded: Boolean,
    isConfirmingDelete: Boolean,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onCancelDelete: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    val amountColor = if (record.isIncome) MintGreen else WatermelonRed

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize()
            .clickable { onToggle() }
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
                        .background(Color.White.copy(alpha = 0.95f), CircleShape)
                        .background(
                            brush = Brush.linearGradient(listOf(Color.White, Color(0xFFF9F9F9))),
                            shape = CircleShape,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = record.icon,
                        contentDescription = record.category,
                        tint = WarmBrown.copy(alpha = 0.92f),
                        modifier = Modifier.size(20.dp),
                    )
                }

                Column {
                    Text(text = record.category, color = Color(0xFF2B211E), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp)
                    Text(
                        text = record.desc,
                        color = WarmBrownMuted,
                        fontWeight = FontWeight.Medium,
                        fontSize = 13.sp,
                    )
                }
            }

            Text(text = record.amount, color = amountColor, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
        }

        AnimatedVisibility(visible = isExpanded) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (isConfirmingDelete) {
                    Row(
                        modifier = Modifier
                            .background(Color(0xFFFFF0F0), RoundedCornerShape(999.dp))
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = "确认删除?", color = WatermelonRed, fontWeight = FontWeight.Bold, fontSize = 11.sp)
                        Icon(
                            imageVector = Icons.Rounded.Close,
                            contentDescription = "cancel-delete",
                            tint = WarmBrown.copy(alpha = 0.65f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onCancelDelete() },
                        )
                        Icon(
                            imageVector = Icons.Rounded.Check,
                            contentDescription = "confirm-delete",
                            tint = Color.White,
                            modifier = Modifier
                                .size(16.dp)
                                .background(WatermelonRed, CircleShape)
                                .clickable { onConfirmDelete() }
                                .padding(2.dp),
                        )
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.Edit,
                            contentDescription = "edit",
                            tint = WarmBrown.copy(alpha = 0.55f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onEdit() },
                        )
                        Icon(
                            imageVector = Icons.Rounded.Delete,
                            contentDescription = "delete",
                            tint = WatermelonRed.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(16.dp)
                                .clickable { onDelete() },
                        )
                    }
                }
            }
        }
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
        val summary = if (income > expense) "收入 ¥${money(income)}" else "支出 ¥${money(expense)}"

        val records = list.sortedByDescending { it.recordTimestamp }.map { tx ->
            val isIncome = tx.type == 1
            ActivityRecord(
                id = tx.id,
                icon = resolveCategoryIcon(tx.categoryName, tx.remark),
                category = tx.categoryName,
                desc = tx.remark,
                time = timeFormat.format(Date(tx.recordTimestamp)),
                amount = (if (isIncome) "+¥ " else "-¥ ") + money(tx.amount),
                amountRaw = String.format(Locale.CHINA, "%.2f", tx.amount),
                isIncome = isIncome,
            )
        }

        DaySection(title = title, summary = summary, records = records)
    }
}

private fun recentDailyExpense(transactions: List<TransactionEntity>, days: Int): List<Float> {
    val now = Calendar.getInstance()
    return (days - 1 downTo 0).map { offset ->
        val day = (now.clone() as Calendar).apply {
            add(Calendar.DAY_OF_MONTH, -offset)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        val start = day.timeInMillis
        day.add(Calendar.DAY_OF_MONTH, 1)
        val end = day.timeInMillis

        transactions.filter { tx -> tx.type == 0 && tx.recordTimestamp in start until end }
            .sumOf { it.amount }
            .toFloat()
    }
}

private fun money(value: Double): String {
    return String.format(Locale.CHINA, "%,.2f", value)
}
