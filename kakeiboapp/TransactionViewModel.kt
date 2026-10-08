package com.example.kakeiboapp

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.kakeiboapp.data.AppDatabase
import com.example.kakeiboapp.data.Category
import com.example.kakeiboapp.data.CategoryNames
import com.example.kakeiboapp.data.PaymentMethod
import com.example.kakeiboapp.data.PaymentNames
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

private const val PREFS_NAME = "kakeibo_prefs"
private const val KEY_AGGREGATE_ON_USAGE = "aggregate_credit_on_usage"
private const val KEY_PAYMENT_DAY = "credit_payment_day"
private const val KEY_HOLIDAY_POLICY = "credit_holiday_policy"
private const val KEY_HOLIDAYS_JSON = "saved_holidays_json"
private const val KEY_SUBSCRIPTIONS_JSON = "subscriptions_json"

private const val HOLIDAYS_API_URL = "https://holidays-jp.github.io/api/v1/date.json"
private const val POLICY_FORWARD = "forward"   // 前倒し
private const val POLICY_BACKWARD = "backward" // 後倒し

/** サブスクの仮データのIDは「この値 - サブスクID」にして、DBのIDと重ならない負の数にする */
private const val VIRTUAL_ID_BASE = -1000

private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val YEAR_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

/** 祝日APIも保存データも使えないときの予備リスト（2026年分のみ） */
private val FALLBACK_HOLIDAYS_2026: Set<LocalDate> = setOf(
    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 2, 11),
    LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 29),
    LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
    LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 11), LocalDate.of(2026, 9, 21),
    LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 12),
    LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 23)
)

/** サブスクの仮データを作るのに必要な設定をまとめたもの */
private data class ScheduleParams(
    val subscriptions: List<Subscription>,
    val paymentDay: Int,
    val holidayPolicy: String,
    val holidays: Set<LocalDate>
)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: TransactionRepository
    val allCategories: Flow<List<Category>>
    val allPaymentMethods: Flow<List<PaymentMethod>>
    val allTransactions: Flow<List<Transaction>> // 全期間のデータ（残高計算用）

    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _aggregateCreditOnUsageDate = MutableStateFlow(prefs.getBoolean(KEY_AGGREGATE_ON_USAGE, true))
    val aggregateCreditOnUsageDate: StateFlow<Boolean> = _aggregateCreditOnUsageDate

    private val _creditPaymentDay = MutableStateFlow(prefs.getInt(KEY_PAYMENT_DAY, 27))
    val creditPaymentDay: StateFlow<Int> = _creditPaymentDay

    private val _creditHolidayPolicy = MutableStateFlow(prefs.getString(KEY_HOLIDAY_POLICY, POLICY_BACKWARD) ?: POLICY_BACKWARD)
    val creditHolidayPolicy: StateFlow<String> = _creditHolidayPolicy

    private val _holidays = MutableStateFlow<Set<LocalDate>>(emptySet())
    val holidays: StateFlow<Set<LocalDate>> = _holidays

    private val _subscriptions = MutableStateFlow<List<Subscription>>(emptyList())
    val subscriptions: StateFlow<List<Subscription>> = _subscriptions

    private val scheduleParams: Flow<ScheduleParams> =
        combine(subscriptions, creditPaymentDay, creditHolidayPolicy, holidays) { subs, day, policy, holidaySet ->
            ScheduleParams(subs, day, policy, holidaySet)
        }

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
                listOf(PaymentMethod(name = PaymentNames.CASH, isDefault = true), PaymentMethod(name = PaymentNames.CREDIT, isDefault = true))
                    .forEach { repository.insertPaymentMethod(it) }
            }
        }
        fetchHolidays()
        loadSubscriptions()
    }

    // ============================
    // 設定
    // ============================

    fun setAggregateCreditOnUsageDate(onUsage: Boolean) { prefs.edit().putBoolean(KEY_AGGREGATE_ON_USAGE, onUsage).apply(); _aggregateCreditOnUsageDate.value = onUsage }
    fun setCreditPaymentDay(day: Int) { prefs.edit().putInt(KEY_PAYMENT_DAY, day).apply(); _creditPaymentDay.value = day }
    fun setCreditHolidayPolicy(policy: String) { prefs.edit().putString(KEY_HOLIDAY_POLICY, policy).apply(); _creditHolidayPolicy.value = policy }

    // ============================
    // 祝日
    // ============================

    /** 祝日APIから取得する。失敗したときは保存済みのデータ、それも無ければ予備リストを使う。 */
    private fun fetchHolidays() = viewModelScope.launch {
        try {
            val jsonString = withContext(Dispatchers.IO) { URL(HOLIDAYS_API_URL).readText() }
            prefs.edit().putString(KEY_HOLIDAYS_JSON, jsonString).apply()
            _holidays.value = parseHolidaysJson(jsonString)
        } catch (e: Exception) {
            val savedJson = prefs.getString(KEY_HOLIDAYS_JSON, null)
            _holidays.value = if (savedJson != null) parseHolidaysJson(savedJson) else FALLBACK_HOLIDAYS_2026
        }
    }

    private fun parseHolidaysJson(jsonString: String): Set<LocalDate> {
        val jsonObject = JSONObject(jsonString)
        val dates = mutableSetOf<LocalDate>()
        for (key in jsonObject.keys()) dates.add(LocalDate.parse(key, DATE_FORMAT))
        return dates
    }

    // ============================
    // サブスクリプション
    // ============================

    private fun loadSubscriptions() {
        val array = JSONArray(prefs.getString(KEY_SUBSCRIPTIONS_JSON, "[]"))
        _subscriptions.value = (0 until array.length()).map { Subscription.fromJson(array.getJSONObject(it)) }
    }

    private fun saveSubscriptions(list: List<Subscription>) {
        val array = JSONArray()
        list.forEach { array.put(it.toJson()) }
        prefs.edit().putString(KEY_SUBSCRIPTIONS_JSON, array.toString()).apply()
        _subscriptions.value = list
    }

    fun addSubscription(name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String) {
        val nextId = (_subscriptions.value.maxOfOrNull { it.id } ?: 0) + 1
        saveSubscriptions(_subscriptions.value + Subscription(nextId, name, amount, isYearly, billingMonth, startYearMonth, null))
    }

    fun updateSubscription(id: Int, name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String) =
        saveSubscriptions(_subscriptions.value.map {
            if (it.id == id) it.copy(name = name, amount = amount, isYearly = isYearly, billingMonth = billingMonth, startYearMonth = startYearMonth) else it
        })

    fun terminateSubscription(id: Int, endYearMonth: String?) =
        saveSubscriptions(_subscriptions.value.map { if (it.id == id) it.copy(endYearMonth = endYearMonth) else it })

    fun deleteSubscription(id: Int) = saveSubscriptions(_subscriptions.value.filter { it.id != id })

    // ============================
    // 取引の取得（DBのデータ + サブスクの仮データ）
    // ============================

    fun getTransactionsByMonth(yearMonth: String): Flow<List<Transaction>> =
        combine(repository.getTransactionsByMonth(yearMonth), scheduleParams) { dbTx, params ->
            dbTx + generateVirtualSubscriptionsForMonth(yearMonth, params)
        }

    fun getTransactionsByYear(year: String): Flow<List<Transaction>> =
        combine(repository.getTransactionsByYear(year), scheduleParams) { dbTx, params ->
            val virtuals = (1..12).flatMap { generateVirtualSubscriptionsForMonth(String.format("%s-%02d", year, it), params) }
            dbTx + virtuals
        }

    /** 指定月に支払いのあるサブスクを、DBに保存しない仮の支出データとして作る。 */
    private fun generateVirtualSubscriptionsForMonth(yearMonth: String, params: ScheduleParams): List<Transaction> {
        val targetYearMonth = YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT)
        val paymentDate = withPaymentDay(targetYearMonth, params.paymentDay)
        val finalPaymentDate = adjustForWeekendAndHoliday(paymentDate, params.holidayPolicy, params.holidays)

        return params.subscriptions.mapNotNull { sub ->
            val startYM = YearMonth.parse(sub.startYearMonth, YEAR_MONTH_FORMAT)
            val endYM = sub.endYearMonth?.let { YearMonth.parse(it, YEAR_MONTH_FORMAT) }
            val isActive = !targetYearMonth.isBefore(startYM) && (endYM == null || !targetYearMonth.isAfter(endYM))
            val isBillingMonth = !sub.isYearly || sub.billingMonth == targetYearMonth.monthValue
            if (isActive && isBillingMonth) {
                Transaction(
                    id = VIRTUAL_ID_BASE - sub.id,
                    title = "${sub.name} (サブスク)",
                    amount = sub.amount,
                    isExpense = true,
                    category = CategoryNames.CREDIT_PAYMENT,
                    date = finalPaymentDate.toString(),
                    isCreditPayment = true,
                    paymentMethod = PaymentNames.CASH
                )
            } else null
        }
    }

    // ============================
    // 取引の追加・更新・削除（クレジットの引き落としデータとの連動）
    // ============================

    fun addTransaction(title: String, amount: Int, isExpense: Boolean, category: String, paymentMethod: String, dateString: String, isCharge: Boolean, chargeSource: String? = null) = viewModelScope.launch {
        val transaction = Transaction(title = title, amount = amount, isExpense = isExpense, category = category, date = dateString, isCreditPayment = false, paymentMethod = paymentMethod, isCharge = isCharge, chargeSource = chargeSource)
        val insertedId = repository.insert(transaction)

        if (isExpense && paymentMethod == PaymentNames.CREDIT && !isCharge) {
            createCreditPaymentData(insertedId.toInt(), title, amount, dateString)
        }
        if (isCharge && chargeSource == PaymentNames.CREDIT) {
            createCreditPaymentData(insertedId.toInt(), "$title (チャージ分)", amount, dateString)
        }
    }

    // 編集時もチャージ元（chargeSource）を見て、クレジット連動を維持する
    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.update(transaction)
        val existingPaymentData = repository.getCreditPaymentTransaction(transaction.id)

        // クレジットの引き落としデータが必要な条件
        val needsCreditPayment = (transaction.isExpense && transaction.paymentMethod == PaymentNames.CREDIT && !transaction.isCharge) ||
                (transaction.isCharge && transaction.chargeSource == PaymentNames.CREDIT)

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

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.getCreditPaymentTransaction(transaction.id)?.let { repository.delete(it) }
        repository.delete(transaction)
    }

    private suspend fun createCreditPaymentData(parentId: Int, title: String, amount: Int, dateString: String) {
        val paymentDate = calculateCreditPaymentDate(dateString)
        repository.insert(Transaction(title = "${title}（引き落とし）", amount = amount, isExpense = true, category = CategoryNames.CREDIT_PAYMENT, date = paymentDate, parentTransactionId = parentId, isCreditPayment = true, paymentMethod = PaymentNames.CASH, isCharge = false))
    }

    // ============================
    // 引き落とし日の計算
    // ============================

    /** 利用日の翌月の引き落とし日（休日調整済み）を "yyyy-MM-dd" で返す。 */
    private fun calculateCreditPaymentDate(usageDateString: String): String {
        val usageDate = LocalDate.parse(usageDateString, DATE_FORMAT)
        val paymentDate = withPaymentDay(YearMonth.from(usageDate.plusMonths(1)), _creditPaymentDay.value)
        return adjustForWeekendAndHoliday(paymentDate, _creditHolidayPolicy.value, _holidays.value).format(DATE_FORMAT)
    }

    /** 指定月の引き落とし指定日。月末を超える場合（31日指定で30日の月など）は月末にする。 */
    private fun withPaymentDay(yearMonth: YearMonth, paymentDay: Int): LocalDate =
        yearMonth.atDay(minOf(paymentDay, yearMonth.lengthOfMonth()))

    /** 土日・祝日を避けて、前倒し（forward）なら前へ、後倒しなら後ろへ平日になるまでずらす。 */
    private fun adjustForWeekendAndHoliday(date: LocalDate, policy: String, holidaySet: Set<LocalDate>): LocalDate {
        var adjustedDate = date
        fun isHolidayOrWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || holidaySet.contains(d)
        while (isHolidayOrWeekend(adjustedDate)) {
            adjustedDate = if (policy == POLICY_FORWARD) adjustedDate.minusDays(1) else adjustedDate.plusDays(1)
        }
        return adjustedDate
    }

    // ============================
    // カテゴリー・支払方法
    // ============================

    fun addCategory(name: String) = viewModelScope.launch { repository.insertCategory(Category(name = name)) }
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    fun addPaymentMethod(name: String) = viewModelScope.launch { repository.insertPaymentMethod(PaymentMethod(name = name)) }
    fun deletePaymentMethod(method: PaymentMethod) = viewModelScope.launch { if (!method.isDefault) repository.deletePaymentMethod(method) }
}
