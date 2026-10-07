package com.example.kakeiboapp // ここは自動で設定されるものに合わせます

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
// 以下３行の「あなたのお使いのパッケージ名」を「kakeiboapp」に修正しました
import com.example.kakeiboapp.data.AppDatabase
import com.example.kakeiboapp.data.Category
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.format.DateTimeFormatter
import org.json.JSONObject
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    val allCategories: Flow<List<Category>>

    // ▼ 設定の保存・読み込み用
    private val prefs = application.getSharedPreferences("kakeibo_prefs", Context.MODE_PRIVATE)

    private val _aggregateCreditOnUsageDate = MutableStateFlow(prefs.getBoolean("aggregate_credit_on_usage", true))
    val aggregateCreditOnUsageDate: StateFlow<Boolean> = _aggregateCreditOnUsageDate

    fun setAggregateCreditOnUsageDate(onUsage: Boolean) {
        prefs.edit().putBoolean("aggregate_credit_on_usage", onUsage).apply()
        _aggregateCreditOnUsageDate.value = onUsage
    }

    // 引き落とし日と休日対応の設定
    private val _creditPaymentDay = MutableStateFlow(prefs.getInt("credit_payment_day", 27))
    val creditPaymentDay: StateFlow<Int> = _creditPaymentDay

    private val _creditHolidayPolicy = MutableStateFlow(prefs.getString("credit_holiday_policy", "backward") ?: "backward")
    val creditHolidayPolicy: StateFlow<String> = _creditHolidayPolicy

    fun setCreditPaymentDay(day: Int) {
        prefs.edit().putInt("credit_payment_day", day).apply()
        _creditPaymentDay.value = day
    }

    fun setCreditHolidayPolicy(policy: String) { // "forward" (前倒し) or "backward" (後ろ倒し)
        prefs.edit().putString("credit_holiday_policy", policy).apply()
        _creditHolidayPolicy.value = policy
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TransactionRepository(database.transactionDao(), database.categoryDao())
        allCategories = repository.allCategories

        // アプリ起動時にカテゴリーが空ならデフォルトを自動追加
        viewModelScope.launch {
            if (repository.getCategoryCount() == 0) {
                val defaultCategories = listOf(
                    Category(name = "食費"),
                    Category(name = "日用品"),
                    Category(name = "交通費"),
                    Category(name = "クレジット", isDefault = true),
                    Category(name = "給料"),
                    Category(name = "その他")
                )
                defaultCategories.forEach { repository.insertCategory(it) }
            }
        }

        fetchHolidays()
    }

    fun getTransactionsByMonth(yearMonth: String): Flow<List<Transaction>> {
        return repository.getTransactionsByMonth(yearMonth)
    }

    // データの追加と、クレジット連動処理
    fun addTransaction(title: String, amount: Int, isExpense: Boolean, category: String, dateString: String) = viewModelScope.launch {

        // 1. ユーザーが入力した「利用データ」を作成
        val transaction = Transaction(
            title = title,
            amount = amount,
            isExpense = isExpense,
            category = category,
            date = dateString,
            isCreditPayment = false
        )

        // 2. 利用データを保存し、保存されたIDを取得
        val insertedId = repository.insert(transaction)

        // 3. カテゴリーが「クレジット」の場合、支払日データを自動生成して保存
        if (category == "クレジット") {
            createCreditPaymentData(insertedId.toInt(), title, amount, dateString)
        }
    }

    // データの更新と、紐づくクレジット支払日データの更新
    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        // 1. 元のデータを更新
        repository.update(transaction)

        // 2. カテゴリーが「クレジット」に変更された、または金額などが変更された場合の処理
        val existingPaymentData = repository.getCreditPaymentTransaction(transaction.id)

        if (transaction.category == "クレジット") {
            if (existingPaymentData != null) {
                // すでに支払日データがあれば、金額や日付を更新
                val paymentDate = calculateCreditPaymentDate(transaction.date)
                repository.update(
                    existingPaymentData.copy(
                        title = "${transaction.title}（引き落とし）",
                        amount = transaction.amount,
                        date = paymentDate
                    )
                )
            } else {
                // クレジットに変更されたが支払日データがない場合は新規作成
                createCreditPaymentData(transaction.id, transaction.title, transaction.amount, transaction.date)
            }
        } else {
            // カテゴリーがクレジット以外に変更された場合、紐づく支払日データがあれば削除
            existingPaymentData?.let { repository.delete(it) }
        }
    }

    // データの削除と、道連れ削除処理
    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        // 紐づく支払日データがあれば削除
        val existingPaymentData = repository.getCreditPaymentTransaction(transaction.id)
        existingPaymentData?.let { repository.delete(it) }

        // 元のデータを削除
        repository.delete(transaction)
    }

    fun updateCategory(category: Category) = viewModelScope.launch {
        repository.updateCategory(category)
    }

    // --- プライベートなヘルパー関数 ---

    // 支払日データを作成・保存する処理
    private suspend fun createCreditPaymentData(parentId: Int, title: String, amount: Int, dateString: String) {
        val paymentDate = calculateCreditPaymentDate(dateString)

        val paymentTransaction = Transaction(
            title = "${title}（引き落とし）",
            amount = amount,
            isExpense = true, // 引き落としは必ず支出扱い
            category = "クレジット引き落とし",
            date = paymentDate,
            parentTransactionId = parentId,
            isCreditPayment = true
        )
        repository.insert(paymentTransaction)
    }

    private fun calculateCreditPaymentDate(usageDateString: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val usageDate = LocalDate.parse(usageDateString, formatter)

        val paymentDay = _creditPaymentDay.value
        val policy = _creditHolidayPolicy.value

        // 基本の引き落とし日（翌月の指定日）を計算
        var paymentDate = usageDate.plusMonths(1)
        val maxDayOfMonth = paymentDate.lengthOfMonth()
        val actualPaymentDay = if (paymentDay > maxDayOfMonth) maxDayOfMonth else paymentDay
        paymentDate = paymentDate.withDayOfMonth(actualPaymentDay)

        // 土日・祝日の補正処理
        return adjustForWeekendAndHoliday(paymentDate, policy).format(formatter)
    }

    // クレジットカードの引き落とし日を計算するロジック（自動取得した祝日判定を反映）
    private fun adjustForWeekendAndHoliday(date: LocalDate, policy: String): LocalDate {
        var adjustedDate = date
        // ★ 自動取得して保持している最新の祝日データを読み込む
        val currentHolidays = _holidays.value

        fun isHolidayOrWeekend(d: LocalDate): Boolean {
            return d.dayOfWeek == DayOfWeek.SATURDAY ||
                    d.dayOfWeek == DayOfWeek.SUNDAY ||
                    currentHolidays.contains(d) // ★ 取得した祝日データの中に存在するかチェック
        }

        // 休日である限り、設定に応じて1日ずつ前(または後ろ)にずらす
        while (isHolidayOrWeekend(adjustedDate)) {
            adjustedDate = if (policy == "forward") {
                adjustedDate.minusDays(1) // 前倒し
            } else {
                adjustedDate.plusDays(1)  // 後ろ倒し
            }
        }
        return adjustedDate
    }

    // ★ 追加1: 自動取得した祝日を保持する変数（既存）
    private val _holidays = MutableStateFlow<Set<LocalDate>>(emptySet())

    // ▼ これを追加（UIから読み取るための公開用変数）
    val holidays: StateFlow<Set<LocalDate>> = _holidays

    // ★ 追加2: 祝日自動取得メソッド (キャッシュ機能付き)
    private fun fetchHolidays() = viewModelScope.launch {
        try {
            val jsonString = withContext(Dispatchers.IO) {
                // APIから最新の祝日リスト（JSON）をダウンロード
                URL("https://holidays-jp.github.io/api/v1/date.json").readText()
            }

            // ダウンロードに成功したら、次回のためにスマホ本体に保存（上書き）
            prefs.edit().putString("saved_holidays_json", jsonString).apply()

            // データを日付のリストに変換して反映
            _holidays.value = parseHolidaysJson(jsonString)

        } catch (e: Exception) {
            // 通信に失敗した場合（オフラインなど）は、最後に保存したデータを読み込む
            val savedJson = prefs.getString("saved_holidays_json", null)

            if (savedJson != null) {
                // 過去に1度でも取得に成功していれば、そのデータを使う
                _holidays.value = parseHolidaysJson(savedJson)
            } else {
                // インストール直後の初回起動かつオフラインの場合のみ、最低限の予備データを使用
                _holidays.value = setOf(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 2, 11),
                    LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 29),
                    LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
                    LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 11), LocalDate.of(2026, 9, 21),
                    LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 12),
                    LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 23)
                )
            }
        }
    }

    // ★ 追加3: JSONテキストを日付のリストに変換する共通ロジック
    private fun parseHolidaysJson(jsonString: String): Set<LocalDate> {
        val jsonObject = JSONObject(jsonString)
        val dates = mutableSetOf<LocalDate>()
        val keys = jsonObject.keys()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        while (keys.hasNext()) {
            val dateStr = keys.next()
            dates.add(LocalDate.parse(dateStr, formatter))
        }
        return dates
    }

    fun getTransactionsByYear(year: String): Flow<List<Transaction>> {
        return repository.getTransactionsByYear(year)
    }

    fun addCategory(name: String) = viewModelScope.launch {
        repository.insertCategory(Category(name = name))
    }

    fun deleteCategory(category: Category) = viewModelScope.launch {
        // デフォルトカテゴリーは削除させない保護
        if (!category.isDefault) {
            repository.deleteCategory(category)
        }
    }
}