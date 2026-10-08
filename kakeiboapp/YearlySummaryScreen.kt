package com.example.kakeiboapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
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
import java.time.LocalDate

@Composable
fun YearlySummaryScreen(viewModel: TransactionViewModel) {
    var currentYear by remember { mutableStateOf(LocalDate.now().year) }
    var showYearPicker by remember { mutableStateOf(false) } // 年選択ダイアログの表示状態

    val transactions by viewModel.getTransactionsByYear(currentYear.toString()).collectAsState(initial = emptyList<Transaction>())
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentYear-- }) { Text("前年") }
            Text(
                text = "${currentYear}年 年間収支",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clickable { showYearPicker = true }
                    .padding(8.dp)
            )
            Button(onClick = { currentYear++ }) { Text("翌年") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        val totalIncome = transactions.calculateTotalIncome()
        val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage)
        val balance = totalIncome - totalExpense
        val balanceColor = if (balance >= 0) Color.Blue else Color.Red

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = SummaryCardColor)) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("年間合計", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("収入: +$totalIncome", color = Color.Blue, fontWeight = FontWeight.Bold)
                    Text("支出: -$totalExpense", color = Color.Red, fontWeight = FontWeight.Bold)
                }
                Text("収支: $balance", color = balanceColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }

        Spacer(modifier = Modifier.height(16.dp))
        Text("月別明細", fontWeight = FontWeight.Bold, color = Color.Gray)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            items(12) { index ->
                val month = index + 1
                val monthString = "%04d-%02d".format(currentYear, month)
                val monthTransactions = transactions.filter { it.date.startsWith(monthString) }
                val mIncome = monthTransactions.calculateTotalIncome()
                val mExpense = monthTransactions.calculateTotalExpense(aggregateOnUsage)

                if (mIncome > 0 || mExpense > 0) {
                    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("${month}月", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                        Column(horizontalAlignment = Alignment.End) {
                            if (mIncome > 0) Text("+$mIncome", color = Color.Blue, fontWeight = FontWeight.Bold)
                            if (mExpense > 0) Text("-$mExpense", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                    HorizontalDivider(color = Color.LightGray)
                }
            }
        }
    }

    // 年選択ダイアログ
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
