package com.example.kakeiboapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.PaymentNames
import com.example.kakeiboapp.data.PaymentTypes
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YearlySummaryScreen(viewModel: TransactionViewModel) {
    var selectedTabIndex by remember { mutableStateOf(0) } // 0: 月間, 1: 年間
    // 月間用ステート
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    var showYearMonthPicker by remember { mutableStateOf(false) }
    // 年間用ステート
    var currentYear by remember { mutableStateOf(LocalDate.now().year) }
    var showYearPicker by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize()) {
        PrimaryTabRow(selectedTabIndex = selectedTabIndex) {
            Tab(selected = selectedTabIndex == 0, onClick = { selectedTabIndex = 0 }, text = { Text("月間収支") })
            Tab(selected = selectedTabIndex == 1, onClick = { selectedTabIndex = 1 }, text = { Text("年間収支") })
        }
        
        Box(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            if (selectedTabIndex == 0) {
                MonthlySummaryView(
                    viewModel = viewModel,
                    currentYearMonth = currentYearMonth,
                    onYearMonthChange = { currentYearMonth = it },
                    onShowPicker = { showYearMonthPicker = true }
                )
            } else {
                YearlySummaryView(
                    viewModel = viewModel,
                    currentYear = currentYear,
                    onYearChange = { currentYear = it },
                    onShowPicker = { showYearPicker = true },
                    onMonthSelected = { month ->
                        currentYearMonth = YearMonth.of(currentYear, month)
                        selectedTabIndex = 0
                    }
                )
            }
        }
    }

    if (showYearMonthPicker) {
        YearMonthPickerDialog(
            initialYearMonth = currentYearMonth,
            onDismissRequest = { showYearMonthPicker = false },
            onYearMonthSelected = { selected ->
                currentYearMonth = selected
                showYearMonthPicker = false
            }
        )
    }

    if (showYearPicker) {
        YearPickerDialog(
            initialYear = currentYear,
            onDismissRequest = { showYearPicker = false },
            onYearSelected = { selected ->
                currentYear = selected
                showYearPicker = false
            }
        )
    }
}

@Composable
fun YearlySummaryView(
    viewModel: TransactionViewModel,
    currentYear: Int,
    onYearChange: (Int) -> Unit,
    onShowPicker: () -> Unit,
    onMonthSelected: (Int) -> Unit
) {
    val transactions by viewModel.getTransactionsByYear(currentYear.toString()).collectAsState(initial = emptyList())
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList())

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onYearChange(currentYear - 1) }) { Text("前年") }
            Text(
                text = "${currentYear}年",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onShowPicker() }.padding(8.dp)
            )
            Button(onClick = { onYearChange(currentYear + 1) }) { Text("翌年") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        val totalIncome = transactions.calculateTotalIncome()
        val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage, paymentMethods)
        val balance = totalIncome - totalExpense
        val balanceColor = if (balance >= 0) Color.Blue else Color.Red

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SummaryCardColor)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("年間合計", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("収入: +$totalIncome", color = Color.Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("支出: -$totalExpense", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("収支: ${if(balance>0) "+" else ""}$balance", color = balanceColor, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("月別明細", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 16.sp)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(12) { index ->
                val month = index + 1
                val monthString = "%04d-%02d".format(currentYear, month)
                val monthTransactions = transactions.filter { it.date.startsWith(monthString) }
                val mIncome = monthTransactions.calculateTotalIncome()
                val mExpense = monthTransactions.calculateTotalExpense(aggregateOnUsage, paymentMethods)
                val mBalance = mIncome - mExpense

                if (mIncome > 0 || mExpense > 0) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onMonthSelected(month) }
                            .padding(vertical = 12.dp, horizontal = 4.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("${month}月", fontSize = 20.sp, fontWeight = FontWeight.Medium)
                        Column(horizontalAlignment = Alignment.End) {
                            if (mIncome > 0) Text("収入: +$mIncome", color = Color.Blue, fontSize = 14.sp)
                            if (mExpense > 0) Text("支出: -$mExpense", color = Color.Red, fontSize = 14.sp)
                            Text("収支: ${if(mBalance>0) "+" else ""}$mBalance", color = if (mBalance >= 0) Color.Blue else Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(top=2.dp))
                        }
                    }
                    HorizontalDivider(color = Color.LightGray)
                }
            }
        }
    }
}

@Composable
fun MonthlySummaryView(
    viewModel: TransactionViewModel,
    currentYearMonth: YearMonth,
    onYearMonthChange: (YearMonth) -> Unit,
    onShowPicker: () -> Unit
) {
    val monthString = currentYearMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    val transactions by viewModel.getTransactionsByMonth(monthString).collectAsState(initial = emptyList())
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList())

    val totalIncome = transactions.calculateTotalIncome()
    val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage, paymentMethods)
    val balance = totalIncome - totalExpense
    val balanceColor = if (balance >= 0) Color.Blue else Color.Red

    // 日別集計
    val dailyMap = transactions.groupBy { it.date }.toSortedMap(reverseOrder())

    // カテゴリ集計（支出・収入）
    val expenseTransactions = transactions.filter { it.isExpense && !it.isCharge }.filter { tx ->
        if (tx.isVirtual) true
        else if (aggregateOnUsage) !tx.isCreditPayment
        else {
            val method = paymentMethods.find { m -> m.name == tx.paymentMethod }
            val isCreditType = method?.type == PaymentTypes.CREDIT || tx.paymentMethod == PaymentNames.CREDIT
            !(isCreditType && !tx.isCreditPayment)
        }
    }
    val incomeTransactions = transactions.filter { !it.isExpense && !it.isCharge && !it.isCreditPayment }

    val expenseCategoryTotals = expenseTransactions.groupBy { it.category }.mapValues { (_, txs) -> txs.sumOf { it.amount } }.filterValues { it != 0 }.toList().sortedByDescending { it.second }
    val incomeCategoryTotals = incomeTransactions.groupBy { it.category }.mapValues { (_, txs) -> txs.sumOf { it.amount } }.filterValues { it > 0 }.toList().sortedByDescending { it.second }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { onYearMonthChange(currentYearMonth.minusMonths(1)) }) { Text("前月") }
            Text(
                text = "${currentYearMonth.year}年 ${currentYearMonth.monthValue}月",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.clickable { onShowPicker() }.padding(8.dp)
            )
            Button(onClick = { onYearMonthChange(currentYearMonth.plusMonths(1)) }) { Text("来月") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SummaryCardColor)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("月間合計", fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("収入: +$totalIncome", color = Color.Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("支出: -$totalExpense", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("収支: ${if(balance>0) "+" else ""}$balance", color = balanceColor, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (incomeCategoryTotals.isNotEmpty() || expenseCategoryTotals.isNotEmpty()) {
                item {
                    Text("カテゴリー別内訳", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 16.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                if (expenseCategoryTotals.isNotEmpty()) {
                    item { Text("■ 支出", color = Color.Red, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom=4.dp)) }
                    items(expenseCategoryTotals) { (cat, amount) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical=4.dp, horizontal=8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, fontSize = 14.sp)
                            val catAmountText = if (amount < 0) "$amount" else "-$amount"
                            val catColor = if (amount < 0) Color(0xFF2E7D32) else Color.Red
                            Text(catAmountText, color = catColor, fontSize = 14.sp)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
                if (incomeCategoryTotals.isNotEmpty()) {
                    item { Text("■ 収入", color = Color.Blue, fontSize = 14.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom=4.dp)) }
                    items(incomeCategoryTotals) { (cat, amount) ->
                        Row(modifier = Modifier.fillMaxWidth().padding(vertical=4.dp, horizontal=8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat, fontSize = 14.sp)
                            Text("+$amount", color = Color.Blue, fontSize = 14.sp)
                        }
                    }
                    item { Spacer(modifier = Modifier.height(8.dp)) }
                }
            }

            if (dailyMap.isNotEmpty()) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("日別明細", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 16.sp)
                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                }
                items(dailyMap.toList()) { (dateStr, dayTxs) ->
                    val dIncome = dayTxs.calculateTotalIncome()
                    val dExpense = dayTxs.calculateTotalExpense(aggregateOnUsage, paymentMethods)
                    val dBalance = dIncome - dExpense
                    if (dIncome > 0 || dExpense != 0) {
                        val parsedDate = LocalDate.parse(dateStr)
                        val dateFormatted = parsedDate.format(DateTimeFormatter.ofPattern("MM/dd"))
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(dateFormatted, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(horizontalAlignment = Alignment.End, modifier = Modifier.padding(end = 12.dp)) {
                                    if (dIncome > 0) Text("+$dIncome", color = Color.Blue, fontSize = 13.sp)
                                    if (dExpense > 0) Text("-$dExpense", color = Color.Red, fontSize = 13.sp)
                                    else if (dExpense < 0) Text("$dExpense", color = Color(0xFF2E7D32), fontSize = 13.sp)
                                }
                                Text(
                                    text = "${if(dBalance>0) "+" else ""}$dBalance",
                                    color = if (dBalance >= 0) Color.Blue else Color.Red,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 15.sp,
                                    modifier = Modifier.width(60.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.End
                                )
                            }
                        }
                        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
            item { Spacer(modifier = Modifier.height(40.dp)) }
        }
    }
}
