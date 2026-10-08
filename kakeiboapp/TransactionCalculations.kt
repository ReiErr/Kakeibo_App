package com.example.kakeiboapp

import com.example.kakeiboapp.data.PaymentNames
import com.example.kakeiboapp.data.Transaction
import com.example.kakeiboapp.data.isBalanceManaged

/** サブスクから自動生成した、DBに保存されない仮の取引か（IDが負の数） */
val Transaction.isVirtual: Boolean get() = id < 0

/** "yyyy-MM" 形式の年月文字列を作る */
fun yearMonthString(year: Int, month: Int): String = String.format("%04d-%02d", year, month)

/**
 * 支払方法の現在の残高を、全期間の取引から計算する。
 * 残高を管理しない支払方法（現金・クレジット）は上限なしとして [Int.MAX_VALUE] を返す。
 */
fun calculateBalance(methodName: String, transactions: List<Transaction>): Int {
    if (!methodName.isBalanceManaged()) return Int.MAX_VALUE
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
fun List<Transaction>.calculateTotalExpense(aggregateOnUsage: Boolean): Int {
    return this.filter { it.isExpense && !it.isCharge }.filter {
        if (it.isVirtual) {
            true
        } else if (aggregateOnUsage) {
            !it.isCreditPayment
        } else {
            !(it.paymentMethod == PaymentNames.CREDIT && !it.isCreditPayment)
        }
    }.sumOf { it.amount }
}
