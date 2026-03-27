package com.qcb.keepaccounts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.icons.resolveCategoryIcon
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
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
    onOpenManualEntry: (ManualEntryPrefill) -> Unit,
    modifier: Modifier = Modifier,
) {
    var query by rememberSaveable { mutableStateOf("") }
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val filtered = remember(transactions, query) {
        val key = query.trim().lowercase(Locale.getDefault())
        if (key.isBlank()) {
            emptyList()
        } else {
            transactions.filter {
                it.categoryName.lowercase(Locale.getDefault()).contains(key) ||
                    it.remark.lowercase(Locale.getDefault()).contains(key)
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp, start = 12.dp, end = 12.dp)
                    .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.15f))
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "back",
                    tint = WarmBrown.copy(alpha = 0.7f),
                    modifier = Modifier
                        .size(20.dp)
                        .clickable { onBack() },
                )
                Icon(
                    imageVector = Icons.Rounded.Search,
                    contentDescription = "search",
                    tint = WarmBrown.copy(alpha = 0.45f),
                    modifier = Modifier.size(18.dp),
                )
                TextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("搜索账单、备注、分类...", color = WarmBrownMuted, fontSize = 13.sp) },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .heightIn(min = 0.dp)
                        .height(42.dp),
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
                        .glassCard(shape = RoundedCornerShape(22.dp), glowColor = MintGreen.copy(alpha = 0.12f))
                        .clickable {
                            onOpenManualEntry(
                                ManualEntryPrefill(
                                    type = if (tx.type == 0) "expense" else "income",
                                    category = tx.categoryName,
                                    desc = tx.remark,
                                    amount = tx.amount.toString(),
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
                        text = (if (tx.type == 0) "-¥ " else "+¥ ") + String.format(Locale.CHINA, "%.2f", tx.amount),
                        color = if (tx.type == 0) Color(0xFFFF8B94) else MintGreen,
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
    return SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timestamp))
}
