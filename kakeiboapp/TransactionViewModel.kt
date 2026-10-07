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

    // クレジットカードの引き落とし日を計算するロジック（設定値と休日判定を反映）
    private fun adjustForWeekendAndHoliday(date: LocalDate, policy: String): LocalDate {
        var adjustedDate = date

        // （参考）2026年〜2027年の主な祝日リスト（必要に応じて追加・更新してください）
        val holidays = listOf(
            LocalDate.of(2026, 10, 12), LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 23),
            LocalDate.of(2027, 1, 1), LocalDate.of(2027, 1, 11), LocalDate.of(2027, 2, 11),
            LocalDate.of(2027, 2, 23), LocalDate.of(2027, 3, 22), LocalDate.of(2027, 4, 29),
            LocalDate.of(2027, 5, 3), LocalDate.of(2027, 5, 4), LocalDate.of(2027, 5, 5),
            LocalDate.of(2027, 7, 19), LocalDate.of(2027, 8, 11), LocalDate.of(2027, 9, 20),
            LocalDate.of(2027, 9, 23), LocalDate.of(2027, 10, 11), LocalDate.of(2027, 11, 3),
            LocalDate.of(2027, 11, 23)
        )

        fun isHolidayOrWeekend(d: LocalDate): Boolean {
            return d.dayOfWeek == DayOfWeek.SATURDAY ||
                    d.dayOfWeek == DayOfWeek.SUNDAY ||
                    holidays.contains(d)
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