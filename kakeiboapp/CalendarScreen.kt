package com.example.kakeiboapp

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakeiboapp.data.CategoryNames
import com.example.kakeiboapp.data.PaymentNames
import com.example.kakeiboapp.data.PaymentTypes
import com.example.kakeiboapp.data.Transaction
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: TransactionViewModel, modifier: Modifier = Modifier) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDailyDetailDialog by remember { mutableStateOf(false) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    val transactions by viewModel.getTransactionsByMonth(currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))).collectAsState(initial = emptyList())
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val holidays by viewModel.holidays.collectAsState()
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList())

    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("先月") }
            Text(text = "${currentMonth.year}年 ${currentMonth.monthValue}月", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showYearMonthPicker = true }.padding(8.dp))
            Button(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text("来月") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("■ 収入", color = Color.Blue, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("■ 支出", color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("■ ｸﾚｼﾞｯﾄ", color = CreditColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            Text("■ 引落/ﾁｬｰｼﾞ", color = ChargeColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        val daysOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day -> Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, fontSize = 14.sp, color = if (day == "日") Color.Red else if (day == "土") Color.Blue else Color.Black) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        val daysInMonth = currentMonth.lengthOfMonth()
        val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7
        val calendarItems = List(firstDayOfWeek) { null } + (1..daysInMonth).toList()

        LazyVerticalGrid(columns = GridCells.Fixed(7), modifier = Modifier.weight(1f)) {
            items(calendarItems) { day ->
                if (day != null) {
                    val date = currentMonth.atDay(day)
                    val dailyTransactions = transactions.filter { it.date == date.toString() }

                    val incomeSum = dailyTransactions.calculateTotalIncome()
                    
                    // クレジット以外の通常の支出
                    val regularExpenseSum = dailyTransactions.filter { tx ->
                        val method = paymentMethods.find { it.name == tx.paymentMethod }
                        val isCredit = method?.type == PaymentTypes.CREDIT || tx.paymentMethod == PaymentNames.CREDIT
                        tx.isExpense && !tx.isCreditPayment && !isCredit && !tx.isCharge
                    }.sumOf { it.amount }
                    
                    // クレジット利用
                    val creditUsageSum = dailyTransactions.filter { tx ->
                        val method = paymentMethods.find { it.name == tx.paymentMethod }
                        val isCredit = method?.type == PaymentTypes.CREDIT || tx.paymentMethod == PaymentNames.CREDIT
                        tx.isExpense && isCredit && !tx.isCreditPayment && !tx.isCharge
                    }.sumOf { it.amount }
                    
                    val chargeAndPaymentSum = dailyTransactions.filter { (it.isCreditPayment || it.isCharge) && it.category != CategoryNames.BALANCE_ADJUSTMENT }.sumOf { it.amount }

                    val isHoliday = holidays.contains(date)
                    val isSunday = date.dayOfWeek.value == 7
                    val isSaturday = date.dayOfWeek.value == 6
                    val isToday = date == today
                    
                    val dateColor = when { isSunday || isHoliday -> Color.Red; isSaturday -> Color.Blue; else -> Color.Black }
                    
                    // 今日のハイライト用の修飾子
                    var boxModifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 85.dp)
                        .padding(2.dp)
                        .background(CalendarCellColor, shape = MaterialTheme.shapes.small)
                        
                    if (isToday) {
                        boxModifier = boxModifier.border(2.dp, Color(0xFF42A5F5), MaterialTheme.shapes.small)
                    }
                        
                    boxModifier = boxModifier
                        .clickable { selectedDate = date; showDailyDetailDialog = true }
                        .padding(top = 4.dp, bottom = 4.dp, start = 1.dp, end = 1.dp)

                    Box(modifier = boxModifier, contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            // 今日の場合は日付の背景を丸く青にする
                            if (isToday) {
                                Box(
                                    modifier = Modifier
                                        .clip(CircleShape)
                                        .background(Color(0xFF42A5F5))
                                        .padding(horizontal = 6.dp, vertical = 2.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(text = day.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = Color.White)
                                }
                            } else {
                                Text(text = day.toString(), fontSize = 14.sp, fontWeight = FontWeight.Bold, color = dateColor, modifier = Modifier.padding(bottom = 2.dp))
                            }
                            
                            val amountStyle = androidx.compose.ui.text.TextStyle(fontSize = 10.sp, lineHeight = 10.sp, textAlign = TextAlign.Center)
                            if (incomeSum > 0) Text(text = "+$incomeSum", color = Color.Blue, style = amountStyle)
                            if (regularExpenseSum > 0) Text(text = "-$regularExpenseSum", color = Color.Red, style = amountStyle)
                            else if (regularExpenseSum < 0) Text(text = "$regularExpenseSum", color = Color(0xFF2E7D32), style = amountStyle)
                            if (creditUsageSum > 0) Text(text = "-$creditUsageSum", color = CreditColor, style = amountStyle)
                            if (chargeAndPaymentSum > 0) Text(text = "±$chargeAndPaymentSum", color = ChargeColor, style = amountStyle)
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 85.dp).padding(2.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val monthlyIncome = transactions.calculateTotalIncome()
        val monthlyExpense = transactions.calculateTotalExpense(aggregateOnUsage, paymentMethods)
        val monthlyBalance = monthlyIncome - monthlyExpense
        val balanceColor = if (monthlyBalance >= 0) Color.Blue else Color.Red
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SummaryCardColor)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("${currentMonth.monthValue}月 合計", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("収入: +$monthlyIncome", color = Color.Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("支出: -$monthlyExpense", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                Text("収支: $monthlyBalance", color = balanceColor, fontWeight = FontWeight.Bold, fontSize = 20.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (showDailyDetailDialog && selectedDate != null) DailyDetailDialog(date = selectedDate!!, transactions = transactions.filter { it.date == selectedDate.toString() }, viewModel = viewModel, onDismiss = { showDailyDetailDialog = false })
    if (showYearMonthPicker) { YearMonthPickerDialog(initialYearMonth = currentMonth, onDismissRequest = { showYearMonthPicker = false }, onYearMonthSelected = { selected -> currentMonth = selected; showYearMonthPicker = false }) }
}
