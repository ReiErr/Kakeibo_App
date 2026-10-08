package com.example.kakeiboapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kakeiboapp.data.PaymentNames
import com.example.kakeiboapp.data.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyDetailDialog(date: LocalDate, transactions: List<Transaction>, viewModel: TransactionViewModel, onDismiss: () -> Unit) {
    var showInputBottomSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val totalIncome = transactions.calculateTotalIncome()
    val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage)

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.8f).padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Scaffold(floatingActionButton = { FloatingActionButton(onClick = { editingTransaction = null; showInputBottomSheet = true }) { Icon(Icons.Filled.Add, contentDescription = "追加") } }) { innerPadding ->
                Column(modifier = Modifier.fillMaxSize().padding(innerPadding).padding(16.dp)) {
                    Text(text = date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")), fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("収入合計: +$totalIncome", color = Color.Blue, fontWeight = FontWeight.Bold)
                        Text("支出合計: -$totalExpense", color = Color.Red, fontWeight = FontWeight.Bold)
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))
                    if (transactions.isEmpty()) { Text("この日の登録はありません", color = Color.Gray) } else {
                        LazyColumn {
                            items(transactions) { transaction ->
                                TransactionItemRow(transaction = transaction, onEditClick = { editingTransaction = transaction; showInputBottomSheet = true }, onDeleteClick = { transactionToDelete = transaction })
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
    if (transactionToDelete != null) {
        AlertDialog(onDismissRequest = { transactionToDelete = null }, title = { Text("削除の確認") }, text = { Text("「${transactionToDelete?.title}」を削除しますか？") }, confirmButton = { TextButton(onClick = { viewModel.deleteTransaction(transactionToDelete!!); transactionToDelete = null }) { Text("削除する", color = Color.Red) } }, dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("キャンセル") } })
    }
    if (showInputBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showInputBottomSheet = false }) {
            TransactionInputForm(date = date, initialTransaction = editingTransaction, viewModel = viewModel, onSave = { title, amount, isExpense, category, paymentMethod, isCharge, chargeSource ->
                if (editingTransaction == null) {
                    viewModel.addTransaction(title, amount, isExpense, category, paymentMethod, date.toString(), isCharge, chargeSource)
                } else {
                    val updated = editingTransaction!!.copy(title = title, amount = amount, isExpense = isExpense, category = category, paymentMethod = paymentMethod, isCharge = isCharge, chargeSource = chargeSource)
                    viewModel.updateTransaction(updated)
                }
                showInputBottomSheet = false
            })
        }
    }
}

@Composable
fun TransactionItemRow(transaction: Transaction, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    val amountColor = if (transaction.isCharge || transaction.isCreditPayment) ChargeColor else if (!transaction.isExpense) Color.Blue else if (transaction.paymentMethod == PaymentNames.CREDIT) CreditColor else Color.Red
    val amountPrefix = if (transaction.isCharge) "±" else if (transaction.isExpense) "-" else "+"

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val methodText = if (transaction.isCharge) " (${transaction.chargeSource} → ${transaction.paymentMethod})" else if (transaction.id > 0) " (${transaction.paymentMethod})" else ""
            Text(text = "${transaction.category}$methodText", fontSize = 12.sp, color = Color.Gray)
        }
        Text(text = "$amountPrefix${transaction.amount}円", color = amountColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))

        if (transaction.isVirtual) {
            Text("自動生成", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
        } else if (!transaction.isCreditPayment) {
            Row {
                IconButton(onClick = onEditClick) { Icon(Icons.Filled.Edit, contentDescription = "編集", tint = Color.Gray) }
                IconButton(onClick = onDeleteClick) { Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red) }
            }
        } else {
            Text("自動計算", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
        }
    }
}
