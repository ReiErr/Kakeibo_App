package com.example.kakeiboapp

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakeiboapp.data.Transaction
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalysisScreen(viewModel: TransactionViewModel, modifier: Modifier = Modifier) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    val transactions by viewModel.getTransactionsByMonth(currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))).collectAsState(initial = emptyList())
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList())

    // 支出のみ抽出し、チャージやクレジットの重複を弾く
    val expenseTransactions = transactions.filter { it.isExpense && !it.isCharge }.filter { tx ->
        if (tx.isVirtual) {
            true
        } else if (aggregateOnUsage) {
            !tx.isCreditPayment
        } else {
            val method = paymentMethods.find { m -> m.name == tx.paymentMethod }
            val isCreditType = method?.type == com.example.kakeiboapp.data.PaymentTypes.CREDIT || tx.paymentMethod == com.example.kakeiboapp.data.PaymentNames.CREDIT
            !(isCreditType && !tx.isCreditPayment)
        }
    }

    // カテゴリーごとに集計
    val categoryTotals = expenseTransactions.groupBy { it.category }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .filterValues { it > 0 }
        .toList()
        .sortedByDescending { it.second }

    // 決済手段ごとに集計（指定なしは除く）
    val methodTotals = expenseTransactions
        .filter { it.paymentMethod != com.example.kakeiboapp.data.PaymentNames.NONE }
        .groupBy { it.paymentMethod }
        .mapValues { (_, txs) -> txs.sumOf { it.amount } }
        .filterValues { it > 0 }
        .toList()
        .sortedByDescending { it.second }

    var selectedTab by remember { mutableStateOf(0) } // 0: 決済手段別, 1: カテゴリー別
    val displayTotals = if (selectedTab == 0) methodTotals else categoryTotals
    val totalExpense = displayTotals.sumOf { it.second }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        // 月選択
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("先月") }
            Text(
                text = "${currentMonth.year}年 ${currentMonth.monthValue}月",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { showYearMonthPicker = true }
                    .padding(8.dp)
            )
            Button(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text("来月") }
        }
        Spacer(modifier = Modifier.height(8.dp))

        TabRow(selectedTabIndex = selectedTab) {
            Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }, text = { Text("決済手段別", fontWeight = FontWeight.Bold) })
            Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }, text = { Text("カテゴリー別", fontWeight = FontWeight.Bold) })
        }
        Spacer(modifier = Modifier.height(16.dp))

        if (totalExpense == 0) {
            Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("この月の対象支出はありません", color = Color.Gray)
            }
        } else {
            // 円グラフ描画
            val colors = listOf(
                Color(0xFFE57373), Color(0xFF81C784), Color(0xFF64B5F6),
                Color(0xFFFFD54F), Color(0xFFBA68C8), Color(0xFF4DB6AC),
                Color(0xFFFF8A65), Color(0xFFAED581), Color(0xFF7986CB)
            )
            
            Box(modifier = Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.size(200.dp)) {
                    var startAngle = -90f
                    displayTotals.forEachIndexed { index, pair ->
                        val sweepAngle = (pair.second.toFloat() / totalExpense) * 360f
                        val color = colors[index % colors.size]
                        drawArc(
                            color = color,
                            startAngle = startAngle,
                            sweepAngle = sweepAngle,
                            useCenter = true,
                            size = Size(size.width, size.height)
                        )
                        startAngle += sweepAngle
                    }
                }
                // ドーナツ型の中心を白でくり抜く
                Canvas(modifier = Modifier.size(100.dp)) {
                    drawCircle(color = Color.White, radius = size.width / 2)
                }
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("支出合計", fontSize = 14.sp, color = Color.Gray)
                    Text("$totalExpense 円", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            Text(if (selectedTab == 0) "決済手段別内訳" else "カテゴリー別内訳", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 16.sp)
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            LazyColumn(modifier = Modifier.weight(1f)) {
                items(displayTotals.size) { index ->
                    val pair = displayTotals[index]
                    val color = colors[index % colors.size]
                    val percentage = (pair.second.toFloat() / totalExpense * 100).toInt()

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(16.dp)) { drawCircle(color) }
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(pair.first, fontSize = 18.sp)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text("${pair.second}円", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text("$percentage%", fontSize = 14.sp, color = Color.Gray)
                        }
                    }
                    HorizontalDivider(color = Color.LightGray)
                }
            }
        }
    }

    if (showYearMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = currentMonth,
            onDismissRequest = { showYearMonthPicker = false },
            onYearMonthSelected = { selected -> currentMonth = selected; showYearMonthPicker = false }
        )
    }
}
