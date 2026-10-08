package com.example.kakeiboapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.kakeiboapp.data.Category
import com.example.kakeiboapp.data.CategoryNames
import com.example.kakeiboapp.data.PaymentMethod
import com.example.kakeiboapp.data.PaymentNames
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.isBalanceManaged
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputForm(date: LocalDate, initialTransaction: Transaction?, viewModel: TransactionViewModel, onSave: (String, Int, Boolean, String, String, Boolean, String?) -> Unit) {
    var tabIndex by remember { mutableStateOf(if (initialTransaction?.isCharge == true) 2 else if (initialTransaction?.isExpense == true) 1 else 0) }
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }
    var amount by remember { mutableStateOf(initialTransaction?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(initialTransaction?.category ?: "") }
    var paymentMethod by remember { mutableStateOf(initialTransaction?.paymentMethod ?: PaymentNames.CASH) }
    var chargeSource by remember { mutableStateOf(initialTransaction?.chargeSource ?: PaymentNames.CASH) }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories by viewModel.allCategories.collectAsState(initial = emptyList<Category>())
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList<PaymentMethod>())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList<Transaction>())

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedPayment by remember { mutableStateOf(false) }
    var expandedSource by remember { mutableStateOf(false) }

    fun getAvailableBal(methodName: String, forChargeSource: Boolean): Int {
        if (!methodName.isBalanceManaged()) return Int.MAX_VALUE
        val currentBal = calculateBalance(methodName, allTransactions)
        val refund = if (initialTransaction != null) {
            if (forChargeSource && initialTransaction.isCharge && initialTransaction.chargeSource == methodName) {
                initialTransaction.amount
            } else if (!forChargeSource && initialTransaction.isExpense && !initialTransaction.isCharge && initialTransaction.paymentMethod == methodName) {
                initialTransaction.amount
            } else 0
        } else 0
        return currentBal + refund
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .imePadding()
    ) {
        val modeText = if (initialTransaction == null) "新規登録" else "編集"
        Text("$modeText: ${date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}", fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { tabIndex = 0; errorMessage = null }, text = { Text("収入", color = Color.Blue) })
            Tab(selected = tabIndex == 1, onClick = { tabIndex = 1; errorMessage = null }, text = { Text("支出", color = Color.Red) })
            Tab(selected = tabIndex == 2, onClick = { tabIndex = 2; errorMessage = null }, text = { Text("チャージ", color = ChargeColor) })
        }
        Spacer(modifier = Modifier.height(16.dp))

        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .verticalScroll(rememberScrollState())
        ) {
            if (tabIndex == 2) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("チャージ元:", modifier = Modifier.width(80.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedSource = true; errorMessage = null }, modifier = Modifier.fillMaxWidth()) { Text(chargeSource) }
                        DropdownMenu(expanded = expandedSource, onDismissRequest = { expandedSource = false }) {
                            paymentMethods.forEach { method ->
                                val available = getAvailableBal(method.name, true)
                                val isEnabled = available > 0 || !method.name.isBalanceManaged()
                                val text = if (!method.name.isBalanceManaged()) method.name else "${method.name} (残高: ${calculateBalance(method.name, allTransactions)}円)"

                                DropdownMenuItem(
                                    text = { Text(text, color = if (isEnabled) Color.Black else Color.LightGray) },
                                    onClick = { if (isEnabled) { chargeSource = method.name; expandedSource = false } },
                                    enabled = isEnabled
                                )
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("チャージ先:", modifier = Modifier.width(80.dp))
                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedButton(onClick = { expandedPayment = true; errorMessage = null }, modifier = Modifier.fillMaxWidth()) { Text(paymentMethod) }
                        DropdownMenu(expanded = expandedPayment, onDismissRequest = { expandedPayment = false }) {
                            paymentMethods.filter { it.name.isBalanceManaged() }.forEach { method ->
                                val bal = calculateBalance(method.name, allTransactions)
                                DropdownMenuItem(text = { Text("${method.name} (残高: ${bal}円)") }, onClick = { paymentMethod = method.name; expandedPayment = false })
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text("チャージ内容 (例: Suicaチャージ)") }, modifier = Modifier.fillMaxWidth())
            } else {
                if (tabIndex == 1) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("支払方法:", modifier = Modifier.width(80.dp))
                        Box(modifier = Modifier.weight(1f)) {
                            OutlinedButton(onClick = { expandedPayment = true; errorMessage = null }, modifier = Modifier.fillMaxWidth()) { Text(paymentMethod) }
                            DropdownMenu(expanded = expandedPayment, onDismissRequest = { expandedPayment = false }) {
                                paymentMethods.forEach { method ->
                                    val available = getAvailableBal(method.name, false)
                                    val isEnabled = available > 0 || !method.name.isBalanceManaged()
                                    val text = if (!method.name.isBalanceManaged()) method.name else "${method.name} (残高: ${calculateBalance(method.name, allTransactions)}円)"

                                    DropdownMenuItem(
                                        text = { Text(text, color = if (isEnabled) Color.Black else Color.LightGray) },
                                        onClick = { if (isEnabled) { paymentMethod = method.name; expandedPayment = false } },
                                        enabled = isEnabled
                                    )
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(if (tabIndex == 0) "収入内容 (例: 給料)" else "内容 (例: スーパー)") }, modifier = Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(8.dp))
                Box {
                    OutlinedButton(onClick = { expandedCategory = true; errorMessage = null }, modifier = Modifier.fillMaxWidth()) { Text(if (category.isEmpty()) "カテゴリーを選択" else category) }
                    DropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
                        categories.forEach { cat -> DropdownMenuItem(text = { Text(cat.name) }, onClick = { category = cat.name; expandedCategory = false }) }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedTextField(value = amount, onValueChange = { amount = it; errorMessage = null }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
        }

        Column(modifier = Modifier.fillMaxWidth().navigationBarsPadding()) {
            if (errorMessage != null) {
                Text(text = errorMessage!!, color = Color.Red, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 8.dp))
            }

            Button(onClick = {
                val amountInt = amount.toIntOrNull() ?: 0
                if (title.isNotEmpty() && amountInt > 0) {
                    val isExpense = (tabIndex == 1)
                    val isCharge = (tabIndex == 2)
                    val saveCategory = if (isCharge) CategoryNames.CHARGE else category

                    val finalPaymentMethod = if (tabIndex == 0) PaymentNames.CASH else paymentMethod

                    if (isCharge && finalPaymentMethod == PaymentNames.CASH) {
                        errorMessage = "現金にはチャージできません"
                        return@Button
                    }
                    if (isCharge && paymentMethods.none { it.name == finalPaymentMethod }) return@Button
                    if (!isCharge && category.isEmpty()) {
                        errorMessage = "カテゴリーを選択してください"
                        return@Button
                    }

                    if (isExpense && !isCharge && finalPaymentMethod.isBalanceManaged()) {
                        val available = getAvailableBal(finalPaymentMethod, false)
                        if (amountInt > available) {
                            errorMessage = "${finalPaymentMethod}の残高が不足しています（利用可能: ${available}円）"
                            return@Button
                        }
                    }
                    if (isCharge && chargeSource.isBalanceManaged()) {
                        val available = getAvailableBal(chargeSource, true)
                        if (amountInt > available) {
                            errorMessage = "${chargeSource}の残高が不足しています（利用可能: ${available}円）"
                            return@Button
                        }
                    }

                    errorMessage = null
                    onSave(title, amountInt, isExpense, saveCategory, finalPaymentMethod, isCharge, if(isCharge) chargeSource else null)
                } else {
                    errorMessage = "内容と金額を正しく入力してください"
                }
            }, modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp)) {
                Text(if (initialTransaction == null) "保存する" else "更新する")
            }
        }
    }
}
