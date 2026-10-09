package com.example.kakeiboapp.data

/** 支払方法の名前。「現金」「クレジット」等は初回起動時に登録される基本の支払方法。 */
object PaymentNames {
    const val CASH = "現金"
    const val CREDIT = "クレジット"
    const val NONE = "指定なし"

    /** 残高修正のときに、差額の相手側として使う仮の名前 */
    const val SYSTEM_ADJUSTMENT = "システム調整"
}

/** 決済種別 */
object PaymentTypes {
    const val CASH = "CASH"
    const val CREDIT = "CREDIT"
    const val ELECTRONIC_MONEY = "ELECTRONIC_MONEY"
    const val OTHER = "OTHER"
}

/** アプリ側で自動的に付けるカテゴリー名 */
object CategoryNames {
    const val CHARGE = "チャージ"
    const val CREDIT_PAYMENT = "口座引落"
    const val BALANCE_ADJUSTMENT = "残高調整"
}

/**
 * 残高を管理する支払方法かどうか。
 * PaymentMethod リストから自身の manageBalance 設定を参照します。
 * システム調整は常に管理外(false)です。
 */
fun String.isBalanceManaged(methods: List<PaymentMethod>): Boolean {
    if (this == PaymentNames.SYSTEM_ADJUSTMENT) return false
    val method = methods.find { it.name == this }
    return method?.manageBalance ?: false
}
