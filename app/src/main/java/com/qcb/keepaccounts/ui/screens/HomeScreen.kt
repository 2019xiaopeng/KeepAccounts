package com.qcb.keepaccounts.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel
import kotlin.math.roundToInt

@Composable
fun HomeScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val monthlyBudget = 4000.0
    val expense = transactions.filter { it.type == 0 }.sumOf { it.amount }
    val remaining = (monthlyBudget - expense).coerceAtLeast(0.0)
    val progress = if (monthlyBudget == 0.0) 0f else (expense / monthlyBudget).toFloat().coerceIn(0f, 1f)

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            BudgetCard(
                monthlyBudget = monthlyBudget,
                expense = expense,
                remaining = remaining,
                progress = progress,
            )
        }

        item {
            Text(
                text = "最近记录",
                color = Color(0xFF5C544D),
                fontWeight = FontWeight.ExtraBold,
            )
        }

        if (transactions.isEmpty()) {
            item {
                Card(
                    shape = RoundedCornerShape(20.dp),
                    colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
                ) {
                    Text(
                        text = "还没有账单，正在准备样例数据...",
                        modifier = Modifier.padding(16.dp),
                        color = Color(0xFF8F8A84),
                    )
                }
            }
        } else {
            items(transactions) { transaction ->
                TransactionListItem(transaction = transaction)
            }
        }
    }
}

@Composable
private fun BudgetCard(
    monthlyBudget: Double,
    expense: Double,
    remaining: Double,
    progress: Float,
) {
    Card(
        shape = RoundedCornerShape(28.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.72f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = "本月预算",
                color = Color(0xFF5C544D),
                fontWeight = FontWeight.Bold,
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = "总额 ¥${"%.2f".format(monthlyBudget)}",
                    color = Color(0xFF5C544D),
                    fontWeight = FontWeight.ExtraBold,
                )
                Text(
                    text = "剩余 ¥${"%.2f".format(remaining)}",
                    color = Color(0xFF4FA97A),
                    fontWeight = FontWeight.Bold,
                )
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        color = Color(0xFFF1F4F1),
                        shape = RoundedCornerShape(999.dp),
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth(progress)
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color(0xFFFF8B94), Color(0xFFFFB6B9)),
                            ),
                            shape = RoundedCornerShape(999.dp),
                        )
                        .padding(vertical = 6.dp),
                ) {}
            }

            Text(
                text = "已用 ${ (progress * 100f).roundToInt() }% · 支出 ¥${"%.2f".format(expense)}",
                color = Color(0xFF8F8A84),
            )
        }
    }
}

@Composable
private fun TransactionListItem(transaction: TransactionEntity) {
    val isIncome = transaction.type == 1
    val amountPrefix = if (isIncome) "+" else "-"
    val amountColor = if (isIncome) Color(0xFF4FA97A) else Color(0xFFFF8B94)

    Card(
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White.copy(alpha = 0.75f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = transaction.categoryIcon)
                    Text(
                        text = transaction.categoryName,
                        color = Color(0xFF5C544D),
                        fontWeight = FontWeight.Bold,
                    )
                }

                Text(
                    text = "$amountPrefix¥${"%.2f".format(transaction.amount)}",
                    color = amountColor,
                    fontWeight = FontWeight.ExtraBold,
                )
            }

            if (transaction.remark.isNotBlank()) {
                HorizontalDivider(color = Color(0x1A5C544D))
                Text(
                    text = transaction.remark,
                    color = Color(0xFF8F8A84),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
