package com.qcb.keepaccounts.ui.screens

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.MintGreenSoft
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel

private data class CategoryOption(
    val icon: String,
    val name: String,
)

private val expenseCategories = listOf(
    CategoryOption("🍜", "餐饮"),
    CategoryOption("🚗", "交通"),
    CategoryOption("🛍️", "购物"),
    CategoryOption("🏠", "居家"),
    CategoryOption("🎮", "娱乐"),
)

private val incomeCategories = listOf(
    CategoryOption("💰", "工资"),
    CategoryOption("🎁", "红包"),
    CategoryOption("🧩", "兼职"),
    CategoryOption("📈", "理财"),
)

@Composable
fun ManualEntryScreen(
    viewModel: MainViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var isExpense by remember { mutableStateOf(true) }
    var amountInput by remember { mutableStateOf("") }
    var remarkInput by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableIntStateOf(0) }
    var saveTip by remember { mutableStateOf<String?>(null) }

    val categories = if (isExpense) expenseCategories else incomeCategories
    if (selectedCategory >= categories.size) selectedCategory = 0

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 14.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
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
                    modifier = Modifier.clickable { onBack() },
                )
                Text(text = "手动记一笔", color = WarmBrown, fontWeight = FontWeight.ExtraBold)
                Spacer(modifier = Modifier.width(40.dp))
            }
        }

        item {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .glassCard(shape = RoundedCornerShape(30.dp), glowColor = MintGreen.copy(alpha = 0.2f))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(text = "快速录入 ✍️", color = WarmBrown, fontWeight = FontWeight.ExtraBold)

                Row(
                    modifier = Modifier
                        .background(Color.White.copy(alpha = 0.6f), RoundedCornerShape(999.dp))
                        .padding(4.dp),
                ) {
                    ToggleChip(
                        text = "支出",
                        selected = isExpense,
                        onClick = { isExpense = true },
                    )
                    ToggleChip(
                        text = "收入",
                        selected = !isExpense,
                        onClick = { isExpense = false },
                    )
                }

                OutlinedTextField(
                    value = amountInput,
                    onValueChange = { amountInput = it.filter { ch -> ch.isDigit() || ch == '.' } },
                    label = { Text("金额") },
                    placeholder = { Text("例如 36.50") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.65f),
                        focusedContainerColor = Color.White.copy(alpha = 0.72f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = remarkInput,
                    onValueChange = { remarkInput = it },
                    label = { Text("备注") },
                    placeholder = { Text("例如 牛肉粉丝汤") },
                    singleLine = true,
                    shape = RoundedCornerShape(18.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = MintGreen,
                        unfocusedBorderColor = Color.White.copy(alpha = 0.65f),
                        focusedContainerColor = Color.White.copy(alpha = 0.72f),
                        unfocusedContainerColor = Color.White.copy(alpha = 0.55f),
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                Text(text = "选择分类", color = WarmBrownMuted, fontWeight = FontWeight.Bold)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    itemsIndexed(categories) { index, option ->
                        CategoryChip(
                            option = option,
                            selected = index == selectedCategory,
                            onClick = { selectedCategory = index },
                        )
                    }
                }
            }
        }

        item {
            Button(
                onClick = {
                    val amount = amountInput.toDoubleOrNull()
                    if (amount == null || amount <= 0.0) {
                        saveTip = "请输入正确金额"
                    } else {
                        val category = categories[selectedCategory]
                        viewModel.addManualTransaction(
                            type = if (isExpense) 0 else 1,
                            amount = amount,
                            categoryName = category.name,
                            categoryIcon = category.icon,
                            remark = remarkInput.ifBlank { if (isExpense) "手动支出" else "手动收入" },
                            recordTimestamp = System.currentTimeMillis(),
                        )
                        saveTip = "已保存，账单已更新"
                        amountInput = ""
                        remarkInput = ""
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(999.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(),
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                listOf(MintGreen, MintGreenSoft),
                            ),
                            shape = RoundedCornerShape(999.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = "保存这笔账单", color = Color.White, fontWeight = FontWeight.ExtraBold)
                }
            }
        }

        if (saveTip != null) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .glassCard(shape = RoundedCornerShape(20.dp), glowColor = MintGreen.copy(alpha = 0.12f))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                ) {
                    Text(
                        text = saveTip ?: "",
                        color = if (saveTip?.contains("正确") == true) WatermelonRed else WarmBrown,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }
    }
}

@Composable
private fun ToggleChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .background(
                color = if (selected) Color.White else Color.Transparent,
                shape = RoundedCornerShape(999.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Text(
            text = text,
            color = if (selected) WarmBrown else WarmBrown.copy(alpha = 0.5f),
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun CategoryChip(
    option: CategoryOption,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .background(
                color = if (selected) Color.White.copy(alpha = 0.92f) else Color.White.copy(alpha = 0.6f),
                shape = RoundedCornerShape(14.dp),
            )
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = option.icon)
        Text(text = option.name, color = WarmBrown, fontWeight = FontWeight.Bold)
    }
}
