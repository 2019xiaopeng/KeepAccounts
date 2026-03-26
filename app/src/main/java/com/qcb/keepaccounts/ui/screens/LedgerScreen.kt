package com.qcb.keepaccounts.ui.screens

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.patrykandpatrick.vico.compose.axis.horizontal.rememberBottomAxis
import com.patrykandpatrick.vico.compose.axis.vertical.rememberStartAxis
import com.patrykandpatrick.vico.compose.chart.Chart
import com.patrykandpatrick.vico.compose.chart.line.lineSpec
import com.patrykandpatrick.vico.compose.chart.line.lineChart
import com.patrykandpatrick.vico.compose.component.shape.shader.verticalGradient
import com.patrykandpatrick.vico.core.entry.entryOf
import com.patrykandpatrick.vico.core.entry.entryModelOf
import com.qcb.keepaccounts.data.local.entity.TransactionEntity
import com.qcb.keepaccounts.ui.components.glassCard
import com.qcb.keepaccounts.ui.theme.MintGreen
import com.qcb.keepaccounts.ui.theme.MintGreenSoft
import com.qcb.keepaccounts.ui.theme.PeachIncome
import com.qcb.keepaccounts.ui.theme.WarmBrown
import com.qcb.keepaccounts.ui.theme.WarmBrownMuted
import com.qcb.keepaccounts.ui.theme.WatermelonPink
import com.qcb.keepaccounts.ui.theme.WatermelonRed
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel

@Composable
fun LedgerScreen(
    viewModel: MainViewModel,
    modifier: Modifier = Modifier,
) {
    val transactions by viewModel.transactions.collectAsStateWithLifecycle()

    val expense = transactions.filter { it.type == 0 }.sumOf { it.amount }
    val income = transactions.filter { it.type == 1 }.sumOf { it.amount }
    val balance = income - expense

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Text(
                text = "统计报表 📊",
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        item {
            SummaryCard(expense = expense, income = income, balance = balance)
        }

        item {
            ChartCard(transactions = transactions)
        }

        item {
            Text(
                text = "分类流水",
                color = WarmBrown,
                fontWeight = FontWeight.ExtraBold,
            )
        }

        items(items = transactions, key = { it.id }) { transaction ->
            LedgerListItem(transaction = transaction)
        }

        item { Spacer(modifier = Modifier.height(88.dp)) }
    }
}

@Composable
private fun SummaryCard(
    expense: Double,
    income: Double,
    balance: Double,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(26.dp))
            .padding(14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        SummaryCell(title = "总支出", value = "¥${"%.2f".format(expense)}", color = WatermelonRed)
        SummaryCell(title = "总收入", value = "¥${"%.2f".format(income)}", color = PeachIncome)
        SummaryCell(title = "结余", value = "¥${"%.2f".format(balance)}", color = MintGreen)
    }
}

@Composable
private fun SummaryCell(
    title: String,
    value: String,
    color: Color,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = title, color = WarmBrownMuted, fontWeight = FontWeight.Medium)
        Text(text = value, color = color, fontWeight = FontWeight.ExtraBold)
    }
}

@Composable
private fun ChartCard(transactions: List<TransactionEntity>) {
    val expensePoints: List<com.patrykandpatrick.vico.core.entry.FloatEntry>
    val incomePoints: List<com.patrykandpatrick.vico.core.entry.FloatEntry>

    if (transactions.isEmpty()) {
        expensePoints = listOf(entryOf(1f, 0f))
        incomePoints = listOf(entryOf(1f, 0f))
    } else {
        val reversed = transactions.takeLast(7).reversed()
        expensePoints = reversed.mapIndexed { index, item ->
            entryOf((index + 1).toFloat(), if (item.type == 0) item.amount.toFloat() else 0f)
        }
        incomePoints = reversed.mapIndexed { index, item ->
            entryOf((index + 1).toFloat(), if (item.type == 1) item.amount.toFloat() else 0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(30.dp))
            .padding(14.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "近 7 条趋势",
                color = WarmBrown,
                fontWeight = FontWeight.Bold,
            )

            Chart(
                chart = lineChart(
                    lines = listOf(
                        lineSpec(
                            lineColor = WatermelonRed,
                            lineBackgroundShader = verticalGradient(
                                colors = arrayOf(
                                    WatermelonRed.copy(alpha = 0.30f),
                                    WatermelonPink.copy(alpha = 0.06f),
                                ),
                            ),
                        ),
                        lineSpec(
                            lineColor = MintGreen,
                            lineBackgroundShader = verticalGradient(
                                colors = arrayOf(
                                    MintGreen.copy(alpha = 0.30f),
                                    MintGreenSoft.copy(alpha = 0.06f),
                                ),
                            ),
                        ),
                    ),
                ),
                model = entryModelOf(expensePoints, incomePoints),
                startAxis = rememberStartAxis(),
                bottomAxis = rememberBottomAxis(),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
            )
        }
    }
}

@Composable
private fun LedgerListItem(transaction: TransactionEntity) {
    val isIncome = transaction.type == 1
    val amountColor = if (isIncome) MintGreen else WatermelonRed
    val amountPrefix = if (isIncome) "+" else "-"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassCard(shape = RoundedCornerShape(22.dp), glowColor = MintGreen.copy(alpha = 0.2f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .height(30.dp)
                    .glassCard(shape = RoundedCornerShape(12.dp), glowColor = Color.Transparent)
                    .padding(horizontal = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = transaction.categoryIcon)
            }
            Column {
                Text(text = transaction.categoryName, color = WarmBrown, fontWeight = FontWeight.Bold)
                Text(text = transaction.remark, color = WarmBrownMuted, fontWeight = FontWeight.Medium)
            }
        }

        Text(
            text = "$amountPrefix¥${"%.2f".format(transaction.amount)}",
            color = amountColor,
            fontWeight = FontWeight.ExtraBold,
        )
    }
}
