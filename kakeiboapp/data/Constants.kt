package com.example.kakeiboapp.data

/** 支払方法の名前。「現金」「クレジット」は初回起動時に登録される基本の支払方法。 */
object PaymentNames {
    const val CASH = "現金"
    const val CREDIT = "クレジット"

    /** 残高修正のときに、差額の相手側として使う仮の名前 */
    const val SYSTEM_ADJUSTMENT = "システム調整"
}

/** アプリ側で自動的に付けるカテゴリー名 */
object CategoryNames {
    const val CHARGE = "チャージ"
    const val CREDIT_PAYMENT = "口座引落"
    const val BALANCE_ADJUSTMENT = "残高調整"
}

/**
 * 残高を管理する支払方法かどうか。
 * 「現金」「クレジット」は残高管理の対象外（false）で、電子マネーなどは対象（true）。
 */
fun String.isBalanceManaged(): Boolean = this != PaymentNames.CASH && this != PaymentNames.CREDIT
