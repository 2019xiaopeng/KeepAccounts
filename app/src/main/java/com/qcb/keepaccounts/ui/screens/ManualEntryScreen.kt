package com.qcb.keepaccounts.ui.screens
import com.qcb.keepaccounts.ui.components.appPressable

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
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DateRange
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.qcb.keepaccounts.ui.components.ThemedSegmentedToggle
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.format.primaryCurrencySymbol
import com.qcb.keepaccounts.ui.icons.resolveCategoryIcon
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.theme.IncomeGreen
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualEntryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    categories: List<String>,
    selectedColor: Color,
    ledgerCurrency: String,
    initialData: ManualEntryPrefill? = null,
    onConsumedInitialData: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    var type by rememberSaveable { mutableStateOf("expense") }
    var amountInput by rememberSaveable { mutableStateOf("") }
    var remarkInput by rememberSaveable { mutableStateOf("") }
    var selectedCategory by rememberSaveable { mutableStateOf("餐饮美食") }
    var recordDateMillis by rememberSaveable { mutableLongStateOf(System.currentTimeMillis()) }
    var showDatePicker by rememberSaveable { mutableStateOf(false) }
    var saveTip by rememberSaveable { mutableStateOf<String?>(null) }

    LaunchedEffect(initialData) {
        if (initialData != null) {
            type = initialData.type
            amountInput = initialData.amount
            remarkInput = initialData.desc
            initialData.recordTimestamp?.let { recordDateMillis = it }
            if (initialData.category.isNotBlank()) {
                selectedCategory = initialData.category
            }
            onConsumedInitialData()
        }
    }

    LaunchedEffect(categories) {
        if (categories.isNotEmpty() && selectedCategory !in categories) {
            selectedCategory = categories.first()
        }
    }

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(22.dp), glowColor = MintGreen.copy(alpha = 0.16f))
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "← 返回",
                    color = WarmBrown,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.appPressable { onBack() },
                )
                Text(text = "手动记一笔", color = WarmBrown, fontWeight = FontWeight.ExtraBold, fontSize = 17.sp)
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        item {
            val currencySymbol = primaryCurrencySymbol(ledgerCurrency)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(30.dp), glowColor = MintGreen.copy(alpha = 0.2f))
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                if (currencySymbol.isNotBlank()) {
                    Text(
                        text = currencySymbol,
                        color = if (type == "expense") WatermelonRed else IncomeGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 34.sp,
                    )
                }
                TextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    placeholder = { Text("0.00", color = WarmBrown.copy(alpha = 0.25f), fontSize = 36.sp) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = Color.Transparent,
                        unfocusedContainerColor = Color.Transparent,
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent,
                    ),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        color = if (type == "expense") WatermelonRed else IncomeGreen,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 42.sp,
                    ),
                    modifier = Modifier.fillMaxWidth(if (currencySymbol.isBlank()) 1f else 0.84f),
                )
            }
        }

        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center,
            ) {
                ThemedSegmentedToggle(
                    options = listOf("支出", "收入"),
                    selectedIndex = if (type == "expense") 0 else 1,
                    onSelectedChange = { index ->
                        type = if (index == 0) "expense" else "income"
                    },
                    accentColor = selectedColor,
                    textSizeSp = 14,
                    horizontalPadding = 24.dp,
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(28.dp), glowColor = MintGreen.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(text = "选择分类", color = WarmBrownMuted, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                CategoryFlow(
                    categories = categories,
                    selectedCategory = selectedCategory,
                    selectedColor = selectedColor,
                    onSelect = { selectedCategory = it },
                )
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(28.dp), glowColor = MintGreen.copy(alpha = 0.16f))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.DateRange,
                            contentDescription = null,
                            tint = WarmBrown.copy(alpha = 0.62f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(text = "日期", color = WarmBrown, fontWeight = FontWeight.Bold)
                    }
                    Row(
                        modifier = Modifier
                            .background(Color.White.copy(alpha = 0.72f), RoundedCornerShape(999.dp))
                            .appPressable { showDatePicker = true }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(text = formatRecordDate(recordDateMillis), color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Rounded.EditNote,
                            contentDescription = null,
                            tint = WarmBrown.copy(alpha = 0.62f),
                            modifier = Modifier.size(16.dp),
                        )
                        Text(text = "备注", color = WarmBrown, fontWeight = FontWeight.Bold)
                    }
                    TextField(
                        value = remarkInput,
                        onValueChange = { remarkInput = it },
                        placeholder = { Text("写点什么吧...", color = WarmBrown.copy(alpha = 0.35f), fontSize = 13.sp) },
                        singleLine = true,
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        textStyle = androidx.compose.ui.text.TextStyle(
                            color = WarmBrown,
                            fontWeight = FontWeight.Medium,
                            fontSize = 14.sp,
                        ),
                        modifier = Modifier.fillMaxWidth(0.65f),
                    )
                }
            }
        }

        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.horizontalGradient(if (type == "expense") {
                            listOf(WatermelonRed, MintGreen)
                        } else {
                            listOf(IncomeGreen, IncomeGreen.copy(alpha = 0.9f))
                        }),
                        shape = RoundedCornerShape(999.dp),
                    )
                    .appPressable {
                        val amount = amountInput.toDoubleOrNull()
                        if (amount == null || amount <= 0.0) {
                            saveTip = "请输入正确金额"
                        } else {
                            viewModel.addManualTransaction(
                                type = if (type == "expense") 0 else 1,
                                amount = amount,
                                categoryName = selectedCategory.ifBlank { "其他" },
                                categoryIcon = selectedCategory.firstOrNull()?.toString() ?: "",
                                remark = remarkInput.ifBlank { if (type == "expense") "手动支出" else "手动收入" },
                                recordTimestamp = recordDateMillis,
                            )
                            saveTip = "已保存，账单已更新"
                            onBack()
                        }
                    }
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(text = "保存记录", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
            }
        }

        if (saveTip != null && saveTip!!.contains("正确")) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(text = saveTip ?: "", color = WatermelonRed, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    if (showDatePicker) {
        val pickerState = androidx.compose.material3.rememberDatePickerState(initialSelectedDateMillis = recordDateMillis)
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        recordDateMillis = pickerState.selectedDateMillis ?: recordDateMillis
                        showDatePicker = false
                    },
                ) {
                    Text(text = "确定")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text(text = "取消")
                }
            },
        ) {
            DatePicker(state = pickerState, showModeToggle = false)
        }
    }
}

private fun formatRecordDate(timestamp: Long): String {
    return SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).format(Date(timestamp))
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CategoryFlow(
    categories: List<String>,
    selectedCategory: String,
    selectedColor: Color,
    onSelect: (String) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        categories.forEach { option ->
            CategoryChip(
                name = option,
                selectedColor = selectedColor,
                selected = selectedCategory == option,
                onClick = { onSelect(option) },
            )
        }
    }
}

@Composable
private fun CategoryChip(
    name: String,
    selectedColor: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = if (selected) selectedColor.copy(alpha = 0.2f) else Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
            )
            .appPressable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            imageVector = resolveCategoryIcon(name),
            contentDescription = name,
            tint = if (selected) selectedColor else WarmBrown,
            modifier = Modifier.size(16.dp),
        )
        Text(
            text = name,
            color = if (selected) selectedColor else WarmBrown,
            fontWeight = FontWeight.Bold,
            fontSize = 12.sp,
        )
    }
}
