package com.example.kakeiboapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kakeiboapp.data.AppDatabase
import com.example.kakeiboapp.data.Category
import com.example.kakeiboapp.data.PaymentMethod
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
    val id: Int, val name: String, val amount: Int, val isYearly: Boolean,
    val billingMonth: Int, val startYearMonth: String, val endYearMonth: String? = null
)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    val allCategories: Flow<List<Category>>
    val allPaymentMethods: Flow<List<PaymentMethod>>
    val allTransactions: Flow<List<Transaction>> // ★追加: 全期間のデータ

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
        repository = TransactionRepository(database.transactionDao(), database.categoryDao(), database.paymentMethodDao())
        allCategories = repository.allCategories
        allPaymentMethods = repository.allPaymentMethods
        allTransactions = repository.allTransactions

        viewModelScope.launch {
            if (repository.getCategoryCount() == 0) {
                listOf("食費", "日用品", "交通費", "給料", "その他").forEach { repository.insertCategory(Category(name = it)) }
            }
            if (repository.getPaymentMethodCount() == 0) {
                listOf(PaymentMethod(name = "現金", isDefault = true), PaymentMethod(name = "クレジット", isDefault = true))
                    .forEach { repository.insertPaymentMethod(it) }
            }
        }
        fetchHolidays()
        loadSubscriptions()
    }

    fun setAggregateCreditOnUsageDate(onUsage: Boolean) { prefs.edit().putBoolean("aggregate_credit_on_usage", onUsage).apply(); _aggregateCreditOnUsageDate.value = onUsage }
    fun setCreditPaymentDay(day: Int) { prefs.edit().putInt("credit_payment_day", day).apply(); _creditPaymentDay.value = day }
    fun setCreditHolidayPolicy(policy: String) { prefs.edit().putString("credit_holiday_policy", policy).apply(); _creditHolidayPolicy.value = policy }

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
                // ★修正: 完全オフライン時の十分な予備リストを復元
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
    private fun parseHolidaysJson(jsonString: String): Set<LocalDate> {
        val jsonObject = JSONObject(jsonString)
        val dates = mutableSetOf<LocalDate>()
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
        for (key in jsonObject.keys()) dates.add(LocalDate.parse(key, formatter))
        return dates
    }

    private fun loadSubscriptions() {
        val array = JSONArray(prefs.getString("subscriptions_json", "[]"))
        val list = (0 until array.length()).map { i ->
            val obj = array.getJSONObject(i)
            Subscription(obj.getInt("id"), obj.getString("name"), obj.getInt("amount"), obj.getBoolean("isYearly"), obj.getInt("billingMonth"), obj.optString("startYearMonth", "2000-01"), if (obj.has("endYearMonth") && !obj.isNull("endYearMonth")) obj.getString("endYearMonth") else null)
        }
        _subscriptions.value = list
    }
    private fun saveSubscriptions(list: List<Subscription>) {
        val array = JSONArray()
        list.forEach { sub ->
            val obj = JSONObject().apply { put("id", sub.id); put("name", sub.name); put("amount", sub.amount); put("isYearly", sub.isYearly); put("billingMonth", sub.billingMonth); put("startYearMonth", sub.startYearMonth); if (sub.endYearMonth != null) put("endYearMonth", sub.endYearMonth) }
            array.put(obj)
        }
        prefs.edit().putString("subscriptions_json", array.toString()).apply()
        _subscriptions.value = list
    }
    fun addSubscription(name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String) = saveSubscriptions(_subscriptions.value.toMutableList().apply { add(Subscription((maxOfOrNull { it.id } ?: 0) + 1, name, amount, isYearly, billingMonth, startYearMonth, null)) })
    fun updateSubscription(id: Int, name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String) = saveSubscriptions(_subscriptions.value.map { if (it.id == id) it.copy(name = name, amount = amount, isYearly = isYearly, billingMonth = billingMonth, startYearMonth = startYearMonth) else it })
    fun terminateSubscription(id: Int, endYearMonth: String?) = saveSubscriptions(_subscriptions.value.map { if (it.id == id) it.copy(endYearMonth = endYearMonth) else it })
    fun deleteSubscription(id: Int) = saveSubscriptions(_subscriptions.value.filter { it.id != id })

    fun getTransactionsByMonth(yearMonth: String): Flow<List<Transaction>> {
        val paramsFlow = combine(subscriptions, creditPaymentDay, creditHolidayPolicy, holidays) { s, pd, p, h -> Triple(s, pd, p) }
        return combine(repository.getTransactionsByMonth(yearMonth), paramsFlow) { dbTx, (subs, pd, pol) -> dbTx + generateVirtualSubscriptionsForMonth(yearMonth, subs, pd, pol, _holidays.value) }
    }
    fun getTransactionsByYear(year: String): Flow<List<Transaction>> {
        val paramsFlow = combine(subscriptions, creditPaymentDay, creditHolidayPolicy, holidays) { s, pd, p, h -> Triple(s, pd, p) }
        return combine(repository.getTransactionsByYear(year), paramsFlow) { dbTx, (subs, pd, pol) ->
            val virtuals = (1..12).flatMap { generateVirtualSubscriptionsForMonth(String.format("%s-%02d", year, it), subs, pd, pol, _holidays.value) }
            dbTx + virtuals
        }
    }

    private fun generateVirtualSubscriptionsForMonth(yearMonth: String, subs: List<Subscription>, paymentDay: Int, policy: String, holidaySet: Set<LocalDate>): List<Transaction> {
        val parsedYearMonth = YearMonth.parse(yearMonth, DateTimeFormatter.ofPattern("yyyy-MM"))
        var paymentDate = parsedYearMonth.atDay(1)
        val maxDay = paymentDate.lengthOfMonth()
        paymentDate = paymentDate.withDayOfMonth(if (paymentDay > maxDay) maxDay else paymentDay)
        val finalPaymentDate = adjustForWeekendAndHoliday(paymentDate, policy, holidaySet)

        return subs.mapNotNull { sub ->
            val subStartYM = YearMonth.parse(sub.startYearMonth, DateTimeFormatter.ofPattern("yyyy-MM"))
            val subEndYM = sub.endYearMonth?.let { YearMonth.parse(it, DateTimeFormatter.ofPattern("yyyy-MM")) }
            if (!parsedYearMonth.isBefore(subStartYM) && (subEndYM == null || !parsedYearMonth.isAfter(subEndYM)) && (!sub.isYearly || sub.billingMonth == parsedYearMonth.monthValue)) {
                Transaction(-1000 - sub.id, "${sub.name} (サブスク)", sub.amount, true, "口座引落", finalPaymentDate.toString(), null, true, "現金", false, null)
            } else null
        }
    }

    // ★修正: chargeSourceをDBに保存
    fun addTransaction(title: String, amount: Int, isExpense: Boolean, category: String, paymentMethod: String, dateString: String, isCharge: Boolean, chargeSource: String? = null) = viewModelScope.launch {
        val transaction = Transaction(title = title, amount = amount, isExpense = isExpense, category = category, date = dateString, isCreditPayment = false, paymentMethod = paymentMethod, isCharge = isCharge, chargeSource = chargeSource)
        val insertedId = repository.insert(transaction)

        if (isExpense && paymentMethod == "クレジット" && !isCharge) {
            createCreditPaymentData(insertedId.toInt(), title, amount, dateString)
        }
        if (isCharge && chargeSource == "クレジット") {
            createCreditPaymentData(insertedId.toInt(), "$title (チャージ分)", amount, dateString)
        }
    }

    // ★修正: 編集時にチャージ元（chargeSource）を見てクレジット連動を維持する
    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.update(transaction)
        val existingPaymentData = repository.getCreditPaymentTransaction(transaction.id)

        // クレジットの引き落としデータが必要な条件
        val needsCreditPayment = (transaction.isExpense && transaction.paymentMethod == "クレジット" && !transaction.isCharge) ||
                (transaction.isCharge && transaction.chargeSource == "クレジット")

        if (needsCreditPayment) {
            if (existingPaymentData != null) {
                val paymentDate = calculateCreditPaymentDate(transaction.date)
                repository.update(existingPaymentData.copy(title = "${transaction.title}（引き落とし）", amount = transaction.amount, date = paymentDate))
            } else {
                val paymentTitle = if (transaction.isCharge) "${transaction.title} (チャージ分)" else transaction.title
                createCreditPaymentData(transaction.id, paymentTitle, transaction.amount, transaction.date)
            }
        } else {
            // 現金払いや現金チャージに変更された場合は引き落としデータを削除
            existingPaymentData?.let { repository.delete(it) }
        }
    }

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch { repository.getCreditPaymentTransaction(transaction.id)?.let { repository.delete(it) }; repository.delete(transaction) }

    private suspend fun createCreditPaymentData(parentId: Int, title: String, amount: Int, dateString: String) {
        val paymentDate = calculateCreditPaymentDate(dateString)
        repository.insert(Transaction(title = "${title}（引き落とし）", amount = amount, isExpense = true, category = "口座引落", date = paymentDate, parentTransactionId = parentId, isCreditPayment = true, paymentMethod = "現金", isCharge = false))
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

    fun addCategory(name: String) = viewModelScope.launch { repository.insertCategory(Category(name = name)) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    fun addPaymentMethod(name: String) = viewModelScope.launch { repository.insertPaymentMethod(PaymentMethod(name = name)) }
    fun deletePaymentMethod(method: PaymentMethod) = viewModelScope.launch { if (!method.isDefault) repository.deletePaymentMethod(method) }
}