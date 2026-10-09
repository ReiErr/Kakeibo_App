package com.example.kakeiboapp

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.ui.platform.LocalContext
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
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TransactionViewModel) {
    val scrollState = rememberScrollState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var newCategoryName by remember { mutableStateOf("") }
    
    val categories by viewModel.allCategories.collectAsState(initial = emptyList())
    val paymentMethods by viewModel.allPaymentMethods.collectAsState(initial = emptyList())
    val allTransactions by viewModel.allTransactions.collectAsState(initial = emptyList())
    val subs by viewModel.subscriptions.collectAsState()

    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()
    
    // サブスク関連
    var subName by remember { mutableStateOf("") }
    var subAmount by remember { mutableStateOf("") }
    var isYearlySub by remember { mutableStateOf(false) }
    var subBillingMonth by remember { mutableStateOf(1) }
    var startYear by remember { mutableStateOf(LocalDate.now().year.toString()) }
    var startMonth by remember { mutableStateOf(LocalDate.now().monthValue.toString()) }
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
    var subPaymentMethod by remember { mutableStateOf(PaymentNames.CREDIT) }
    var subCategory by remember { mutableStateOf("その他") }
    var editSubPaymentMethod by remember { mutableStateOf(PaymentNames.CREDIT) }
    var editSubCategory by remember { mutableStateOf("その他") }
    var expandedSubPayment by remember { mutableStateOf(false) }
    var expandedSubCategory by remember { mutableStateOf(false) }
    var expandedEditSubPayment by remember { mutableStateOf(false) }
    var expandedEditSubCategory by remember { mutableStateOf(false) }

    // 残高修正関連
    var editBalanceMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var newBalanceInput by remember { mutableStateOf("") }

    // CSVエクスポート
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        CsvExporter.exportToCsv(allTransactions, outputStream)
                        Toast.makeText(context, "エクスポートが完了しました", Toast.LENGTH_SHORT).show()
                    }
                } catch (e: Exception) {
                    Toast.makeText(context, "エラーが発生しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // JSONバックアップ
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            coroutineScope.launch {
                try {
                    val jsonString = viewModel.createBackupJson()
                    context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                        outputStream.write(jsonString.toByteArray())
                    }
                    Toast.makeText(context, "バックアップが完了しました", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(context, "エラーが発生しました: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // JSONリストア
    var showRestoreConfirmDialog by remember { mutableStateOf(false) }
    var restoreUri by remember { mutableStateOf<android.net.Uri?>(null) }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            restoreUri = uri
            showRestoreConfirmDialog = true
        }
    }

    if (showRestoreConfirmDialog && restoreUri != null) {
        AlertDialog(
            onDismissRequest = { showRestoreConfirmDialog = false; restoreUri = null },
            title = { Text("データの復元", fontWeight = FontWeight.Bold) },
            text = { Text("現在のアプリ内データは全て削除され、バックアップファイルの内容で完全に上書きされます。本当によろしいですか？", color = Color.Red) },
            confirmButton = {
                TextButton(onClick = {
                    coroutineScope.launch {
                        try {
                            val jsonString = context.contentResolver.openInputStream(restoreUri!!)?.bufferedReader().use { it?.readText() } ?: ""
                            viewModel.restoreFromBackupJson(jsonString)
                            Toast.makeText(context, "復元が完了しました", Toast.LENGTH_SHORT).show()
                        } catch (e: Exception) {
                            Toast.makeText(context, "エラーが発生しました: ${e.message}", Toast.LENGTH_LONG).show()
                        } finally {
                            showRestoreConfirmDialog = false
                            restoreUri = null
                        }
                    }
                }) { Text("復元を実行する", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { showRestoreConfirmDialog = false; restoreUri = null }) { Text("キャンセル") }
            }
        )
    }

    // 削除確認ダイアログ
    var deleteCategoryCandidate by remember { mutableStateOf<Category?>(null) }
    var deleteMethodCandidate by remember { mutableStateOf<PaymentMethod?>(null) }
    var deleteUsageCount by remember { mutableStateOf(0) }
    
    // クレジットカード追加ダイアログ
    var showAddCreditCardDialog by remember { mutableStateOf(false) }
    var newCardName by remember { mutableStateOf("") }
    var newCardClosingDay by remember { mutableStateOf("0") } // 0=月末
    var newCardPaymentDay by remember { mutableStateOf("27") }
    var newCardOffset by remember { mutableStateOf("1") } // 1=翌月, 2=翌々月
    var newCardHolidayPolicy by remember { mutableStateOf("backward") } // 休日の対応

    // クレジットカード編集ダイアログ
    var editCreditMethod by remember { mutableStateOf<PaymentMethod?>(null) }
    var editCardName by remember { mutableStateOf("") }
    var editCardClosingDay by remember { mutableStateOf("") }
    var editCardPaymentDay by remember { mutableStateOf("") }
    var editCardOffset by remember { mutableStateOf("") }
    var editCardHolidayPolicy by remember { mutableStateOf("") }

    // 一般決済手段追加
    var showAddPaymentDialog by remember { mutableStateOf(false) }
    var newPaymentName by remember { mutableStateOf("") }
    var newPaymentType by remember { mutableStateOf(PaymentTypes.ELECTRONIC_MONEY) }

    // サブスク関連ダイアログ
    if (editTerminateSubId != null) {
        val targetSub = subs.find { it.id == editTerminateSubId }
        val isAlreadyTerminated = targetSub?.endYearMonth != null
        AlertDialog(
            onDismissRequest = { editTerminateSubId = null },
            title = { Text(if (isAlreadyTerminated) "解約設定の修正" else "サブスクの解約", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    Text("支払いを終了した（または終了する）年月を入力してください。", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = termYear, onValueChange = { termYear = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp))
                        Text(" 年 ", fontSize = 16.sp)
                        OutlinedTextField(value = termMonth, onValueChange = { termMonth = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(60.dp))
                        Text(" 月", fontSize = 16.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { val y = termYear.toIntOrNull() ?: LocalDate.now().year; val m = termMonth.toIntOrNull() ?: LocalDate.now().monthValue; viewModel.terminateSubscription(editTerminateSubId!!, yearMonthString(y, m)); editTerminateSubId = null }) { Text(if (isAlreadyTerminated) "更新する" else "解約する", fontSize = 16.sp) } },
            dismissButton = { Row { if (isAlreadyTerminated) { TextButton(onClick = { viewModel.terminateSubscription(editTerminateSubId!!, null); editTerminateSubId = null }) { Text("解約取消", color = Color.Red, fontSize = 16.sp) } }; TextButton(onClick = { editTerminateSubId = null }) { Text("キャンセル", fontSize = 16.sp) } } }
        )
    }

    if (editSubId != null) {
        AlertDialog(
            onDismissRequest = { editSubId = null },
            title = { Text("登録内容の編集", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    OutlinedTextField(value = editSubName, onValueChange = { editSubName = it }, label = { Text("サブスク名") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = editSubAmount, onValueChange = { editSubAmount = it }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (editIsYearlySub) "年払い" else "月々払い", fontSize = 14.sp, fontWeight = FontWeight.Bold); Switch(checked = editIsYearlySub, onCheckedChange = { editIsYearlySub = it }) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("開始時期:", fontSize = 16.sp); Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = editStartYear, onValueChange = { editStartYear = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 年", fontSize = 16.sp); Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = editStartMonth, onValueChange = { editStartMonth = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(60.dp)); Text(" 月", fontSize = 16.sp)
                    }
                    if (editIsYearlySub) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) { Text("支払い月:", fontSize = 16.sp); Spacer(modifier = Modifier.width(8.dp)); OutlinedTextField(value = editSubBillingMonth.toString(), onValueChange = { it.toIntOrNull()?.let { m -> if (m in 1..12) editSubBillingMonth = m } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 月", fontSize = 16.sp) }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box {
                            OutlinedButton(onClick = { expandedEditSubPayment = true }) { Text("支払: $editSubPaymentMethod") }
                            DropdownMenu(expanded = expandedEditSubPayment, onDismissRequest = { expandedEditSubPayment = false }) {
                                paymentMethods.forEach { method -> DropdownMenuItem(text = { Text(method.name) }, onClick = { editSubPaymentMethod = method.name; expandedEditSubPayment = false }) }
                            }
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Box {
                            OutlinedButton(onClick = { expandedEditSubCategory = true }) { Text("分類: $editSubCategory") }
                            DropdownMenu(expanded = expandedEditSubCategory, onDismissRequest = { expandedEditSubCategory = false }) {
                                categories.filter { it.isExpense }.forEach { cat -> DropdownMenuItem(text = { Text(cat.name) }, onClick = { editSubCategory = cat.name; expandedEditSubCategory = false }) }
                            }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { val amt = editSubAmount.toIntOrNull() ?: 0; val y = editStartYear.toIntOrNull() ?: LocalDate.now().year; val m = editStartMonth.toIntOrNull() ?: LocalDate.now().monthValue; if (editSubName.isNotBlank() && amt > 0) { viewModel.updateSubscription(editSubId!!, editSubName, amt, editIsYearlySub, if(editIsYearlySub) editSubBillingMonth else 1, yearMonthString(y, m), editSubPaymentMethod, editSubCategory); editSubId = null } }) { Text("保存する", fontSize = 16.sp) } },
            dismissButton = { TextButton(onClick = { editSubId = null }) { Text("キャンセル", fontSize = 16.sp) } }
        )
    }

    if (editBalanceMethod != null) {
        AlertDialog(
            onDismissRequest = { editBalanceMethod = null },
            title = { Text("${editBalanceMethod!!.name} の残高修正", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    Text("現在の正しい残高を入力してください。\n差額は自動的に「残高調整」として処理されます。", fontSize = 16.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedTextField(value = newBalanceInput, onValueChange = { newBalanceInput = it }, label = { Text("新しい残高") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val newBal = newBalanceInput.toIntOrNull() ?: 0
                    val curBal = calculateBalance(editBalanceMethod!!.name, allTransactions, paymentMethods)
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
                }) { Text("保存する", fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { editBalanceMethod = null }) { Text("キャンセル", fontSize = 16.sp) } }
        )
    }
    
    // クレカ追加ダイアログ
    if (showAddCreditCardDialog) {
        AlertDialog(
            onDismissRequest = { showAddCreditCardDialog = false },
            title = { Text("クレジットカードの追加", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    OutlinedTextField(value = newCardName, onValueChange = { newCardName = it }, label = { Text("カード名 (例: 楽天カード)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = newCardClosingDay, onValueChange = { newCardClosingDay = it }, label = { Text("締め日(0=月末)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = newCardPaymentDay, onValueChange = { newCardPaymentDay = it }, label = { Text("引落日") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("引落月: ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        RadioButton(selected = newCardOffset == "1", onClick = { newCardOffset = "1" }); Text("翌月", fontSize = 16.sp)
                        RadioButton(selected = newCardOffset == "2", onClick = { newCardOffset = "2" }); Text("翌々月", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("休日の対応: ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        RadioButton(selected = newCardHolidayPolicy == "forward", onClick = { newCardHolidayPolicy = "forward" }); Text("前倒し", fontSize = 16.sp)
                        RadioButton(selected = newCardHolidayPolicy == "backward", onClick = { newCardHolidayPolicy = "backward" }); Text("後倒し", fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newCardName.isNotBlank()) {
                        viewModel.addPaymentMethod(PaymentMethod(
                            name = newCardName,
                            type = PaymentTypes.CREDIT,
                            manageBalance = false,
                            closingDay = newCardClosingDay.toIntOrNull() ?: 0,
                            paymentDay = newCardPaymentDay.toIntOrNull() ?: 27,
                            paymentMonthOffset = newCardOffset.toIntOrNull() ?: 1,
                            holidayPolicy = newCardHolidayPolicy
                        ))
                        newCardName = ""; showAddCreditCardDialog = false
                    }
                }) { Text("追加", fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { showAddCreditCardDialog = false }) { Text("キャンセル", fontSize = 16.sp) } }
        )
    }

    // クレカ編集ダイアログ
    if (editCreditMethod != null) {
        AlertDialog(
            onDismissRequest = { editCreditMethod = null },
            title = { Text("クレジットカード設定の編集", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    OutlinedTextField(
                        value = editCardName, 
                        onValueChange = { editCardName = it }, 
                        label = { Text("カード名") }, 
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !editCreditMethod!!.isDefault // デフォルトの「クレジット」は名前変更不可
                    )
                    if (editCreditMethod!!.isDefault) {
                        Text("※「クレジット」の名前は変更できません", fontSize = 12.sp, color = Color.Gray)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(value = editCardClosingDay, onValueChange = { editCardClosingDay = it }, label = { Text("締め日(0=月末)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                        Spacer(modifier = Modifier.width(8.dp))
                        OutlinedTextField(value = editCardPaymentDay, onValueChange = { editCardPaymentDay = it }, label = { Text("引落日") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("引落月: ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        RadioButton(selected = editCardOffset == "1", onClick = { editCardOffset = "1" }); Text("翌月", fontSize = 16.sp)
                        RadioButton(selected = editCardOffset == "2", onClick = { editCardOffset = "2" }); Text("翌々月", fontSize = 16.sp)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("休日の対応: ", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        RadioButton(selected = editCardHolidayPolicy == "forward", onClick = { editCardHolidayPolicy = "forward" }); Text("前倒し", fontSize = 16.sp)
                        RadioButton(selected = editCardHolidayPolicy == "backward", onClick = { editCardHolidayPolicy = "backward" }); Text("後倒し", fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (editCardName.isNotBlank() || editCreditMethod!!.isDefault) {
                        val finalName = if (editCreditMethod!!.isDefault) editCreditMethod!!.name else editCardName
                        val updated = editCreditMethod!!.copy(
                            name = finalName,
                            closingDay = editCardClosingDay.toIntOrNull() ?: 0,
                            paymentDay = editCardPaymentDay.toIntOrNull() ?: 27,
                            paymentMonthOffset = editCardOffset.toIntOrNull() ?: 1,
                            holidayPolicy = editCardHolidayPolicy
                        )
                        viewModel.updatePaymentMethod(updated)
                        editCreditMethod = null
                    }
                }) { Text("保存する", fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { editCreditMethod = null }) { Text("キャンセル", fontSize = 16.sp) } }
        )
    }

    // 一般決済手段追加ダイアログ
    if (showAddPaymentDialog) {
        AlertDialog(
            onDismissRequest = { showAddPaymentDialog = false },
            title = { Text("決済手段の追加", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    OutlinedTextField(value = newPaymentName, onValueChange = { newPaymentName = it }, label = { Text("名前 (例: 食費用財布, PayPay)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("種類:", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = newPaymentType == PaymentTypes.CASH, onClick = { newPaymentType = PaymentTypes.CASH })
                        Text("財布 (現金)", fontSize = 16.sp)
                        Spacer(modifier = Modifier.width(16.dp))
                        RadioButton(selected = newPaymentType == PaymentTypes.ELECTRONIC_MONEY, onClick = { newPaymentType = PaymentTypes.ELECTRONIC_MONEY })
                        Text("電子マネー", fontSize = 16.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (newPaymentName.isNotBlank()) {
                        viewModel.addPaymentMethod(PaymentMethod(
                            name = newPaymentName,
                            type = newPaymentType,
                            manageBalance = true
                        ))
                        newPaymentName = ""; showAddPaymentDialog = false
                    }
                }) { Text("追加", fontSize = 16.sp) }
            },
            dismissButton = { TextButton(onClick = { showAddPaymentDialog = false }) { Text("キャンセル", fontSize = 16.sp) } }
        )
    }

    // 削除確認ダイアログ
    if (deleteCategoryCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteCategoryCandidate = null },
            title = { Text("カテゴリーの削除", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    Text("「${deleteCategoryCandidate?.name}」を削除しますか？", fontSize = 16.sp)
                    if (deleteUsageCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⚠️ このカテゴリーは ${deleteUsageCount} 件の取引で使用されています。削除すると過去のデータに影響が出ます。", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.deleteCategory(deleteCategoryCandidate!!); deleteCategoryCandidate = null }) { Text("削除する", color = Color.Red, fontSize = 16.sp) } },
            dismissButton = { TextButton(onClick = { deleteCategoryCandidate = null }) { Text("キャンセル", fontSize = 16.sp) } }
        )
    }
    
    if (deleteMethodCandidate != null) {
        AlertDialog(
            onDismissRequest = { deleteMethodCandidate = null },
            title = { Text("決済手段の削除", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 20.sp) },
            text = {
                Column {
                    Text("「${deleteMethodCandidate?.name}」を削除しますか？", fontSize = 16.sp)
                    if (deleteUsageCount > 0) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("⚠️ この決済手段は ${deleteUsageCount} 件の取引で使用されています。削除すると過去の残高計算などに重大な影響が出ます。", color = Color.Red, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            },
            confirmButton = { TextButton(onClick = { viewModel.deletePaymentMethod(deleteMethodCandidate!!); deleteMethodCandidate = null }) { Text("削除する", color = Color.Red, fontSize = 16.sp) } },
            dismissButton = { TextButton(onClick = { deleteMethodCandidate = null }) { Text("キャンセル", fontSize = 16.sp) } }
        )
    }


    Column(modifier = Modifier.fillMaxSize().padding(16.dp).verticalScroll(scrollState)) {
        // ============================
        // データ管理 (バックアップ・復元)
        // ============================
        Text("データ管理", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = { 
            val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
            exportLauncher.launch("KakeiboData_$dateStr.csv") 
        }, modifier = Modifier.fillMaxWidth()) {
            Text("取引データをCSV出力 (エクスポート)", fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Button(onClick = { 
                val dateStr = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"))
                backupLauncher.launch("KakeiboBackup_$dateStr.json") 
            }, modifier = Modifier.weight(1f)) {
                Text("全データ バックアップ (JSON)", fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(onClick = { restoreLauncher.launch(arrayOf("application/json", "*/*")) }, modifier = Modifier.weight(1f)) {
                Text("復元 (インポート)", fontSize = 14.sp, color = Color.Red)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))

        // ============================
        // システム設定
        // ============================
        Text("システム設定", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Column { 
                Text("クレジット全体の集計", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(if (aggregateOnUsage) "「利用日」の合計に含める" else "「引き落とし日」の合計に含める", fontSize = 14.sp, color = Color.DarkGray) 
            }
            Switch(checked = aggregateOnUsage, onCheckedChange = { viewModel.setAggregateCreditOnUsageDate(it) })
        }
        Spacer(modifier = Modifier.height(32.dp))

        // ============================
        // 決済手段の設定
        // ============================
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text("決済手段の設定", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Row {
                TextButton(onClick = { showAddPaymentDialog = true }) { Text("+ 電子マネー等", fontSize = 16.sp) }
                TextButton(onClick = { showAddCreditCardDialog = true }) { Text("+ クレカ", fontSize = 16.sp) }
            }
        }
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        paymentMethods.forEach { method ->
            val balance = calculateBalance(method.name, allTransactions, paymentMethods)

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = method.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    if (method.manageBalance) {
                        Text(text = "残高: $balance 円", fontSize = 14.sp, color = Color(0xFF1565C0), fontWeight = FontWeight.Bold)
                    } else if (method.type == PaymentTypes.CREDIT) {
                        val cd = if (method.closingDay == 0) "月末" else "${method.closingDay}日"
                        val off = if (method.paymentMonthOffset == 1) "翌月" else "翌々月"
                        val hol = if (method.holidayPolicy == "forward") "前倒し" else "後倒し"
                        Text(text = "$cd 締め / $off ${method.paymentDay}日払い (休日: $hol)", fontSize = 14.sp, color = Color.DarkGray)
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (method.manageBalance) {
                        IconButton(onClick = {
                            editBalanceMethod = method
                            newBalanceInput = balance.toString()
                        }) { Icon(Icons.Filled.Edit, contentDescription = "残高修正", tint = Color.Gray) }
                    } else if (method.type == PaymentTypes.CREDIT) {
                        // クレジットの場合は設定変更ダイアログ
                        IconButton(onClick = {
                            editCreditMethod = method
                            editCardName = method.name
                            editCardClosingDay = method.closingDay.toString()
                            editCardPaymentDay = method.paymentDay.toString()
                            editCardOffset = method.paymentMonthOffset.toString()
                            editCardHolidayPolicy = method.holidayPolicy
                        }) { Icon(Icons.Filled.Edit, contentDescription = "カード設定", tint = Color.Gray) }
                    }
                    
                    if (!method.isDefault) {
                        IconButton(onClick = {
                            coroutineScope.launch {
                                deleteUsageCount = viewModel.checkPaymentMethodUsage(method)
                                deleteMethodCandidate = method
                            }
                        }) { Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red) }
                    } else {
                        Text("基本", fontSize = 14.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                    }
                }
            }
            HorizontalDivider(color = Color.LightGray)
        }
        Spacer(modifier = Modifier.height(32.dp))

        // ============================
        // サブスクリプション設定
        // ============================
        Text("サブスクリプション設定", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedTextField(value = subName, onValueChange = { subName = it }, label = { Text("サブスク名 (例: 音楽配信)") }, modifier = Modifier.fillMaxWidth())
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(value = subAmount, onValueChange = { subAmount = it }, label = { Text("金額") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(16.dp))
            Column(horizontalAlignment = Alignment.CenterHorizontally) { Text(if (isYearlySub) "年払い" else "月々払い", fontSize = 14.sp, fontWeight = FontWeight.Bold); Switch(checked = isYearlySub, onCheckedChange = { isYearlySub = it }) }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("開始時期:", fontSize = 16.sp); Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = startYear, onValueChange = { startYear = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 年", fontSize = 16.sp); Spacer(modifier = Modifier.width(8.dp))
            OutlinedTextField(value = startMonth, onValueChange = { startMonth = it }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(60.dp)); Text(" 月", fontSize = 16.sp)
        }
        if (isYearlySub) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("支払い月:", fontSize = 16.sp); Spacer(modifier = Modifier.width(8.dp))
                OutlinedTextField(value = subBillingMonth.toString(), onValueChange = { it.toIntOrNull()?.let { m -> if (m in 1..12) subBillingMonth = m } }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.width(80.dp)); Text(" 月", fontSize = 16.sp)
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box {
                OutlinedButton(onClick = { expandedSubPayment = true }) { Text("支払: $subPaymentMethod") }
                DropdownMenu(expanded = expandedSubPayment, onDismissRequest = { expandedSubPayment = false }) {
                    paymentMethods.forEach { method -> DropdownMenuItem(text = { Text(method.name) }, onClick = { subPaymentMethod = method.name; expandedSubPayment = false }) }
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Box {
                OutlinedButton(onClick = { expandedSubCategory = true }) { Text("分類: $subCategory") }
                DropdownMenu(expanded = expandedSubCategory, onDismissRequest = { expandedSubCategory = false }) {
                    categories.filter { it.isExpense }.forEach { cat -> DropdownMenuItem(text = { Text(cat.name) }, onClick = { subCategory = cat.name; expandedSubCategory = false }) }
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        Button(onClick = {
            val amt = subAmount.toIntOrNull() ?: 0
            val y = startYear.toIntOrNull() ?: LocalDate.now().year
            val m = startMonth.toIntOrNull() ?: LocalDate.now().monthValue
            if (subName.isNotBlank() && amt > 0) { 
                viewModel.addSubscription(subName, amt, isYearlySub, if(isYearlySub) subBillingMonth else 1, yearMonthString(y, m), subPaymentMethod, subCategory)
                subName = ""; subAmount = "" 
            }
        }, modifier = Modifier.fillMaxWidth()) { Text("サブスクを追加", fontSize = 16.sp) }
        Spacer(modifier = Modifier.height(16.dp))

        subs.forEach { sub ->
            val typeText = if (sub.isYearly) "年払い(${sub.billingMonth}月)" else "月々払い"
            val parts = sub.startYearMonth.split("-")
            val startText = if (parts.size == 2) "${parts[0]}年${parts[1].toInt()}月開始" else ""
            val endText = if (sub.endYearMonth != null) { val eParts = sub.endYearMonth.split("-"); " / ${eParts[0]}年${eParts[1].toInt()}月終了" } else ""

            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = sub.name, fontSize = 18.sp, color = if (sub.endYearMonth != null) Color.Gray else Color.Black, fontWeight = FontWeight.Medium)
                    Text(text = "$typeText / ${sub.amount}円 / ${sub.paymentMethod} / ${sub.category}", fontSize = 14.sp, color = Color.DarkGray)
                    Text(text = "$startText$endText", fontSize = 14.sp, color = Color.Gray)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (sub.endYearMonth == null) {
                        TextButton(onClick = { editTerminateSubId = sub.id; termYear = LocalDate.now().year.toString(); termMonth = LocalDate.now().monthValue.toString() }) { Text("解約", fontSize = 16.sp) }
                    } else {
                        TextButton(onClick = { editTerminateSubId = sub.id; val eParts = sub.endYearMonth.split("-"); if (eParts.size == 2) { termYear = eParts[0]; termMonth = eParts[1].toInt().toString() } }) { Text("修正", fontSize = 16.sp) }
                    }
                    IconButton(onClick = {
                        editSubId = sub.id; editSubName = sub.name; editSubAmount = sub.amount.toString()
                        editIsYearlySub = sub.isYearly; editSubBillingMonth = sub.billingMonth
                        editSubPaymentMethod = sub.paymentMethod; editSubCategory = sub.category
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
        var categoryTab by remember { mutableStateOf(0) } // 0: 支出用, 1: 収入用
        Text("カテゴリー設定", fontSize = 22.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        TabRow(selectedTabIndex = categoryTab) {
            Tab(selected = categoryTab == 0, onClick = { categoryTab = 0 }, text = { Text("支出用", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (categoryTab == 0) Color.Red else Color.Gray) })
            Tab(selected = categoryTab == 1, onClick = { categoryTab = 1 }, text = { Text("収入用", fontSize = 16.sp, fontWeight = FontWeight.Bold, color = if (categoryTab == 1) Color.Blue else Color.Gray) })
        }
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            val labelText = if (categoryTab == 0) "新しい支出カテゴリー" else "新しい収入カテゴリー"
            OutlinedTextField(value = newCategoryName, onValueChange = { newCategoryName = it }, label = { Text(labelText) }, modifier = Modifier.weight(1f))
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = { 
                if (newCategoryName.isNotBlank()) { 
                    viewModel.addCategory(newCategoryName, isExpense = (categoryTab == 0))
                    newCategoryName = "" 
                } 
            }) { Text("追加", fontSize = 16.sp) }
        }
        Spacer(modifier = Modifier.height(24.dp))
        Text(if (categoryTab == 0) "登録済みの支出カテゴリー" else "登録済みの収入カテゴリー", fontWeight = FontWeight.Bold, color = Color.DarkGray, fontSize = 16.sp)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        val filteredCategories = categories.filter { if (categoryTab == 0) it.isExpense else !it.isExpense }
        if (filteredCategories.isEmpty()) {
            Text("カテゴリーが登録されていません", color = Color.Gray, fontSize = 14.sp, modifier = Modifier.padding(vertical = 8.dp))
        } else {
            filteredCategories.forEach { category ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(text = category.name, fontSize = 18.sp, fontWeight = FontWeight.Medium)
                    IconButton(onClick = {
                        coroutineScope.launch {
                            deleteUsageCount = viewModel.checkCategoryUsage(category)
                            deleteCategoryCandidate = category
                        }
                    }) { Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red) }
                }
                HorizontalDivider(color = Color.LightGray)
            }
        }
        Spacer(modifier = Modifier.height(32.dp))
    }
}
