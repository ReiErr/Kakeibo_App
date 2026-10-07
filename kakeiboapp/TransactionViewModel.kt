package com.example.kakeiboapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kakeiboapp.data.AppDatabase
import com.example.kakeiboapp.data.Category
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.LocalDate
import java.time.DayOfWeek
import java.time.YearMonth
import java.time.format.DateTimeFormatter

data class Subscription(
    val id: Int,
    val name: String,
    val amount: Int,
    val isYearly: Boolean,
    val billingMonth: Int,
    val startYearMonth: String,
    val endYearMonth: String? = null
)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    val allCategories: Flow<List<Category>>

    private val prefs = application.getSharedPreferences("kakeibo_prefs", Context.MODE_PRIVATE)

    private val _aggregateCreditOnUsageDate = MutableStateFlow(prefs.getBoolean("aggregate_credit_on_usage", true))
    val aggregateCreditOnUsageDate: StateFlow<Boolean> = _aggregateCreditOnUsageDate

    private val _creditPaymentDay = MutableStateFlow(prefs.getInt("credit_payment_day", 27))
    val creditPaymentDay: StateFlow<Int> = _creditPaymentDay

    private val _creditHolidayPolicy = MutableStateFlow(prefs.getString("credit_holiday_policy", "backward") ?: "backward")
    val creditHolidayPolicy: StateFlow<String> = _creditHolidayPolicy

    private val _holidays = MutableStateFlow<Set<LocalDate>>(emptySet())
    val holidays: StateFlow<Set<LocalDate>> = _holidays

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TransactionRepository(database.transactionDao(), database.categoryDao())
        allCategories = repository.allCategories

        viewModelScope.launch {
            if (repository.getCategoryCount() == 0) {
                val defaultCategories = listOf(
                    Category(name = "食費"), Category(name = "日用品"), Category(name = "交通費"),
                    Category(name = "クレジット", isDefault = true), Category(name = "給料"), Category(name = "その他")
                )
                defaultCategories.forEach { repository.insertCategory(it) }
            }
        }
        fetchHolidays()
        loadSubscriptions()
    }

    fun setAggregateCreditOnUsageDate(onUsage: Boolean) {
        prefs.edit().putBoolean("aggregate_credit_on_usage", onUsage).apply()
        _aggregateCreditOnUsageDate.value = onUsage
    }

    fun setCreditPaymentDay(day: Int) {
        prefs.edit().putInt("credit_payment_day", day).apply()
        _creditPaymentDay.value = day
    }

    fun setCreditHolidayPolicy(policy: String) {
        prefs.edit().putString("credit_holiday_policy", policy).apply()
        _creditHolidayPolicy.value = policy
    }

    private fun fetchHolidays() = viewModelScope.launch {
        try {
            val jsonString = withContext(Dispatchers.IO) { URL("https://holidays-jp.github.io/api/v1/date.json").readText() }
            prefs.edit().putString("saved_holidays_json", jsonString).apply()
            _holidays.value = parseHolidaysJson(jsonString)
        } catch (e: Exception) {
            val savedJson = prefs.getString("saved_holidays_json", null)
            if (savedJson != null) {
                _holidays.value = parseHolidaysJson(savedJson)
            } else {
                _holidays.value = setOf(
                    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 2, 11),
                    LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 29)
                )
            }
        }
    }

    private fun parseHolidaysJson(jsonString: String): Set<LocalDate> {
        val jsonObject = JSONObject(jsonString)
        val dates = mutableSetOf<LocalDate>()
        val keys = jsonObject.keys()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        while (keys.hasNext()) { dates.add(LocalDate.parse(keys.next(), formatter)) }
        return dates
    }

    // --- サブスクリプション管理 ---
    private fun loadSubscriptions() {
        val json = prefs.getString("subscriptions_json", "[]")
        val array = JSONArray(json)
        val list = mutableListOf<Subscription>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            val startYM = obj.optString("startYearMonth", "2000-01")
            val endYM = if (obj.has("endYearMonth") && !obj.isNull("endYearMonth")) obj.getString("endYearMonth") else null
            list.add(Subscription(obj.getInt("id"), obj.getString("name"), obj.getInt("amount"), obj.getBoolean("isYearly"), obj.getInt("billingMonth"), startYM, endYM))
        }
        _subscriptions.value = list
    }

    fun addSubscription(name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String) {
        val current = _subscriptions.value.toMutableList()
        val newId = (current.maxOfOrNull { it.id } ?: 0) + 1
        current.add(Subscription(newId, name, amount, isYearly, billingMonth, startYearMonth, null))
        saveSubscriptions(current)
    }

    // ★追加: サブスクリプションの基本情報を更新する
    fun updateSubscription(id: Int, name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String) {
        val current = _subscriptions.value.map {
            if (it.id == id) {
                it.copy(name = name, amount = amount, isYearly = isYearly, billingMonth = billingMonth, startYearMonth = startYearMonth)
            } else {
                it
            }
        }
        saveSubscriptions(current)
    }

    fun terminateSubscription(id: Int, endYearMonth: String?) {
        val current = _subscriptions.value.map {
            if (it.id == id) it.copy(endYearMonth = endYearMonth) else it
        }
        saveSubscriptions(current)
    }

    fun deleteSubscription(id: Int) {
        val current = _subscriptions.value.filter { it.id != id }
        saveSubscriptions(current)
    }

    private fun saveSubscriptions(list: List<Subscription>) {
        val array = JSONArray()
        list.forEach { sub ->
            val obj = JSONObject()
            obj.put("id", sub.id); obj.put("name", sub.name); obj.put("amount", sub.amount)
            obj.put("isYearly", sub.isYearly); obj.put("billingMonth", sub.billingMonth)
            obj.put("startYearMonth", sub.startYearMonth)
            if (sub.endYearMonth != null) obj.put("endYearMonth", sub.endYearMonth)
            array.put(obj)
        }
        prefs.edit().putString("subscriptions_json", array.toString()).apply()
        _subscriptions.value = list
    }

    // --- トランザクション処理 ---
    private data class SubParams(val subs: List<Subscription>, val paymentDay: Int, val policy: String, val holidaySet: Set<LocalDate>)

    fun getTransactionsByMonth(yearMonth: String): Flow<List<Transaction>> {
        val paramsFlow = combine(subscriptions, creditPaymentDay, creditHolidayPolicy, holidays) { s, pd, p, h -> SubParams(s, pd, p, h) }
        return combine(repository.getTransactionsByMonth(yearMonth), paramsFlow) { dbTx, params ->
            val virtuals = generateVirtualSubscriptionsForMonth(yearMonth, params.subs, params.paymentDay, params.policy, params.holidaySet)
            dbTx + virtuals
        }
    }

    fun getTransactionsByYear(year: String): Flow<List<Transaction>> {
        val paramsFlow = combine(subscriptions, creditPaymentDay, creditHolidayPolicy, holidays) { s, pd, p, h -> SubParams(s, pd, p, h) }
        return combine(repository.getTransactionsByYear(year), paramsFlow) { dbTx, params ->
            val virtuals = mutableListOf<Transaction>()
            for (month in 1..12) {
                virtuals.addAll(generateVirtualSubscriptionsForMonth(String.format("%s-%02d", year, month), params.subs, params.paymentDay, params.policy, params.holidaySet))
            }
            dbTx + virtuals
        }
    }

    private fun generateVirtualSubscriptionsForMonth(yearMonth: String, subs: List<Subscription>, paymentDay: Int, policy: String, holidaySet: Set<LocalDate>): List<Transaction> {
        val parsedYearMonth = YearMonth.parse(yearMonth, DateTimeFormatter.ofPattern("yyyy-MM"))

        var paymentDate = parsedYearMonth.atDay(1)
        val maxDay = paymentDate.lengthOfMonth()
        paymentDate = paymentDate.withDayOfMonth(if (paymentDay > maxDay) maxDay else paymentDay)
        val finalPaymentDate = adjustForWeekendAndHoliday(paymentDate, policy, holidaySet)

        val virtuals = mutableListOf<Transaction>()

        subs.forEach { sub ->
            val subStartYM = YearMonth.parse(sub.startYearMonth, DateTimeFormatter.ofPattern("yyyy-MM"))
            val subEndYM = sub.endYearMonth?.let { YearMonth.parse(it, DateTimeFormatter.ofPattern("yyyy-MM")) }

            if (!parsedYearMonth.isBefore(subStartYM) && (subEndYM == null || !parsedYearMonth.isAfter(subEndYM))) {
                if (!sub.isYearly || sub.billingMonth == parsedYearMonth.monthValue) {
                    virtuals.add(Transaction(-1000 - sub.id, "${sub.name} (サブスク)", sub.amount, true, "クレジット引き落とし", finalPaymentDate.toString(), null, true))
                }
            }
        }
        return virtuals
    }

    // --- 既存のデータ処理 ---
    fun addTransaction(title: String, amount: Int, isExpense: Boolean, category: String, dateString: String) = viewModelScope.launch {
        val transaction = Transaction(title = title, amount = amount, isExpense = isExpense, category = category, date = dateString, isCreditPayment = false)
        val insertedId = repository.insert(transaction)
        if (category == "クレジット") createCreditPaymentData(insertedId.toInt(), title, amount, dateString)
    }

    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.update(transaction)
        val existingPaymentData = repository.getCreditPaymentTransaction(transaction.id)
        if (transaction.category == "クレジット") {
            if (existingPaymentData != null) {
                val paymentDate = calculateCreditPaymentDate(transaction.date)
                repository.update(existingPaymentData.copy(title = "${transaction.title}（引き落とし）", amount = transaction.amount, date = paymentDate))
            } else {
                createCreditPaymentData(transaction.id, transaction.title, transaction.amount, transaction.date)
            }
        } else {
            existingPaymentData?.let { repository.delete(it) }
        }
    }

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.getCreditPaymentTransaction(transaction.id)?.let { repository.delete(it) }
        repository.delete(transaction)
    }

    private suspend fun createCreditPaymentData(parentId: Int, title: String, amount: Int, dateString: String) {
        val paymentDate = calculateCreditPaymentDate(dateString)
        repository.insert(Transaction(title = "${title}（引き落とし）", amount = amount, isExpense = true, category = "クレジット引き落とし", date = paymentDate, parentTransactionId = parentId, isCreditPayment = true))
    }

    private fun calculateCreditPaymentDate(usageDateString: String): String {
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        val usageDate = LocalDate.parse(usageDateString, formatter)
        var paymentDate = usageDate.plusMonths(1)
        val maxDay = paymentDate.lengthOfMonth()
        val paymentDay = _creditPaymentDay.value
        paymentDate = paymentDate.withDayOfMonth(if (paymentDay > maxDay) maxDay else paymentDay)
        return adjustForWeekendAndHoliday(paymentDate, _creditHolidayPolicy.value, _holidays.value).format(formatter)
    }

    private fun adjustForWeekendAndHoliday(date: LocalDate, policy: String, holidaySet: Set<LocalDate>): LocalDate {
        var adjustedDate = date
        fun isHolidayOrWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || holidaySet.contains(d)
        while (isHolidayOrWeekend(adjustedDate)) { adjustedDate = if (policy == "forward") adjustedDate.minusDays(1) else adjustedDate.plusDays(1) }
        return adjustedDate
    }

    fun updateCategory(category: Category) = viewModelScope.launch { repository.updateCategory(category) }
    fun addCategory(name: String) = viewModelScope.launch { repository.insertCategory(Category(name = name)) }
    fun deleteCategory(category: Category) = viewModelScope.launch { if (!category.isDefault) repository.deleteCategory(category) }
}