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
import com.example.kakeiboapp.data.PaymentTypes
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.isBalanceManaged
import java.time.LocalDate
import java.time.format.DateTimeFormatter

import androidx.compose.ui.text.style.TextOverflow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputForm(date: LocalDate, initialTransaction: Transaction?, viewModel: TransactionViewModel, onSave: (String, Int, Boolean, String, String, Boolean, String?, String) -> Unit) {
    var tabIndex by remember { mutableStateOf(if (initialTransaction?.isCharge == true) 2 else if (initialTransaction?.isExpense == true) 1 else 0) }
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }
    var amount by remember { mutableStateOf(initialTransaction?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(initialTransaction?.category ?: "") }
    var paymentMethod by remember { mutableStateOf(initialTransaction?.paymentMethod ?: PaymentNames.CASH) }
    var chargeSource by remember { mutableStateOf(initialTransaction?.chargeSource ?: PaymentNames.CASH) }
    var memo by remember { mutableStateOf(initialTransaction?.memo ?: "") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories by viewModel.allCategories.collectAsState(initial = emptyList<Category>())
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList<PaymentMethod>())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList<Transaction>())

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedPayment by remember { mutableStateOf(false) }
    var expandedSource by remember { mutableStateOf(false) }

    fun getAvailableBal(methodName: String, forChargeSource: Boolean): Int {
        if (!methodName.isBalanceManaged(paymentMethods)) return Int.MAX_VALUE
        val currentBal = calculateBalance(methodName, allTransactions, paymentMethods)
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
            .navigationBarsPadding()
            .imePadding()
    ) {
        val modeText = if (initialTransaction == null) "新規登録" else "編集"
        Text("$modeText: ${date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}", fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(modifier = Modifier.height(8.dp))

        TabRow(selectedTabIndex = tabIndex) {
            Tab(selected = tabIndex == 0, onClick = { if (tabIndex != 0) { tabIndex = 0; errorMessage = null; category = "" } }, text = { Text("収入", color = Color.Blue, fontSize = 15.sp, fontWeight = FontWeight.Bold) })
            Tab(selected = tabIndex == 1, onClick = { if (tabIndex != 1) { tabIndex = 1; errorMessage = null; category = "" } }, text = { Text("支出", color = Color.Red, fontSize = 15.sp, fontWeight = FontWeight.Bold) })
            Tab(selected = tabIndex == 2, onClick = { if (tabIndex != 2) { tabIndex = 2; errorMessage = null; category = "" } }, text = { Text("チャージ", color = ChargeColor, fontSize = 15.sp, fontWeight = FontWeight.Bold) })
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 行1: 選択系（支払方法・受取先 / カテゴリー）
        if (tabIndex == 2) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // チャージ元
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expandedSource = true; errorMessage = null }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(text = "元: $chargeSource", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = expandedSource, onDismissRequest = { expandedSource = false }) {
                        DropdownMenuItem(
                            text = { Text(PaymentNames.NONE, color = Color.Black, fontSize = 15.sp) },
                            onClick = { chargeSource = PaymentNames.NONE; expandedSource = false }
                        )
                        paymentMethods.filter { it.type == PaymentTypes.CASH }.forEach { method ->
                            val available = getAvailableBal(method.name, true)
                            val isEnabled = available > 0 || !method.name.isBalanceManaged(paymentMethods)
                            val text = "${method.name} (${calculateBalance(method.name, allTransactions, paymentMethods)}円)"
                            DropdownMenuItem(
                                text = { Text(text, color = if (isEnabled) Color.Black else Color.LightGray, fontSize = 15.sp) },
                                onClick = { if (isEnabled) { chargeSource = method.name; expandedSource = false } },
                                enabled = isEnabled
                            )
                        }
                    }
                }
                // チャージ先
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expandedPayment = true; errorMessage = null }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(text = "先: $paymentMethod", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = expandedPayment, onDismissRequest = { expandedPayment = false }) {
                        paymentMethods.filter { it.manageBalance }.forEach { method ->
                            val bal = calculateBalance(method.name, allTransactions, paymentMethods)
                            DropdownMenuItem(text = { Text("${method.name} (${bal}円)", fontSize = 15.sp) }, onClick = { paymentMethod = method.name; expandedPayment = false })
                        }
                    }
                }
            }
        } else {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                // 支払方法 / 受取先
                Box(modifier = Modifier.weight(1f)) {
                    OutlinedButton(onClick = { expandedPayment = true; errorMessage = null }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        val prefix = if (tabIndex == 0) "受取" else "支払"
                        Text(text = "$prefix: $paymentMethod", fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    DropdownMenu(expanded = expandedPayment, onDismissRequest = { expandedPayment = false }) {
                        if (tabIndex == 0) {
                            DropdownMenuItem(
                                text = { Text(PaymentNames.NONE, color = Color.Black, fontSize = 15.sp) },
                                onClick = { paymentMethod = PaymentNames.NONE; expandedPayment = false }
                            )
                        }
                        paymentMethods.forEach { method ->
                            val isEnabled = if (tabIndex == 0) true else (getAvailableBal(method.name, false) > 0 || !method.manageBalance)
                            val text = if (!method.manageBalance) method.name else "${method.name} (${calculateBalance(method.name, allTransactions, paymentMethods)}円)"
                            DropdownMenuItem(
                                text = { Text(text, color = if (isEnabled) Color.Black else Color.LightGray, fontSize = 15.sp) },
                                onClick = { if (isEnabled) { paymentMethod = method.name; expandedPayment = false } },
                                enabled = isEnabled
                            )
                        }
                    }
                }
                // カテゴリー選択（支出/収入で分離）
                Box(modifier = Modifier.weight(1f)) {
                    val currentCategories = categories.filter { if (tabIndex == 1) it.isExpense else !it.isExpense }
                    OutlinedButton(onClick = { expandedCategory = true; errorMessage = null }, modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(horizontal = 8.dp)) {
                        Text(
                            text = if (category.isEmpty()) "カテゴリー選択" else category,
                            fontSize = 14.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    DropdownMenu(expanded = expandedCategory, onDismissRequest = { expandedCategory = false }) {
                        if (currentCategories.isEmpty()) {
                            DropdownMenuItem(text = { Text("（設定から追加可能）", color = Color.Gray, fontSize = 14.sp) }, onClick = { expandedCategory = false })
                        } else {
                            currentCategories.forEach { cat ->
                                DropdownMenuItem(text = { Text(cat.name, fontSize = 15.sp) }, onClick = { category = cat.name; expandedCategory = false })
                            }
                        }
                    }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 行2: 金額 ＋ 内容
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = amount,
                onValueChange = { amount = it; errorMessage = null },
                label = { Text("金額 (円)") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                singleLine = true,
                modifier = Modifier.weight(0.42f)
            )
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text(if (tabIndex == 0) "収入内容 (例: 給料)" else if (tabIndex == 1) "内容 (例: スーパー)" else "チャージ内容") },
                singleLine = true,
                modifier = Modifier.weight(0.58f)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))

        // 行3: メモ（任意）
        OutlinedTextField(
            value = memo,
            onValueChange = { memo = it },
            label = { Text("メモ (任意)") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        if (errorMessage != null) {
            Text(text = errorMessage!!, color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(top = 4.dp))
        }
        Spacer(modifier = Modifier.height(10.dp))

        // 保存ボタン
        Button(
            onClick = {
                val amountInt = amount.toIntOrNull() ?: 0
                if (title.isNotEmpty() && amountInt > 0) {
                    val isExpense = (tabIndex == 1)
                    val isCharge = (tabIndex == 2)
                    val saveCategory = if (isCharge) CategoryNames.CHARGE else category
                    val finalPaymentMethod = paymentMethod

                    if (isCharge && paymentMethods.none { it.name == finalPaymentMethod }) return@Button
                    if (!isCharge && category.isEmpty()) {
                        errorMessage = "カテゴリーを選択してください"
                        return@Button
                    }

                    if (isExpense && !isCharge && finalPaymentMethod.isBalanceManaged(paymentMethods)) {
                        val available = getAvailableBal(finalPaymentMethod, false)
                        if (amountInt > available) {
                            errorMessage = "${finalPaymentMethod}の残高が不足しています（利用可能: ${available}円）"
                            return@Button
                        }
                    }
                    if (isCharge && chargeSource.isBalanceManaged(paymentMethods)) {
                        val available = getAvailableBal(chargeSource, true)
                        if (amountInt > available) {
                            errorMessage = "${chargeSource}の残高が不足しています（利用可能: ${available}円）"
                            return@Button
                        }
                    }

                    errorMessage = null
                    onSave(title, amountInt, isExpense, saveCategory, finalPaymentMethod, isCharge, if(isCharge) { if (chargeSource == PaymentNames.NONE) null else chargeSource } else null, memo)
                } else {
                    errorMessage = "内容と金額を正しく入力してください"
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (initialTransaction == null) "保存する" else "更新する", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        }
    }
}
