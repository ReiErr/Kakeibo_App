package com.example.kakeiboapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.RadioButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

// 画面の種類を定義
enum class ScreenType {
    Calendar, YearlySummary, Settings
}

class MainActivity : ComponentActivity() {
    private val viewModel: TransactionViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KakeiboAppTheme {
                MainAppScreen(viewModel = viewModel)
            }
        }
    }
}

// === メイン画面（タブ切り替えの土台） ===
@Composable
fun MainAppScreen(viewModel: TransactionViewModel) {
    var currentScreen by remember { mutableStateOf(ScreenType.Calendar) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = "カレンダー") },
                    label = { Text("カレンダー") },
                    selected = currentScreen == ScreenType.Calendar,
                    onClick = { currentScreen = ScreenType.Calendar }
                )
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Assessment, contentDescription = "年間収支") },
                    label = { Text("年間収支") },
                    selected = currentScreen == ScreenType.YearlySummary,
                    onClick = { currentScreen = ScreenType.YearlySummary }
                )
                // 設定タブを追加
                NavigationBarItem(
                    icon = { Icon(Icons.Filled.Settings, contentDescription = "設定") },
                    label = { Text("設定") },
                    selected = currentScreen == ScreenType.Settings,
                    onClick = { currentScreen = ScreenType.Settings }
                )
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                ScreenType.Calendar -> CalendarScreen(viewModel = viewModel)
                ScreenType.YearlySummary -> YearlySummaryScreen(viewModel = viewModel)
                ScreenType.Settings -> SettingsScreen(viewModel = viewModel) // 設定画面の呼び出し
            }
        }
    }
}

@Composable
fun YearlySummaryScreen(viewModel: TransactionViewModel) {
    var currentYear by remember { mutableStateOf(LocalDate.now().year) }
    val transactions by viewModel.getTransactionsByYear(currentYear.toString()).collectAsState(initial = emptyList<Transaction>())
    // ★追加: 設定値を取得
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(onClick = { currentYear-- }) { Text("前年") }
            Text(text = "${currentYear}年 年間収支", fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Button(onClick = { currentYear++ }) { Text("翌年") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        // ★修正: 新しい計算ルールを適用
        val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
        val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage) // 重複なし
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

                // ★修正: 新しい計算ルールを適用
                val mIncome = monthTransactions.filter { !it.isExpense }.sumOf { it.amount }
                val mExpense = monthTransactions.calculateTotalExpense(aggregateOnUsage) // 重複なし

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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: TransactionViewModel) {
    var newCategoryName by remember { mutableStateOf("") }
    val categories by viewModel.allCategories.collectAsState(initial = emptyList<Category>())

    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()

    // ★追加: ViewModelから引き落とし設定を監視
    val creditPaymentDay by viewModel.creditPaymentDay.collectAsState()
    val creditHolidayPolicy by viewModel.creditHolidayPolicy.collectAsState()
    var paymentDayInput by remember { mutableStateOf(creditPaymentDay.toString()) }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("システム設定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        // クレジット計上タイミングの設定
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text("クレジット支出の計算", fontSize = 16.sp)
                Text(if (aggregateOnUsage) "「利用日」の合計に含める" else "「引き落とし日」の合計に含める", fontSize = 12.sp, color = Color.Gray)
            }
            Switch(
                checked = aggregateOnUsage,
                onCheckedChange = { viewModel.setAggregateCreditOnUsageDate(it) }
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp), color = Color(0xFFEEEEEE))

        // ★追加: 引き落とし日の設定
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("引き落とし日 (翌月)", fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = paymentDayInput,
                    onValueChange = {
                        paymentDayInput = it
                        it.toIntOrNull()?.let { day ->
                            if (day in 1..31) viewModel.setCreditPaymentDay(day)
                        }
                    },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.width(80.dp)
                )
                Text(" 日", fontSize = 16.sp)
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ★追加: 土日祝日の対応設定
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("休日の対応", fontSize = 16.sp)
            Row(verticalAlignment = Alignment.CenterVertically) {
                RadioButton(
                    selected = creditHolidayPolicy == "forward",
                    onClick = { viewModel.setCreditHolidayPolicy("forward") }
                )
                Text("前倒し", fontSize = 14.sp)
                RadioButton(
                    selected = creditHolidayPolicy == "backward",
                    onClick = { viewModel.setCreditHolidayPolicy("backward") }
                )
                Text("後倒し", fontSize = 14.sp)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // カテゴリー設定 (既存)
        Text("カテゴリー設定", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(16.dp))

        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newCategoryName,
                onValueChange = { newCategoryName = it },
                label = { Text("新しいカテゴリー") },
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newCategoryName.isNotBlank()) {
                    viewModel.addCategory(newCategoryName)
                    newCategoryName = ""
                }
            }) { Text("追加") }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Text("登録済みのカテゴリー", fontWeight = FontWeight.Bold, color = Color.Gray)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        LazyColumn {
            items(categories) { category ->
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = category.name, fontSize = 16.sp)
                    if (category.isDefault) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("登録時に表示", fontSize = 12.sp, color = Color.Gray, modifier = Modifier.padding(end = 8.dp))
                            Switch(
                                checked = category.isVisible,
                                onCheckedChange = { isChecked ->
                                    viewModel.updateCategory(category.copy(isVisible = isChecked))
                                }
                            )
                        }
                    } else {
                        IconButton(onClick = { viewModel.deleteCategory(category) }) {
                            Icon(androidx.compose.material.icons.Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red)
                        }
                    }
                }
                HorizontalDivider(color = Color.LightGray)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen(viewModel: TransactionViewModel, modifier: Modifier = Modifier) {
    var currentMonth by remember { mutableStateOf(YearMonth.now()) }
    var selectedDate by remember { mutableStateOf<LocalDate?>(null) }
    var showDailyDetailDialog by remember { mutableStateOf(false) }

    val transactions by viewModel.getTransactionsByMonth(
        currentMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"))
    ).collectAsState(initial = emptyList<Transaction>())

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Button(onClick = { currentMonth = currentMonth.minusMonths(1) }) { Text("先月") }
            Text(
                text = "${currentMonth.year}年 ${currentMonth.monthValue}月",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Button(onClick = { currentMonth = currentMonth.plusMonths(1) }) { Text("来月") }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 色分けの凡例（ガイド）
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            Text("■ 収入", color = Color.Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("■ 支出", color = Color.Red, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("■ ｸﾚｼﾞｯﾄ", color = Color(0xFFF57C00), fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text("■ 引落し", color = Color(0xFF6A1B9A), fontSize = 10.sp, fontWeight = FontWeight.Bold)
        }

        Spacer(modifier = Modifier.height(12.dp))

        val daysOfWeek = listOf("日", "月", "火", "水", "木", "金", "土")
        Row(modifier = Modifier.fillMaxWidth()) {
            daysOfWeek.forEach { day ->
                Text(
                    text = day,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.Bold,
                    color = if (day == "日") Color.Red else if (day == "土") Color.Blue else Color.Black
                )
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        val daysInMonth = currentMonth.lengthOfMonth()
        val firstDayOfWeek = currentMonth.atDay(1).dayOfWeek.value % 7
        val calendarItems = List(firstDayOfWeek) { null } + (1..daysInMonth).toList()

        LazyVerticalGrid(
            columns = GridCells.Fixed(7),
            modifier = Modifier.fillMaxSize()
        ) {
            items(calendarItems) { day ->
                if (day != null) {
                    val date = currentMonth.atDay(day)
                    val dailyTransactions = transactions.filter { it.date == date.toString() }

                    val incomeSum = dailyTransactions.filter { !it.isExpense }.sumOf { it.amount }
                    val regularExpenseSum = dailyTransactions.filter { it.isExpense && !it.isCreditPayment && it.category != "クレジット" }.sumOf { it.amount }
                    val creditUsageSum = dailyTransactions.filter { it.isExpense && it.category == "クレジット" }.sumOf { it.amount }
                    val creditPaymentSum = dailyTransactions.filter { it.isCreditPayment }.sumOf { it.amount }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth() // 横幅は7等分に広げる
                            .heightIn(min = 85.dp) // ★固定比率をやめ、内容が溢れたら動的にマスが縦に伸びるようにする
                            .padding(2.dp)
                            .background(Color(0xFFF5F5F5), shape = MaterialTheme.shapes.small)
                            .clickable {
                                selectedDate = date
                                showDailyDetailDialog = true
                            }
                            .padding(top = 4.dp, bottom = 4.dp, start = 1.dp, end = 1.dp), // 左右の余白を削って広く使う
                        contentAlignment = Alignment.TopCenter
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(0.dp) // ★項目間の隙間を0にして極限まで詰める
                        ) {
                            Text(
                                text = day.toString(),
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 2.dp) // 日付の下だけ少し隙間をあける
                            )

                            // ★金額表示用の専用テキストスタイル（行間を詰める）
                            val amountStyle = androidx.compose.ui.text.TextStyle(
                                fontSize = 9.sp,
                                lineHeight = 9.sp, // 行の高さを文字サイズと同じにして余白を消す
                                textAlign = TextAlign.Center
                            )

                            if (incomeSum > 0) {
                                Text(text = "+$incomeSum", color = Color.Blue, style = amountStyle)
                            }
                            if (regularExpenseSum > 0) {
                                Text(text = "-$regularExpenseSum", color = Color.Red, style = amountStyle)
                            }
                            if (creditUsageSum > 0) {
                                Text(text = "-$creditUsageSum", color = Color(0xFFF57C00), style = amountStyle)
                            }
                            if (creditPaymentSum > 0) {
                                Text(text = "-$creditPaymentSum", color = Color(0xFF6A1B9A), style = amountStyle)
                            }
                        }
                    }
                } else {
                    // 空白マスも高さを合わせる
                    Box(modifier = Modifier.fillMaxWidth().heightIn(min = 85.dp).padding(2.dp))
                }
            }
        }
    }

    if (showDailyDetailDialog && selectedDate != null) {
        DailyDetailDialog(
            date = selectedDate!!,
            transactions = transactions.filter { it.date == selectedDate.toString() },
            viewModel = viewModel,
            onDismiss = { showDailyDetailDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DailyDetailDialog(
    date: LocalDate,
    transactions: List<Transaction>,
    viewModel: TransactionViewModel,
    onDismiss: () -> Unit
) {
    var showInputBottomSheet by remember { mutableStateOf(false) }
    var editingTransaction by remember { mutableStateOf<Transaction?>(null) }
    var transactionToDelete by remember { mutableStateOf<Transaction?>(null) }

    // ★追加: 設定値を取得
    val aggregateOnUsage by viewModel.aggregateCreditOnUsageDate.collectAsState()

    // ★修正: 新しい計算ルールを適用
    val totalIncome = transactions.filter { !it.isExpense }.sumOf { it.amount }
    val totalExpense = transactions.calculateTotalExpense(aggregateOnUsage)

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.8f)
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Scaffold(
                floatingActionButton = {
                    FloatingActionButton(onClick = {
                        editingTransaction = null // 新規作成モード
                        showInputBottomSheet = true
                    }) {
                        Icon(Icons.Filled.Add, contentDescription = "追加")
                    }
                }
            ) { innerPadding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                        .padding(16.dp)
                ) {
                    Text(
                        text = date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日")),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("収入合計: +$totalIncome", color = Color.Blue, fontWeight = FontWeight.Bold)
                        Text("支出合計: -$totalExpense", color = Color.Red, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 16.dp))

                    if (transactions.isEmpty()) {
                        Text("この日の登録はありません", color = Color.Gray)
                    } else {
                        LazyColumn {
                            items(transactions) { transaction ->
                                TransactionItemRow(
                                    transaction = transaction,
                                    onEditClick = {
                                        editingTransaction = transaction // 編集モード
                                        showInputBottomSheet = true
                                    },
                                    onDeleteClick = {
                                        transactionToDelete = transaction // 削除確認を出す
                                    }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }

    // 削除確認ダイアログ
    if (transactionToDelete != null) {
        AlertDialog(
            onDismissRequest = { transactionToDelete = null },
            title = { Text("削除の確認") },
            text = { Text("「${transactionToDelete?.title}」を削除してもよろしいですか？\n※クレジット利用分を削除すると、実際の引き落としデータも一緒に削除されます。") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteTransaction(transactionToDelete!!)
                    transactionToDelete = null
                }) {
                    Text("削除する", color = Color.Red)
                }
            },
            dismissButton = {
                TextButton(onClick = { transactionToDelete = null }) {
                    Text("キャンセル")
                }
            }
        )
    }

    // 入力・編集ボトムシート
    if (showInputBottomSheet) {
        ModalBottomSheet(onDismissRequest = { showInputBottomSheet = false }) {
            TransactionInputForm(
                date = date,
                initialTransaction = editingTransaction, // 編集データを渡す
                viewModel = viewModel,
                onSave = { title, amount, isExpense, category ->
                    if (editingTransaction == null) {
                        // 新規追加
                        viewModel.addTransaction(title, amount, isExpense, category, date.toString())
                    } else {
                        // 更新
                        val updated = editingTransaction!!.copy(
                            title = title,
                            amount = amount,
                            isExpense = isExpense,
                            category = category
                        )
                        viewModel.updateTransaction(updated)
                    }
                    showInputBottomSheet = false
                }
            )
        }
    }
}

@Composable
fun TransactionItemRow(
    transaction: Transaction,
    onEditClick: () -> Unit,
    onDeleteClick: () -> Unit
) {
    val amountColor = if (!transaction.isExpense) {
        Color.Blue
    } else if (transaction.isCreditPayment) {
        Color(0xFF6A1B9A)
    } else if (transaction.category == "クレジット") {
        Color(0xFFF57C00)
    } else {
        Color.Red
    }

    val amountPrefix = if (transaction.isExpense) "-" else "+"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(text = transaction.title, fontSize = 16.sp, fontWeight = FontWeight.Medium)
            Text(text = transaction.category, fontSize = 12.sp, color = Color.Gray)
        }
        Text(
            text = "$amountPrefix${transaction.amount}円",
            color = amountColor,
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        if (!transaction.isCreditPayment) {
            Row {
                IconButton(onClick = onEditClick) {
                    Icon(Icons.Filled.Edit, contentDescription = "編集", tint = Color.Gray)
                }
                IconButton(onClick = onDeleteClick) {
                    Icon(Icons.Filled.Delete, contentDescription = "削除", tint = Color.Red)
                }
            }
        } else {
            Text("自動計算", fontSize = 10.sp, color = Color.Gray, modifier = Modifier.padding(8.dp))
        }
    }
}

// === 入力フォーム（非表示設定のカテゴリーを除外する） ===
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionInputForm(
    date: LocalDate,
    initialTransaction: Transaction?,
    viewModel: TransactionViewModel,
    onSave: (String, Int, Boolean, String) -> Unit
) {
    var title by remember { mutableStateOf(initialTransaction?.title ?: "") }
    var amount by remember { mutableStateOf(initialTransaction?.amount?.toString() ?: "") }
    var isExpense by remember { mutableStateOf(initialTransaction?.isExpense ?: true) }
    var category by remember { mutableStateOf(initialTransaction?.category ?: "") }

    // ★すべてのカテゴリーを取得し、表示ON（isVisible == true）のものだけを絞り込む
    val allCategories by viewModel.allCategories.collectAsState(initial = emptyList<Category>())
    val visibleCategories = allCategories.filter { it.isVisible }

    var expanded by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .padding(bottom = 32.dp)
    ) {
        val modeText = if (initialTransaction == null) "新規登録" else "編集"
        Text(
            "$modeText: ${date.format(DateTimeFormatter.ofPattern("yyyy年MM月dd日"))}",
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(16.dp))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("収入")
            Switch(checked = isExpense, onCheckedChange = { isExpense = it })
            Text("支出")
        }

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = title,
            onValueChange = { title = it },
            label = { Text("内容 (例: スーパー)") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = amount,
            onValueChange = { amount = it },
            label = { Text("金額") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))

        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(if (category.isEmpty()) "カテゴリーを選択" else category)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                // ★絞り込んだ visibleCategories を表示
                visibleCategories.forEach { cat ->
                    DropdownMenuItem(
                        text = { Text(cat.name) },
                        onClick = { category = cat.name; expanded = false }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = {
                val amountInt = amount.toIntOrNull() ?: 0
                if (title.isNotEmpty() && amountInt > 0 && category.isNotEmpty()) {
                    onSave(title, amountInt, isExpense, category)
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (initialTransaction == null) "保存する" else "更新する")
        }
    }
}

// === 重複合算を防止する専用の計算ルール ===
fun List<Transaction>.calculateTotalExpense(aggregateOnUsage: Boolean): Int {
    return this.filter { it.isExpense }.filter {
        if (aggregateOnUsage) {
            !it.isCreditPayment // 利用日合算なら、引き落としデータは合計から除外する
        } else {
            !(it.category == "クレジット" && !it.isCreditPayment) // 引き落とし日合算なら、利用データは合計から除外する
        }
    }.sumOf { it.amount }
}