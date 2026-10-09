package com.example.kakeiboapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
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
import com.example.kakeiboapp.data.PaymentTypes
import com.example.kakeiboapp.data.Transaction
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyDetailDialog(date: LocalDate, transactions: List<Transaction>, viewModel: TransactionViewModel, onDismiss: () -> Unit) {
    var showInputBottomSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    var transactionToAdjust by remember { mutableStateOf<Transaction?>(null) }
    var actualAmountInput by remember { mutableStateOf("") }
    
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList())
    
    val totalIncome = transactions.calculateTotalIncome()
    val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage, paymentMethods)

    Dialog(onDismissRequest = onDismiss) {
        Card(modifier = Modifier.fillMaxWidth().fillMaxHeight(0.85f).padding(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
                Text(text = date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")), fontSize = 22.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(16.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("収入合計: +$totalIncome", color = Color.Blue, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("支出合計: -$totalExpense", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                }
                HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (transactions.isEmpty()) {
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text("この日の登録はありません", color = Color.Gray, fontSize = 16.sp)
                        }
                    } else {
                        LazyColumn(modifier = Modifier.fillMaxSize()) {
                            items(transactions) { transaction ->
                                TransactionItemRow(transaction = transaction, viewModel = viewModel, onEditClick = { editingTransaction = transaction; showInputBottomSheet = true }, onDeleteClick = { transactionToDelete = transaction }, onAdjustClick = { transactionToAdjust = transaction; actualAmountInput = transaction.amount.toString() })
                                HorizontalDivider()
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { editingTransaction = null; showInputBottomSheet = true },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("収支を追加", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
    if (transactionToDelete != null) {
        AlertDialog(onDismissRequest = { transactionToDelete = null }, title = { Text("削除の確認") }, text = { Text("「${transactionToDelete?.title}」を削除しますか？") }, confirmButton = { TextButton(onClick = { viewModel.deleteTransaction(transactionToDelete!!); transactionToDelete = null }) { Text("削除する", color = Color.Red) } }, dismissButton = { TextButton(onClick = { transactionToDelete = null }) { Text("キャンセル") } })
    }

    if (transactionToAdjust != null) {
        AlertDialog(
            onDismissRequest = { transactionToAdjust = null },
            title = { Text("確定請求額の端数調整", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("計算上の合計額: ${transactionToAdjust!!.amount} 円", fontSize = 16.sp, color = Color.Gray)
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("実際のクレジットカード請求額（引き落とし額）を入力してください。差額分が「端数調整」として自動的に計上されます。", fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = actualAmountInput,
                        onValueChange = { actualAmountInput = it },
                        label = { Text("実際の請求額") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val actualAmount = actualAmountInput.toIntOrNull()
                    if (actualAmount != null && actualAmount != transactionToAdjust!!.amount) {
                        viewModel.addCreditAdjustmentTransaction(transactionToAdjust!!, actualAmount)
                        transactionToAdjust = null
                        actualAmountInput = ""
                    }
                }) { Text("調整する") }
            },
            dismissButton = {
                TextButton(onClick = { transactionToAdjust = null; actualAmountInput = "" }) { Text("キャンセル") }
            }
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    if (showInputBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showInputBottomSheet = false }, sheetState = sheetState) {
            TransactionInputForm(date = date, initialTransaction = editingTransaction, viewModel = viewModel, onSave = { title, amount, isExpense, category, paymentMethod, isCharge, chargeSource, memo ->
                if (editingTransaction == null) {
                    viewModel.addTransaction(title, amount, isExpense, category, paymentMethod, date.toString(), isCharge, chargeSource, memo)
                } else {
                    val updated = editingTransaction!!.copy(title = title, amount = amount, isExpense = isExpense, category = category, paymentMethod = paymentMethod, isCharge = isCharge, chargeSource = chargeSource, memo = memo)
                    viewModel.updateTransaction(updated)
                }
                showInputBottomSheet = false
            })
        }
    }
}

@Composable
fun TransactionItemRow(transaction: Transaction, viewModel: TransactionViewModel, onEditClick: () -> Unit, onDeleteClick: () -> Unit, onAdjustClick: () -> Unit = {}) {
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList())
    val method = paymentMethods.find { it.name == transaction.paymentMethod }
    val isCreditType = method?.type == PaymentTypes.CREDIT || transaction.paymentMethod == PaymentNames.CREDIT
    
    val amountColor = when {
        transaction.isCharge || transaction.isCreditPayment -> ChargeColor
        transaction.amount < 0 -> Color(0xFF2E7D32) // マイナス支出（割引等）は緑色
        !transaction.isExpense -> Color.Blue
        isCreditType -> CreditColor
        else -> Color.Red
    }
    val amountText = when {
        transaction.isCharge -> "±${Math.abs(transaction.amount)}円"
        transaction.amount < 0 -> "${transaction.amount}円"
        transaction.isExpense -> "-${transaction.amount}円"
        else -> "+${transaction.amount}円"
    }

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.title, fontSize = 18.sp, fontWeight = FontWeight.Medium)
            val methodText = if (transaction.isCharge) " (${transaction.chargeSource} → ${transaction.paymentMethod})" else if (transaction.id > 0) " (${transaction.paymentMethod})" else ""
            Text(text = "${transaction.category}$methodText", fontSize = 14.sp, color = Color.Gray)
            if (transaction.memo.isNotBlank()) {
                Text(text = transaction.memo, fontSize = 14.sp, color = Color.DarkGray)
            }
        }
        Text(text = amountText, color = amountColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(end = 8.dp))

        if (transaction.isVirtual || transaction.isCreditPayment) {
            TextButton(onClick = onAdjustClick) { Text("調整", fontSize = 14.sp) }
        } else {
            Row {
                IconButton(onClick = onEditClick) { Icon(Icons.Filled.Edit, contentDescription = "編集", tint = Color.Gray) }
                IconButton(onClick = onDeleteClick) { Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red) }
            }
        }
    }
}
