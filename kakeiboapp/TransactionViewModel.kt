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
import com.example.kakeiboapp.data.PaymentTypes
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
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

private const val VIRTUAL_ID_BASE = -1000
private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
private val YEAR_MONTH_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM")

private val FALLBACK_HOLIDAYS_2026: Set<LocalDate> = setOf(
    LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 12), LocalDate.of(2026, 2, 11),
    LocalDate.of(2026, 2, 23), LocalDate.of(2026, 3, 20), LocalDate.of(2026, 4, 29),
    LocalDate.of(2026, 5, 3), LocalDate.of(2026, 5, 4), LocalDate.of(2026, 5, 5),
    LocalDate.of(2026, 7, 20), LocalDate.of(2026, 8, 11), LocalDate.of(2026, 9, 21),
    LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23), LocalDate.of(2026, 10, 12),
    LocalDate.of(2026, 11, 3), LocalDate.of(2026, 11, 23)
)

private data class ScheduleParams(
    val subscriptions: List<Subscription>,
    val paymentDay: Int,
    val holidayPolicy: String,
    val holidays: Set<LocalDate>,
    val paymentMethods: List<PaymentMethod>
)

class TransactionViewModel(application: Application) : AndroidViewModel(application) {

    val repository: TransactionRepository
    val allCategories: Flow<List<Category>>
    val allPaymentMethods: Flow<List<PaymentMethod>>
    val allTransactions: Flow<List<Transaction>>

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

    private val scheduleParams: Flow<ScheduleParams> by lazy {
        combine(
            subscriptions,
            combine(creditPaymentDay, creditHolidayPolicy, holidays) { day, policy, holidaySet -> Triple(day, policy, holidaySet) },
            allPaymentMethods
        ) { subs, triple, methods ->
            ScheduleParams(subs, triple.first, triple.second, triple.third, methods)
        }
    }

    init {
        val database = AppDatabase.getDatabase(application)
        repository = TransactionRepository(database.transactionDao(), database.categoryDao(), database.paymentMethodDao())
        allCategories = repository.allCategories
        allPaymentMethods = repository.allPaymentMethods
        allTransactions = repository.allTransactions

        viewModelScope.launch {
            if (repository.getCategoryCount() == 0) {
                listOf(
                    Category(name = "食費", isExpense = true),
                    Category(name = "日用品", isExpense = true),
                    Category(name = "交通費", isExpense = true),
                    Category(name = "趣味・娯楽", isExpense = true),
                    Category(name = "その他", isExpense = true),
                    Category(name = "給料", isExpense = false),
                    Category(name = "臨時収入", isExpense = false),
                    Category(name = "その他", isExpense = false)
                ).forEach { repository.insertCategory(it) }
            }
            if (repository.getPaymentMethodCount() == 0) {
                listOf(
                    PaymentMethod(name = PaymentNames.CASH, isDefault = true, type = PaymentTypes.CASH, manageBalance = true),
                    PaymentMethod(name = PaymentNames.CREDIT, isDefault = true, type = PaymentTypes.CREDIT, manageBalance = false, closingDay = 0, paymentDay = 27, paymentMonthOffset = 1, holidayPolicy = "backward")
                ).forEach { repository.insertPaymentMethod(it) }
            }
        }
        fetchHolidays()
        loadSubscriptions()
    }

    // ============================
    // 設定
    // ============================
    fun setAggregateCreditOnUsageDate(onUsage: Boolean) { prefs.edit().putBoolean(KEY_AGGREGATE_ON_USAGE, onUsage).apply(); _aggregateCreditOnUsageDate.value = onUsage }
    
    fun setCreditPaymentDay(day: Int) { 
        prefs.edit().putInt(KEY_PAYMENT_DAY, day).apply()
        _creditPaymentDay.value = day
        recalculateAllCreditPaymentDates()
    }
    
    fun setCreditHolidayPolicy(policy: String) { 
        prefs.edit().putString(KEY_HOLIDAY_POLICY, policy).apply()
        _creditHolidayPolicy.value = policy
        recalculateAllCreditPaymentDates()
    }

    // ============================
    // 祝日
    // ============================
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

    fun addSubscription(name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String, paymentMethod: String, category: String) {
        val nextId = (_subscriptions.value.maxOfOrNull { it.id } ?: 0) + 1
        saveSubscriptions(_subscriptions.value + Subscription(nextId, name, amount, isYearly, billingMonth, startYearMonth, null, paymentMethod, category))
    }

    fun updateSubscription(id: Int, name: String, amount: Int, isYearly: Boolean, billingMonth: Int, startYearMonth: String, paymentMethod: String, category: String) =
        saveSubscriptions(_subscriptions.value.map {
            if (it.id == id) it.copy(name = name, amount = amount, isYearly = isYearly, billingMonth = billingMonth, startYearMonth = startYearMonth, paymentMethod = paymentMethod, category = category) else it
        })

    fun terminateSubscription(id: Int, endYearMonth: String?) =
        saveSubscriptions(_subscriptions.value.map { if (it.id == id) it.copy(endYearMonth = endYearMonth) else it })

    fun deleteSubscription(id: Int) = saveSubscriptions(_subscriptions.value.filter { it.id != id })

    // ============================
    // 取引の取得
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

    private fun generateVirtualSubscriptionsForMonth(yearMonth: String, params: ScheduleParams): List<Transaction> {
        val targetYearMonth = YearMonth.parse(yearMonth, YEAR_MONTH_FORMAT)

        return params.subscriptions.mapNotNull { sub ->
            val startYM = YearMonth.parse(sub.startYearMonth, YEAR_MONTH_FORMAT)
            val endYM = sub.endYearMonth?.let { YearMonth.parse(it, YEAR_MONTH_FORMAT) }
            val isActive = !targetYearMonth.isBefore(startYM) && (endYM == null || !targetYearMonth.isAfter(endYM))
            val isBillingMonth = !sub.isYearly || sub.billingMonth == targetYearMonth.monthValue
            
            if (isActive && isBillingMonth) {
                // サブスクごとに設定された決済手段に基づいて支払日を計算
                val method = params.paymentMethods.find { it.name == sub.paymentMethod }
                val paymentDay = method?.paymentDay ?: params.paymentDay
                val holidayPolicy = method?.holidayPolicy ?: params.holidayPolicy
                
                val paymentDate = targetYearMonth.atDay(minOf(paymentDay, targetYearMonth.lengthOfMonth()))
                val finalPaymentDate = adjustForWeekendAndHoliday(paymentDate, holidayPolicy, params.holidays)

                Transaction(
                    id = VIRTUAL_ID_BASE - sub.id,
                    title = "${sub.name} (サブスク)",
                    amount = sub.amount,
                    isExpense = true,
                    category = sub.category,
                    date = finalPaymentDate.toString(),
                    isCreditPayment = true,
                    paymentMethod = sub.paymentMethod,
                    isCharge = false
                )
            } else null
        }
    }

    // ============================
    // 取引の追加・更新・削除
    // ============================
    fun addTransaction(title: String, amount: Int, isExpense: Boolean, category: String, paymentMethod: String, dateString: String, isCharge: Boolean, chargeSource: String? = null, memo: String = "") = viewModelScope.launch {
        val transaction = Transaction(title = title, amount = amount, isExpense = isExpense, category = category, date = dateString, isCreditPayment = false, paymentMethod = paymentMethod, isCharge = isCharge, chargeSource = chargeSource, memo = memo)
        val insertedId = repository.insert(transaction)

        val methods = repository.allPaymentMethods.first()
        val pMethod = methods.find { it.name == paymentMethod }
        val cSourceMethod = methods.find { it.name == chargeSource }

        val isMethodCreditType = pMethod?.type == PaymentTypes.CREDIT || paymentMethod == PaymentNames.CREDIT
        val isChargeSourceCreditType = cSourceMethod?.type == PaymentTypes.CREDIT || chargeSource == PaymentNames.CREDIT

        if (isExpense && isMethodCreditType && !isCharge) {
            val methodForCalc = pMethod ?: PaymentMethod(name = PaymentNames.CREDIT, type = PaymentTypes.CREDIT, paymentDay = _creditPaymentDay.value)
            createCreditPaymentData(insertedId.toInt(), title, amount, dateString, methodForCalc)
        }
        if (isCharge && isChargeSourceCreditType) {
            val methodForCalc = cSourceMethod ?: PaymentMethod(name = PaymentNames.CREDIT, type = PaymentTypes.CREDIT, paymentDay = _creditPaymentDay.value)
            createCreditPaymentData(insertedId.toInt(), "$title (チャージ分)", amount, dateString, methodForCalc)
        }
    }

    fun addCreditAdjustmentTransaction(targetTx: Transaction, actualAmount: Int) = viewModelScope.launch {
        val diff = actualAmount - targetTx.amount
        if (diff == 0) return@launch

        // 差額がマイナス（実際の請求額が安い）場合でも収入にはせず、
        // 支出合計を減額するために負の金額の支出(isExpense = true)として記録する。
        val title = if (diff < 0) "端数調整（クレジット割引）" else "端数調整（クレジット手数料等）"
        val transaction = Transaction(
            title = title,
            amount = diff, // diff < 0 なら負の金額で支出合計をマイナスする
            isExpense = true,
            category = "その他", // 調整用カテゴリ
            date = targetTx.date,
            isCreditPayment = false,
            paymentMethod = targetTx.paymentMethod,
            isCharge = false,
            memo = "計算上の金額: ${targetTx.amount}円, 実際の請求額: ${actualAmount}円"
        )
        repository.insert(transaction)
    }

    fun updateTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.update(transaction)
        val existingPaymentData = repository.getCreditPaymentTransaction(transaction.id)

        val methods = repository.allPaymentMethods.first()
        val pMethod = methods.find { it.name == transaction.paymentMethod }
        val cSourceMethod = methods.find { it.name == transaction.chargeSource }

        val isMethodCreditType = pMethod?.type == PaymentTypes.CREDIT || transaction.paymentMethod == PaymentNames.CREDIT
        val isChargeSourceCreditType = cSourceMethod?.type == PaymentTypes.CREDIT || transaction.chargeSource == PaymentNames.CREDIT

        val needsCreditPayment = (transaction.isExpense && isMethodCreditType && !transaction.isCharge) ||
                (transaction.isCharge && isChargeSourceCreditType)

        if (needsCreditPayment) {
            val calcMethod = if (transaction.isCharge) cSourceMethod else pMethod
            val methodForCalc = calcMethod ?: PaymentMethod(name = PaymentNames.CREDIT, type = PaymentTypes.CREDIT, paymentDay = _creditPaymentDay.value)
            val paymentDate = calculateCreditPaymentDateWithMethod(transaction.date, methodForCalc, _holidays.value)
            
            if (existingPaymentData != null) {
                repository.update(existingPaymentData.copy(title = "${transaction.title}（引き落とし）", amount = transaction.amount, date = paymentDate))
            } else {
                val paymentTitle = if (transaction.isCharge) "${transaction.title} (チャージ分)" else transaction.title
                createCreditPaymentData(transaction.id, paymentTitle, transaction.amount, transaction.date, methodForCalc)
            }
        } else {
            existingPaymentData?.let { repository.delete(it) }
        }
    }

    fun deleteTransaction(transaction: Transaction) = viewModelScope.launch {
        repository.getCreditPaymentTransaction(transaction.id)?.let { repository.delete(it) }
        repository.delete(transaction)
    }

    private suspend fun createCreditPaymentData(parentId: Int, title: String, amount: Int, dateString: String, method: PaymentMethod) {
        val paymentDate = calculateCreditPaymentDateWithMethod(dateString, method, _holidays.value)
        repository.insert(Transaction(title = "${title}（引き落とし）", amount = amount, isExpense = true, category = CategoryNames.CREDIT_PAYMENT, date = paymentDate, parentTransactionId = parentId, isCreditPayment = true, paymentMethod = PaymentNames.CASH, isCharge = false))
    }

    // ============================
    // クレジット一括再計算
    // ============================
    private fun recalculateAllCreditPaymentDates() = viewModelScope.launch {
        val allCreditPayments = repository.getAllCreditPaymentTransactions()
        val allTx = repository.allTransactions.first()
        val methods = repository.allPaymentMethods.first()

        val updatedList = mutableListOf<Transaction>()
        for (payment in allCreditPayments) {
            val parent = allTx.find { it.id == payment.parentTransactionId } ?: continue
            val calcMethodName = if (parent.isCharge) parent.chargeSource else parent.paymentMethod
            val method = methods.find { it.name == calcMethodName } ?: PaymentMethod(name = PaymentNames.CREDIT, type = PaymentTypes.CREDIT, paymentDay = _creditPaymentDay.value)
            val newDate = calculateCreditPaymentDateWithMethod(parent.date, method, _holidays.value)
            if (payment.date != newDate) {
                updatedList.add(payment.copy(date = newDate))
            }
        }
        if (updatedList.isNotEmpty()) {
            repository.updateTransactions(updatedList)
        }
    }

    // ============================
    // カテゴリー・支払方法
    // ============================
    fun addCategory(name: String, isExpense: Boolean = true) = viewModelScope.launch { repository.insertCategory(Category(name = name, isExpense = isExpense)) }
    suspend fun checkCategoryUsage(category: Category): Int = repository.getTransactionCountByCategory(category.name)
    fun deleteCategory(category: Category) = viewModelScope.launch { repository.deleteCategory(category) }

    fun addPaymentMethod(method: PaymentMethod) = viewModelScope.launch { repository.insertPaymentMethod(method) }
    suspend fun checkPaymentMethodUsage(method: PaymentMethod): Int = repository.getTransactionCountByPaymentMethod(method.name)
    fun deletePaymentMethod(method: PaymentMethod) = viewModelScope.launch { if (!method.isDefault) repository.deletePaymentMethod(method) }
    
    fun updatePaymentMethod(method: PaymentMethod) = viewModelScope.launch { 
        repository.updatePaymentMethod(method)
        if (method.type == PaymentTypes.CREDIT) {
            recalculateAllCreditPaymentDates()
        }
    }

    // ============================
    // バックアップと復元
    // ============================
    suspend fun createBackupJson(): String = withContext(Dispatchers.IO) {
        val root = JSONObject()

        val txList = repository.getAllTransactionsSync()
        val txArray = JSONArray()
        txList.forEach { txArray.put(it.toJson()) }
        root.put("transactions", txArray)

        val catList = repository.getAllCategoriesSync()
        val catArray = JSONArray()
        catList.forEach { catArray.put(it.toJson()) }
        root.put("categories", catArray)

        val methodList = repository.getAllPaymentMethodsSync()
        val methodArray = JSONArray()
        methodList.forEach { methodArray.put(it.toJson()) }
        root.put("paymentMethods", methodArray)

        root.put("subscriptions", JSONArray(prefs.getString(KEY_SUBSCRIPTIONS_JSON, "[]")))

        root.put("aggregateCreditOnUsage", _aggregateCreditOnUsageDate.value)
        root.put("creditPaymentDay", _creditPaymentDay.value)
        root.put("creditHolidayPolicy", _creditHolidayPolicy.value)

        root.toString(2)
    }

    suspend fun restoreFromBackupJson(jsonString: String) = withContext(Dispatchers.IO) {
        val root = JSONObject(jsonString)

        val txArray = root.getJSONArray("transactions")
        val txList = (0 until txArray.length()).map { Transaction.fromJson(txArray.getJSONObject(it)) }

        val catArray = root.getJSONArray("categories")
        val catList = (0 until catArray.length()).map { Category.fromJson(catArray.getJSONObject(it)) }

        val methodArray = root.getJSONArray("paymentMethods")
        val methodList = (0 until methodArray.length()).map { PaymentMethod.fromJson(methodArray.getJSONObject(it)) }

        repository.restoreDatabase(txList, catList, methodList)

        if (root.has("subscriptions")) {
            prefs.edit().putString(KEY_SUBSCRIPTIONS_JSON, root.getJSONArray("subscriptions").toString()).apply()
        }
        
        if (root.has("aggregateCreditOnUsage")) {
            val v = root.getBoolean("aggregateCreditOnUsage")
            prefs.edit().putBoolean(KEY_AGGREGATE_ON_USAGE, v).apply()
            _aggregateCreditOnUsageDate.value = v
        }
        if (root.has("creditPaymentDay")) {
            val v = root.getInt("creditPaymentDay")
            prefs.edit().putInt(KEY_PAYMENT_DAY, v).apply()
            _creditPaymentDay.value = v
        }
        if (root.has("creditHolidayPolicy")) {
            val v = root.getString("creditHolidayPolicy")
            prefs.edit().putString(KEY_HOLIDAY_POLICY, v).apply()
            _creditHolidayPolicy.value = v
        }

        // リストア後にStateFlowを更新するため
        withContext(Dispatchers.Main) {
            loadSubscriptions()
        }
    }
}
