package com.example.kakeiboapp

import org.json.JSONObject

/**
 * サブスクリプション（月払い／年払い）の登録情報。
 * DBではなく SharedPreferences に JSON で保存する。
 *
 * @param billingMonth 年払いの支払い月（月払いのときは 1 固定）
 * @param startYearMonth 開始年月（"yyyy-MM"）
 * @param endYearMonth 終了年月（"yyyy-MM"）。null の間は無期限
 */
data class Subscription(
    val id: Int,
    val name: String,
    val amount: Int,
    val isYearly: Boolean,
    val billingMonth: Int,
    val startYearMonth: String,
    val endYearMonth: String? = null
) {
    fun toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("name", name)
        put("amount", amount)
        put("isYearly", isYearly)
        put("billingMonth", billingMonth)
        put("startYearMonth", startYearMonth)
        if (endYearMonth != null) put("endYearMonth", endYearMonth)
    }

    companion object {
        fun fromJson(obj: JSONObject): Subscription = Subscription(
            id = obj.getInt("id"),
            name = obj.getString("name"),
            amount = obj.getInt("amount"),
            isYearly = obj.getBoolean("isYearly"),
            billingMonth = obj.getInt("billingMonth"),
            startYearMonth = obj.optString("startYearMonth", "2000-01"),
            endYearMonth = if (obj.has("endYearMonth") && !obj.isNull("endYearMonth")) obj.getString("endYearMonth") else null
        )
    }
}
