package com.example.kakeiboapp

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TransactionViewModel) {
    val scrollState = rememberScrollState()

    var newCategoryName by remember { mutableStateOf("") }
    var newPaymentMethodName by remember { mutableStateOf("") }
    val categories by viewModel.allCategories.collectAsState(initial = emptyList<Category>())
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList<PaymentMethod>())

    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val creditPaymentDay by viewModel.creditPaymentDay.collectAsState()
    val creditHolidayPolicy by viewModel.creditHolidayPolicy.collectAsState()
    var paymentDayInput by remember { mutableStateOf(creditPaymentDay.toString()) }

    var subName by remember { mutableStateOf("") }
    var subAmount by remember { mutableStateOf("") }
    var isYearlySub by remember { mutableStateOf(false) }
    var subBillingMonth by remember { mutableStateOf(1) }
    var startYear by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var startMonth by remember { mutableStateOf(LocalDate.now().monthValue.toString()) }
    val subs by viewModel.subscriptions.collectAsState()

    var editTerminateSubId by remember { mutableStateOf<Int?>(null) }
    var termYear by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var termMonth by remember { mutableStateOf(LocalDate.now().monthValue.toString()) }

    var editSubId by remember { mutableStateOf<Int?>(null) }
    var editSubName by remember { mutableStateOf("") }
    var editSubAmount by remember { mutableStateOf("") }
    var editIsYearlySub by remember { mutableStateOf(false) }
    var editSubBillingMonth by remember { mutableStateOf(1) }
    var editStartYear by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var editStartMonth by remember { mutableStateOf(LocalDate.now().monthValue.toString()) }

    var editBalanceMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var newBalanceInput by remember { mutableStateOf("") }
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList<Transaction>())

    if (editTerminateSubId != null) {
        val targetSub = subs.find { it.id == editTerminateSubId }
        val isAlreadyTerminated = targetSub?.endYearMonth != null
        AlertDialog(
            onDismissRequest = { editTerminateSubId = null },
            title = { Text(if (isAlreadyTerminated) "解約設定の修正" else "サブスクの解約", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("支払いを終了した（または終了する）年月を入力してください。")
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = termYear, onValueChange = { termYear = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                        Text(" 年 ")
                        OutlinedTextField(value = termMonth, onValueChange = { termMonth = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(60.dp))
                        Text(" 月")
                    }
                }
            },
            confirmButton = { TextButton(onClick = { val y = termYear.toIntOrNull() ?: LocalDate.now().year; val m = termMonth.toIntOrNull() ?: LocalDate.now().monthValue; viewModel.terminateSubscription(editTerminateSubId!!, yearMonthString(y, m)); editTerminateSubId = null }) { Text(if (isAlreadyTerminated) "更新する" else "解約する") } },
            dismissButton = { Row { if (isAlreadyTerminated) { TextButton(onClick = { viewModel.terminateSubscription(editTerminateSubId!!, null); editTerminateSubId = null }) { Text("解約取消", color = Color.Red) } }; TextButton(onClick = { editTerminateSubId = null }) { Text("キャンセル") } } }
        )
    }

    if (editSubId != null) {
        AlertDialog(
            onDismissRequest = { editSubId = null },
            title = { Text("登録内容の編集", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(value = editSubName, onValueChange = { editSubName = it }, label = { Text("サブスク名") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = editSubAmount, onValueChange = { editSubAmount = it }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (editIsYearlySub) "年払い" else "月々払い", fontSize = 12.sp, fontWeight = FontWeight.Bold); Switch(checked = editIsYearlySub, onCheckedChange = { editIsYearlySub = it }) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("開始時期:"); Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = editStartYear, onValueChange = { editStartYear = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 年"); Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = editStartMonth, onValueChange = { editStartMonth = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(60.dp)); Text(" 月")
                    }
                    if (editIsYearlySub) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("支払い月:"); Spacer(modifier = Modifier.width(8.dp)); OutlinedTextField(value = editSubBillingMonth.toString(), onValueChange = { it.toIntOrNull()?.let { m -> if (m in 1..12) editSubBillingMonth = m } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 月") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { val amt = editSubAmount.toIntOrNull() ?: 0; val y = editStartYear.toIntOrNull() ?: LocalDate.now().year; val m = editStartMonth.toIntOrNull() ?: LocalDate.now().monthValue; if (editSubName.isNotBlank() && amt > 0) { viewModel.updateSubscription(editSubId!!, editSubName, amt, editIsYearlySub, if(editIsYearlySub) editSubBillingMonth else 1, yearMonthString(y, m)); editSubId = null } }) { Text("保存する") } },
            dismissButton = { TextButton(onClick = { editSubId = null }) { Text("キャンセル") } }
        )
    }

    if (editBalanceMethod != null) {
        AlertDialog(
            onDismissRequest = { editBalanceMethod = null },
            title = { Text("${editBalanceMethod!!.name} の残高修正", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text("現在の正しい残高を入力してください。\n差額は自動的に「残高調整」として処理されます。")
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = newBalanceInput, onValueChange = { newBalanceInput = it }, label = { Text("新しい残高") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newBal = newBalanceInput.toIntOrNull() ?: 0
                    val curBal = calculateBalance(editBalanceMethod!!.name, allTransactions)
                    val diff = newBal - curBal
                    if (diff != 0) {
                        val dateStr = LocalDate.now().toString()
                        if (diff > 0) {
                            viewModel.addTransaction("残高調整", diff, false, CategoryNames.BALANCE_ADJUSTMENT, editBalanceMethod!!.name, dateStr, true, PaymentNames.SYSTEM_ADJUSTMENT)
                        } else {
                            viewModel.addTransaction("残高調整", -diff, false, CategoryNames.BALANCE_ADJUSTMENT, PaymentNames.SYSTEM_ADJUSTMENT, dateStr, true, editBalanceMethod!!.name)
                        }
                    }
                    editBalanceMethod = null
                }) { Text("保存する") }
            },
            dismissButton = { TextButton(onClick = { editBalanceMethod = null }) { Text("キャンセル") } }
        )
    }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {

        Text("システム設定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { Text("クレジット支出の計算", fontSize = 16.sp); Text(if (aggregateOnUsage) "「利用日」の合計に含める" else "「引き落とし日」の合計に含める", fontSize = 12.sp, color = Color.Gray) }
            Switch(checked = aggregateOnUsage, onCheckedChange = { viewModel.setAggregateCreditOnUsageDate(it) })
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("引き落とし日 (翌月)", fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) { OutlinedTextField(value = paymentDayInput, onValueChange = { paymentDayInput = it; it.toIntOrNull()?.let { day -> if (day in 1..31) viewModel.setCreditPaymentDay(day) } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 日", fontSize = 16.sp) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("休日の対応", fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(selected = creditHolidayPolicy == "forward", onClick = { viewModel.setCreditHolidayPolicy("forward") }); Text("前倒し", fontSize = 14.sp)
                RadioButton(selected = creditHolidayPolicy == "backward", onClick = { viewModel.setCreditHolidayPolicy("backward") }); Text("後倒し", fontSize = 14.sp)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // ============================
        // 支払方法（決済手段）の設定
        // ============================
        Text("支払方法の設定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newPaymentMethodName, onValueChange = { newPaymentMethodName = it }, label = { Text("新しい支払方法 (例: PayPay)") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { if (newPaymentMethodName.isNotBlank()) { viewModel.addPaymentMethod(newPaymentMethodName); newPaymentMethodName = "" } }) { Text("追加") }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("登録済みの支払方法 と 現在の残高", fontWeight = FontWeight.Bold, color = Color.Gray)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        paymentMethods.forEach { method ->
            val balance = calculateBalance(method.name, allTransactions)

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = method.name, fontSize = 16.sp)
                    if (method.name.isBalanceManaged()) {
                        Text(text = "残高: $balance 円", fontSize = 12.sp, color = Color.Blue)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (method.name.isBalanceManaged()) {
                        IconButton(onClick = {
                            editBalanceMethod = method
                            newBalanceInput = balance.toString()
                        }) { Icon(Icons.Filled.Edit, contentDescription = "残高修正", tint = Color.Gray) }
                    }
                    if (!method.isDefault) {
                        IconButton(onClick = { viewModel.deletePaymentMethod(method) }) { Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red) }
                    } else {
                        Text("基本(削除不可)", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            HorizontalDivider(color = Color.LightGray)
        }
        Spacer(modifier = Modifier.height(32.dp))

        // ============================
        // サブスクリプション設定
        // ============================
        Text("サブスクリプション設定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = subName, onValueChange = { subName = it }, label = { Text("サブスク名 (例: 音楽配信)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = subAmount, onValueChange = { subAmount = it }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (isYearlySub) "年払い" else "月々払い", fontSize = 12.sp, fontWeight = FontWeight.Bold); Switch(checked = isYearlySub, onCheckedChange = { isYearlySub = it }) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("開始時期:"); Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = startYear, onValueChange = { startYear = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 年"); Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = startMonth, onValueChange = { startMonth = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(60.dp)); Text(" 月")
        }
        if (isYearlySub) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("支払い月:"); Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(value = subBillingMonth.toString(), onValueChange = { it.toIntOrNull()?.let { m -> if (m in 1..12) subBillingMonth = m } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 月")
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val amt = subAmount.toIntOrNull() ?: 0
            val y = startYear.toIntOrNull() ?: LocalDate.now().year
            val m = startMonth.toIntOrNull() ?: LocalDate.now().monthValue
            if (subName.isNotBlank() && amt > 0) { viewModel.addSubscription(subName, amt, isYearlySub, if(isYearlySub) subBillingMonth else 1, yearMonthString(y, m)); subName = ""; subAmount = "" }
        }, modifier = Modifier.fillMaxWidth()) { Text("サブスクを追加") }
        Spacer(modifier = Modifier.height(16.dp))

        subs.forEach { sub ->
            val typeText = if (sub.isYearly) "年払い(${sub.billingMonth}月)" else "月々払い"
            val parts = sub.startYearMonth.split("-")
            val startText = if (parts.size == 2) "${parts[0]}年${parts[1].toInt()}月開始" else ""
            val endText = if (sub.endYearMonth != null) { val eParts = sub.endYearMonth.split("-"); " / ${eParts[0]}年${eParts[1].toInt()}月終了" } else ""

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = sub.name, fontSize = 16.sp, color = if (sub.endYearMonth != null) Color.Gray else Color.Black)
                    Text(text = "$typeText / ${sub.amount}円 / $startText$endText", fontSize = 12.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sub.endYearMonth == null) {
                        TextButton(onClick = { editTerminateSubId = sub.id; termYear = LocalDate.now().year.toString(); termMonth = LocalDate.now().monthValue.toString() }) { Text("解約") }
                    } else {
                        TextButton(onClick = { editTerminateSubId = sub.id; val eParts = sub.endYearMonth.split("-"); if (eParts.size == 2) { termYear = eParts[0]; termMonth = eParts[1].toInt().toString() } }) { Text("修正") }
                    }
                    IconButton(onClick = {
                        editSubId = sub.id; editSubName = sub.name; editSubAmount = sub.amount.toString()
                        editIsYearlySub = sub.isYearly; editSubBillingMonth = sub.billingMonth
                        val sParts = sub.startYearMonth.split("-"); if (sParts.size == 2) { editStartYear = sParts[0]; editStartMonth = sParts[1].toInt().toString() }
                    }) { Icon(Icons.Filled.Edit, contentDescription = "編集", tint = Color.Gray) }
                    IconButton(onClick = { viewModel.deleteSubscription(sub.id) }) { Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red) }
                }
            }
            HorizontalDivider(color = Color.LightGray)
        }
        Spacer(modifier = Modifier.height(32.dp))

        // ============================
        // カテゴリー設定
        // ============================
        Text("カテゴリー設定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text("新しいカテゴリー") }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { if (newCategoryName.isNotBlank()) { viewModel.addCategory(newCategoryName); newCategoryName = "" } }) { Text("追加") }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text("登録済みのカテゴリー", fontWeight = FontWeight.Bold, color = Color.Gray)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        categories.forEach { category ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(text = category.name, fontSize = 16.sp)
                IconButton(onClick = { viewModel.deleteCategory(category) }) { Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red) }
            }
            HorizontalDivider(color = Color.LightGray)
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
