package com.example.kakeiboapp

import com.example.kakeiboapp.data.PaymentMethod
import com.example.kakeiboapp.data.PaymentNames
import com.example.kakeiboapp.data.PaymentTypes
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.isBalanceManaged
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter

/** サブスクから自動生成した、DBに保存されない仮の取引か（IDが負の数） */
val Transaction.isVirtual: Boolean get() = id < 0

/** "yyyy-MM" 形式の年月文字列を作る */
fun yearMonthString(year: Int, month: Int): String = String.format("%04d-%02d", year, month)

/**
 * 支払方法の現在の残高を、全期間の取引から計算する。
 * 残高を管理しない支払方法は上限なしとして [Int.MAX_VALUE] を返す。
 */
fun calculateBalance(methodName: String, transactions: List<Transaction>, methods: List<PaymentMethod>): Int {
    if (!methodName.isBalanceManaged(methods)) return Int.MAX_VALUE
    val chargeIn = transactions.filter { it.isCharge && it.paymentMethod == methodName }.sumOf { it.amount }
    val chargeOut = transactions.filter { it.isCharge && it.chargeSource == methodName }.sumOf { it.amount }
    val incomeIn = transactions.filter { !it.isExpense && !it.isCharge && it.paymentMethod == methodName }.sumOf { it.amount }
    val expenseOut = transactions.filter { it.isExpense && it.paymentMethod == methodName && !it.isCharge }.sumOf { it.amount }
    return chargeIn + incomeIn - chargeOut - expenseOut
}

/** 収入の合計（チャージは含めない） */
fun List<Transaction>.calculateTotalIncome(): Int =
    this.filter { !it.isExpense && !it.isCharge }.sumOf { it.amount }

/**
 * 支出の合計（チャージは含めない）。
 * クレジット払いはDB上に「利用日」と「引き落とし日」の2件が存在するため、
 * 設定に応じて片方だけを数えて二重計上を防ぐ。サブスクの仮データは常に数える。
 */
fun List<Transaction>.calculateTotalExpense(aggregateOnUsage: Boolean, paymentMethods: List<PaymentMethod>): Int {
    return this.filter { it.isExpense && !it.isCharge }.filter { tx ->
        if (tx.isVirtual) {
            true
        } else if (aggregateOnUsage) {
            !tx.isCreditPayment
        } else {
            val method = paymentMethods.find { m -> m.name == tx.paymentMethod }
            val isCreditType = method?.type == PaymentTypes.CREDIT || tx.paymentMethod == PaymentNames.CREDIT
            !(isCreditType && !tx.isCreditPayment)
        }
    }.sumOf { it.amount }
}

/**
 * クレジットカードの締め日・支払月に基づいて引き落とし日を計算する
 * 締め日が0の場合は月末締め。
 * 休日対応ポリシーは method.holidayPolicy から取得します。
 */
fun calculateCreditPaymentDateWithMethod(
    usageDateString: String,
    method: PaymentMethod,
    holidays: Set<LocalDate>
): String {
    val usageDate = LocalDate.parse(usageDateString, DateTimeFormatter.ofPattern("yyyy-MM-dd"))
    
    // 締め日判定
    val closingDay = if (method.closingDay == 0) {
        usageDate.lengthOfMonth()
    } else {
        method.closingDay
    }

    // 利用日が締め日を過ぎている場合は、さらに翌月扱いになる
    val isAfterClosing = usageDate.dayOfMonth > closingDay
    
    // 基準月 (利用月 + オフセット)
    var targetMonthsToAdd = method.paymentMonthOffset.toLong()
    if (isAfterClosing) {
        targetMonthsToAdd += 1
    }
    
    val targetMonth = YearMonth.from(usageDate.plusMonths(targetMonthsToAdd))
    val paymentDay = minOf(method.paymentDay, targetMonth.lengthOfMonth())
    val paymentDate = targetMonth.atDay(paymentDay)
    
    return adjustForWeekendAndHoliday(paymentDate, method.holidayPolicy, holidays).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"))
}

fun adjustForWeekendAndHoliday(date: LocalDate, policy: String, holidaySet: Set<LocalDate>): LocalDate {
    var adjustedDate = date
    fun isHolidayOrWeekend(d: LocalDate) = d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY || holidaySet.contains(d)
    while (isHolidayOrWeekend(adjustedDate)) {
        adjustedDate = if (policy == "forward") adjustedDate.minusDays(1) else adjustedDate.plusDays(1)
    }
    return adjustedDate
}
