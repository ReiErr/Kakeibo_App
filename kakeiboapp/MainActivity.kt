package com.example.kakeiboapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.ui.theme.KakeiboAppTheme
import com.example.kakeiboapp.data.Category
import com.example.kakeiboapp.data.PaymentMethod
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

enum class ScreenType { Calendar, YearlySummary, Settings }

fun calculateBalance(methodName: String, transactions: List<Transaction>): Int {
    if (methodName == "現金" || methodName == "クレジット") return Int.MAX_VALUE
    val chargeIn = transactions.filter { it.isCharge && it.paymentMethod == methodName }.sumOf { it.amount }
    val chargeOut = transactions.filter { it.isCharge && it.chargeSource == methodName }.sumOf { it.amount }
    val incomeIn = transactions.filter { !it.isExpense && !it.isCharge && it.paymentMethod == methodName }.sumOf { it.amount }
    val expenseOut = transactions.filter { it.isExpense && it.paymentMethod == methodName && !it.isCharge }.sumOf { it.amount }
    return chargeIn + incomeIn - chargeOut - expenseOut
}

class MainActivity : ComponentActivity() {
    private val viewModel: TransactionViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KakeiboAppTheme { MainAppScreen(viewModel = viewModel) } }
    }
}

@Composable
fun MainAppScreen(viewModel: TransactionViewModel) {
    var currentScreen by remember { mutableStateOf(ScreenType.Calendar) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Filled.DateRange, "カレンダー") }, label = { Text("カレンダー") }, selected = currentScreen == ScreenType.Calendar, onClick = { currentScreen = ScreenType.Calendar })
                NavigationBarItem(icon = { Icon(Icons.Filled.Assessment, "年間収支") }, label = { Text("年間収支") }, selected = currentScreen == ScreenType.YearlySummary, onClick = { currentScreen = ScreenType.YearlySummary })
                NavigationBarItem(icon = { Icon(Icons.Filled.Settings, "設定") }, label = { Text("設定") }, selected = currentScreen == ScreenType.Settings, onClick = { currentScreen = ScreenType.Settings })
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                ScreenType.Calendar -> CalendarScreen(viewModel = viewModel)
                ScreenType.YearlySummary -> YearlySummaryScreen(viewModel = viewModel)
                ScreenType.Settings -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}

@Composable
fun YearlySummaryScreen(viewModel: TransactionViewModel) {
    var currentYear by remember { mutableStateOf(LocalDate.now().year) }
    var showYearPicker by remember { mutableStateOf(false) } // ★追加: 年選択ダイアログの表示状態

    val transactions by viewModel.getTransactionsByYear(currentYear.toString()).collectAsState(initial = emptyList<Transaction>())
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentYear-- }) { Text("前年") }
            // ★変更: タップ可能にしてダイアログを開く
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

        val totalIncome = transactions.filter { !it.isExpense && !it.isCharge }.sumOf { it.amount }
        val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage)
        val balance = totalIncome - totalExpense
        val balanceColor = if (balance >= 0) Color.Blue else Color.Red

        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
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
                val mIncome = monthTransactions.filter { !it.isExpense && !it.isCharge }.sumOf { it.amount }
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

    // ★追加: 年選択ダイアログの呼び出し
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
            confirmButton = { TextButton(onClick = { val y = termYear.toIntOrNull() ?: LocalDate.now().year; val m = termMonth.toIntOrNull() ?: LocalDate.now().monthValue; viewModel.terminateSubscription(editTerminateSubId!!, String.format("%04d-%02d", y, m)); editTerminateSubId = null }) { Text(if (isAlreadyTerminated) "更新する" else "解約する") } },
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
            confirmButton = { TextButton(onClick = { val amt = editSubAmount.toIntOrNull() ?: 0; val y = editStartYear.toIntOrNull() ?: LocalDate.now().year; val m = editStartMonth.toIntOrNull() ?: LocalDate.now().monthValue; if (editSubName.isNotBlank() && amt > 0) { viewModel.updateSubscription(editSubId!!, editSubName, amt, editIsYearlySub, if(editIsYearlySub) editSubBillingMonth else 1, String.format("%04d-%02d", y, m)); editSubId = null } }) { Text("保存する") } },
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
                            viewModel.addTransaction("残高調整", diff, false, "残高調整", editBalanceMethod!!.name, dateStr, true, "システム調整")
                        } else {
                            viewModel.addTransaction("残高調整", -diff, false, "残高調整", "システム調整", dateStr, true, editBalanceMethod!!.name)
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
                    if (method.name != "現金" && method.name != "クレジット") {
                        Text(text = "残高: $balance 円", fontSize = 12.sp, color = Color.Blue)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (method.name != "現金" && method.name != "クレジット") {
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
            if (subName.isNotBlank() && amt > 0) { viewModel.addSubscription(subName, amt, isYearlySub, if(isYearlySub) subBillingMonth else 1, String.format("%04d-%02d", y, m)); subName = ""; subAmount = "" }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: TransactionViewModel, modifier: Modifier = Modifier) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDailyDetailDialog by remember { mutableStateOf(false) }
    var showYearMonthPicker by remember { mutableStateOf(false) }

    val transactions by viewModel.getTransactionsByMonth(currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))).collectAsState(initial = emptyList<Transaction>())
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val holidays by viewModel.holidays.collectAsState()

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("先月") }
            Text(text = "${currentMonth.year}年 ${currentMonth.monthValue}月", fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable { showYearMonthPicker = true }.padding(8.dp))
            Button(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text("来月") }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            Text("■ 収入", color = Color.Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("■ 支出", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("■ ｸﾚｼﾞｯﾄ", color = Color(0xFFF57C00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("■ 引落/ﾁｬｰｼﾞ", color = Color(0xFF6A1B9A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }
        Spacer(modifier = Modifier.height(12.dp))
        val daysOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day -> Text(text = day, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = if (day == "日") Color.Red else if (day == "土") Color.Blue else Color.Black) }
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

                    val incomeSum = dailyTransactions.filter { !it.isExpense && !it.isCharge }.sumOf { it.amount }
                    val regularExpenseSum = dailyTransactions.filter { it.isExpense && !it.isCreditPayment && it.paymentMethod != "クレジット" && !it.isCharge }.sumOf { it.amount }
                    val creditUsageSum = dailyTransactions.filter { it.isExpense && it.paymentMethod == "クレジット" && !it.isCreditPayment && !it.isCharge }.sumOf { it.amount }
                    val chargeAndPaymentSum = dailyTransactions.filter { (it.isCreditPayment || it.isCharge) && it.category != "残高調整" }.sumOf { it.amount }

                    val isHoliday = holidays.contains(date)
                    val isSunday = date.dayOfWeek.value == 7
                    val isSaturday = date.dayOfWeek.value == 6
                    val dateColor = when { isSunday || isHoliday -> Color.Red; isSaturday -> Color.Blue; else -> Color.Black }

                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 85.dp).padding(2.dp).background(Color(0xFFF5F5F5), shape = MaterialTheme.shapes.small).clickable { selectedDate = date; showDailyDetailDialog = true }.padding(top = 4.dp, bottom = 4.dp, start = 1.dp, end = 1.dp), contentAlignment = Alignment.TopCenter) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(0.dp)) {
                            Text(text = day.toString(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = dateColor, modifier = Modifier.padding(bottom = 2.dp))
                            val amountStyle = androidx.compose.ui.text.TextStyle(fontSize = 9.sp, lineHeight = 9.sp, textAlign = TextAlign.Center)
                            if (incomeSum > 0) Text(text = "+$incomeSum", color = Color.Blue, style = amountStyle)
                            if (regularExpenseSum > 0) Text(text = "-$regularExpenseSum", color = Color.Red, style = amountStyle)
                            if (creditUsageSum > 0) Text(text = "-$creditUsageSum", color = Color(0xFFF57C00), style = amountStyle)
                            if (chargeAndPaymentSum > 0) Text(text = "±$chargeAndPaymentSum", color = Color(0xFF6A1B9A), style = amountStyle)
                        }
                    }
                } else {
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 85.dp).padding(2.dp))
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))

        val monthlyIncome = transactions.filter { !it.isExpense && !it.isCharge }.sumOf { it.amount }
        val monthlyExpense = transactions.calculateTotalExpense(aggregateOnUsage)
        val monthlyBalance = monthlyIncome - monthlyExpense
        val balanceColor = if (monthlyBalance >= 0) Color.Blue else Color.Red
        Card(modifier = Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD))) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("${currentMonth.monthValue}月 合計", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("収入: +$monthlyIncome", color = Color.Blue, fontWeight = FontWeight.Bold)
                    Text("支出: -$monthlyExpense", color = Color.Red, fontWeight = FontWeight.Bold)
                }
                Text("収支: $monthlyBalance", color = balanceColor, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
            }
        }
    }
    if (showDailyDetailDialog && selectedDate != null) DailyDetailDialog(date = selectedDate!!, transactions = transactions.filter { it.date == selectedDate.toString() }, viewModel = viewModel, onDismiss = { showDailyDetailDialog = false })
    if (showYearMonthPicker) { YearMonthPickerDialog(initialYearMonth = currentMonth, onDismissRequest = { showYearMonthPicker = false }, onYearMonthSelected = { selected -> currentMonth = selected; showYearMonthPicker = false }) }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyDetailDialog(date: LocalDate, transactions: List<Transaction>, viewModel: TransactionViewModel, onDismiss: () -> Unit) {
    var showInputBottomSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    val totalIncome = transactions.filter { !it.isExpense && !it.isCharge }.sumOf { it.amount }
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputForm(date: LocalDate, initialTransaction: Transaction?, viewModel: TransactionViewModel, onSave: (String, Int, Boolean, String, String, Boolean, String?) -> Unit) {
    var tabIndex by remember { mutableStateOf(if (initialTransaction?.isCharge == true) 2 else if (initialTransaction?.isExpense == true) 1 else 0) }
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }
    var amount by remember { mutableStateOf(initialTransaction?.amount?.toString() ?: "") }
    var category by remember { mutableStateOf(initialTransaction?.category ?: "") }
    var paymentMethod by remember { mutableStateOf(initialTransaction?.paymentMethod ?: "現金") }
    var chargeSource by remember { mutableStateOf(initialTransaction?.chargeSource ?: "現金") }

    var errorMessage by remember { mutableStateOf<String?>(null) }

    val categories by viewModel.allCategories.collectAsState(initial = emptyList<Category>())
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList<PaymentMethod>())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList<Transaction>())

    var expandedCategory by remember { mutableStateOf(false) }
    var expandedPayment by remember { mutableStateOf(false) }
    var expandedSource by remember { mutableStateOf(false) }

    fun getAvailableBal(methodName: String, forChargeSource: Boolean): Int {
        if (methodName == "現金" || methodName == "クレジット") return Int.MAX_VALUE
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
            Tab(selected = tabIndex == 2, onClick = { tabIndex = 2; errorMessage = null }, text = { Text("チャージ", color = Color(0xFF6A1B9A)) })
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
                                val isEnabled = available > 0 || method.name == "現金" || method.name == "クレジット"
                                val text = if (method.name == "現金" || method.name == "クレジット") method.name else "${method.name} (残高: ${calculateBalance(method.name, allTransactions)}円)"

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
                            paymentMethods.filter { it.name != "現金" && it.name != "クレジット" }.forEach { method ->
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
                                    val isEnabled = available > 0 || method.name == "現金" || method.name == "クレジット"
                                    val text = if (method.name == "現金" || method.name == "クレジット") method.name else "${method.name} (残高: ${calculateBalance(method.name, allTransactions)}円)"

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
                    val saveCategory = if (isCharge) "チャージ" else category

                    val finalPaymentMethod = if (tabIndex == 0) "現金" else paymentMethod

                    if (isCharge && finalPaymentMethod == "現金") {
                        errorMessage = "現金にはチャージできません"
                        return@Button
                    }
                    if (isCharge && paymentMethods.none { it.name == finalPaymentMethod }) return@Button
                    if (!isCharge && category.isEmpty()) {
                        errorMessage = "カテゴリーを選択してください"
                        return@Button
                    }

                    if (isExpense && !isCharge && finalPaymentMethod != "現金" && finalPaymentMethod != "クレジット") {
                        val available = getAvailableBal(finalPaymentMethod, false)
                        if (amountInt > available) {
                            errorMessage = "${finalPaymentMethod}の残高が不足しています（利用可能: ${available}円）"
                            return@Button
                        }
                    }
                    if (isCharge && chargeSource != "現金" && chargeSource != "クレジット") {
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

@Composable
fun TransactionItemRow(transaction: Transaction, onEditClick: () -> Unit, onDeleteClick: () -> Unit) {
    val amountColor = if (transaction.isCharge || transaction.isCreditPayment) Color(0xFF6A1B9A) else if (!transaction.isExpense) Color.Blue else if (transaction.paymentMethod == "クレジット") Color(0xFFF57C00) else Color.Red
    val amountPrefix = if (transaction.isCharge) "±" else if (transaction.isExpense) "-" else "+"

    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            val methodText = if (transaction.isCharge) " (${transaction.chargeSource} → ${transaction.paymentMethod})" else if (transaction.id > 0) " (${transaction.paymentMethod})" else ""
            Text(text = "${transaction.category}$methodText", fontSize = 12.sp, color = Color.Gray)
        }
        Text(text = "$amountPrefix${transaction.amount}円", color = amountColor, fontWeight = FontWeight.Bold, fontSize = 16.sp, modifier = Modifier.padding(end = 8.dp))

        if (transaction.id < 0) {
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

fun List<Transaction>.calculateTotalExpense(aggregateOnUsage: Boolean): Int {
    return this.filter { it.isExpense && !it.isCharge }.filter {
        if (it.id < 0) {
            true
        } else if (aggregateOnUsage) {
            !it.isCreditPayment
        } else {
            !(it.paymentMethod == "クレジット" && !it.isCreditPayment)
        }
    }.sumOf { it.amount }
}

@Composable
fun YearMonthPickerDialog(initialYearMonth: YearMonth, onDismissRequest: () -> Unit, onYearMonthSelected: (YearMonth) -> Unit) {
    var selectedYear by remember { mutableStateOf(initialYearMonth.year) }
    var selectedMonth by remember { mutableStateOf(initialYearMonth.monthValue) }
    val years = (2000..2050).toList()
    val months = (1..12).toList()
    val yearListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, years.indexOf(initialYearMonth.year) - 2))
    val monthListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, months.indexOf(initialYearMonth.monthValue) - 2))
    AlertDialog(onDismissRequest = onDismissRequest, title = { Text("年月を選択", fontWeight = FontWeight.Bold) }, text = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LazyColumn(state = yearListState, modifier = Modifier.weight(1f).height(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(years) { year -> val isSelected = year == selectedYear; Text(text = "${year}年", fontSize = if (isSelected) 22.sp else 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.Blue else Color.Gray, modifier = Modifier.fillMaxWidth().clickable { selectedYear = year }.padding(vertical = 12.dp), textAlign = TextAlign.Center) }
            }
            LazyColumn(state = monthListState, modifier = Modifier.weight(1f).height(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(months) { month -> val isSelected = month == selectedMonth; Text(text = "${month}月", fontSize = if (isSelected) 22.sp else 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.Blue else Color.Gray, modifier = Modifier.fillMaxWidth().clickable { selectedMonth = month }.padding(vertical = 12.dp), textAlign = TextAlign.Center) }
            }
        }
    }, confirmButton = { TextButton(onClick = { onYearMonthSelected(YearMonth.of(selectedYear, selectedMonth)) }) { Text("決定") } }, dismissButton = { TextButton(onClick = onDismissRequest) { Text("キャンセル") } })
}

// ★追加: 年間収支画面用の「年」選択ダイアログ
@Composable
fun YearPickerDialog(initialYear: Int, onDismissRequest: () -> Unit, onYearSelected: (Int) -> Unit) {
    var selectedYear by remember { mutableStateOf(initialYear) }
    val years = (2000..2050).toList()
    val yearListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, years.indexOf(initialYear) - 2))

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("年を選択", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(state = yearListState, modifier = Modifier.fillMaxWidth().height(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(years) { year ->
                    val isSelected = year == selectedYear
                    Text(
                        text = "${year}年",
                        fontSize = if (isSelected) 22.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Blue else Color.Gray,
                        modifier = Modifier.fillMaxWidth().clickable { selectedYear = year }.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onYearSelected(selectedYear) }) { Text("決定") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("キャンセル") } }
    )
}