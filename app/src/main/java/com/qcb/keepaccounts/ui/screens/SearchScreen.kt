package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.format.formatRelativeDateTime
import com.qcb.keepaccounts.ui.format.formatSignedCurrency
import com.qcb.keepaccounts.ui.icons.resolveCategoryIcon
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.theme.IncomeGreen
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun SearchScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    ledgerCurrency: String,
    accentColor: Color = MintGreen,
    onOpenManualEntry: (ManualEntryPrefill) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val filtered = remember(transactions, query) {
        val terms = tokenizeSearchQuery(query)
        if (terms.isEmpty()) {
            emptyList()
        } else {
            transactions
                .filter { tx ->
                    val fields = buildSearchFields(tx)
                    terms.all { term -> fields.any { field -> field.contains(term) } }
                }
                .sortedByDescending { it.recordTimestamp }
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                    .glassCard(shape = RoundedCornerShape(20.dp), glowColor = accentColor.copy(alpha = 0.15f))
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "back",
                    tint = WarmBrown.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(18.dp)
                        .appPressable { onBack() },
                )
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "search",
                    tint = WarmBrown.copy(alpha = 0.45f),
                    modifier = Modifier.size(16.dp),
                )
                BasicTextField(
                    value = query,
                    onValueChange = { query = it },
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = WarmBrown,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 14.sp,
                        lineHeight = 18.sp,
                    ),
                    singleLine = true,
                    cursorBrush = SolidColor(WarmBrown),
                    modifier = Modifier
                        .weight(1f),
                    decorationBox = { innerTextField ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (query.isBlank()) {
                                Text(
                                    text = "搜索时间、金额、分类、备注...",
                                    color = WarmBrown.copy(alpha = 0.5f),
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            innerTextField()
                        }
                    },
                )
            }
        }

        if (query.isBlank()) {
            item {
                EmptySearchHint(text = "输入关键词，快速定位每一笔账单")
            }
        } else if (filtered.isEmpty()) {
            item {
                EmptySearchHint(text = "没有匹配结果，换个关键词试试")
            }
        } else {
            items(filtered, key = { it.id }) { tx ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .glassCard(shape = RoundedCornerShape(22.dp), glowColor = accentColor.copy(alpha = 0.12f))
                        .appPressable {
                            onOpenManualEntry(
                                ManualEntryPrefill(
                                    transactionId = tx.id,
                                    type = if (tx.type == 0) "expense" else "income",
                                    category = tx.categoryName,
                                    desc = tx.remark,
                                    amount = tx.amount.toString(),
                                    recordTimestamp = tx.recordTimestamp,
                                ),
                            )
                        }
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .background(Color.White.copy(alpha = 0.75f), RoundedCornerShape(14.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = resolveCategoryIcon(tx.categoryName, tx.remark),
                                contentDescription = tx.categoryName,
                                tint = WarmBrown,
                                modifier = Modifier.size(18.dp),
                            )
                        }

                        Column {
                            Text(text = tx.categoryName, color = WarmBrown, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = "${tx.remark} · ${formatDateTime(tx.recordTimestamp)}",
                                color = WarmBrownMuted,
                                fontSize = 11.sp,
                            )
                        }
                    }

                    Text(
                        text = formatSignedCurrency(ledgerCurrency, tx.amount, tx.type == 1),
                        color = if (tx.type == 0) Color(0xFFFF8B94) else IncomeGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptySearchHint(text: String) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 80.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(84.dp)
                .background(Color.White.copy(alpha = 0.5f), RoundedCornerShape(999.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Rounded.Search,
                contentDescription = null,
                tint = WarmBrown.copy(alpha = 0.35f),
                modifier = Modifier.size(32.dp),
            )
        }
        Text(text = "查找历史账单", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
        Text(text = text, color = WarmBrownMuted, fontSize = 13.sp)
    }
}

private fun formatDateTime(timestamp: Long): String {
    return formatRelativeDateTime(timestamp)
}

private fun tokenizeSearchQuery(raw: String): List<String> {
    return raw
        .trim()
        .split(Regex("[\\s,，;；]+"))
        .map { normalizeSearchToken(it) }
        .filter { it.isNotBlank() }
        .distinct()
}

private fun buildSearchFields(tx: TransactionEntity): List<String> {
    val date = Date(tx.recordTimestamp)
    val amount = trimSearchAmount(tx.amount)
    val signedAmount = if (tx.type == 1) "+$amount" else "-$amount"
    val typeKeyword = if (tx.type == 1) "收入" else "支出"

    val dateFormats = listOf(
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.CHINA),
        SimpleDateFormat("yyyy-MM-dd", Locale.CHINA),
        SimpleDateFormat("MM-dd", Locale.CHINA),
        SimpleDateFormat("M月d日", Locale.CHINA),
        SimpleDateFormat("yyyy年M月d日", Locale.CHINA),
        SimpleDateFormat("HH:mm", Locale.CHINA),
    )

    val dateTexts = dateFormats.map { formatter -> formatter.format(date) }

    return buildList {
        add(normalizeSearchToken(tx.categoryName))
        add(normalizeSearchToken(tx.remark))
        add(normalizeSearchToken(typeKeyword))
        add(normalizeSearchToken(amount))
        add(normalizeSearchToken(signedAmount))
        dateTexts.forEach { add(normalizeSearchToken(it)) }
    }
}

private fun normalizeSearchToken(raw: String): String {
    return raw.lowercase(Locale.getDefault())
        .replace("￥", "")
        .replace("¥", "")
        .replace("元", "")
        .replace("块", "")
        .trim()
}

private fun trimSearchAmount(value: Double): String {
    val text = String.format(Locale.CHINA, "%.2f", value)
    return text.trimEnd('0').trimEnd('.')
}
