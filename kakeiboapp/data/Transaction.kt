package com.example.kakeiboapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,
    val amount: Int,
    val isExpense: Boolean,
    val category: String,
    val date: String,
    val parentTransactionId: Int? = null,
    val isCreditPayment: Boolean = false,
    val paymentMethod: String = PaymentNames.CASH,
    val isCharge: Boolean = false,
    val chargeSource: String? = null, // チャージ元の支払方法名（チャージ以外は null）
    val memo: String = "" // 具体的な詳細などを残すメモ
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("title", title)
        put("amount", amount)
        put("isExpense", isExpense)
        put("category", category)
        put("date", date)
        if (parentTransactionId != null) put("parentTransactionId", parentTransactionId)
        put("isCreditPayment", isCreditPayment)
        put("paymentMethod", paymentMethod)
        put("isCharge", isCharge)
        if (chargeSource != null) put("chargeSource", chargeSource)
        put("memo", memo)
    }

    companion object {
        fun fromJson(obj: org.json.JSONObject): Transaction = Transaction(
            id = obj.getInt("id"),
            title = obj.getString("title"),
            amount = obj.getInt("amount"),
            isExpense = obj.getBoolean("isExpense"),
            category = obj.getString("category"),
            date = obj.getString("date"),
            parentTransactionId = if (obj.has("parentTransactionId") && !obj.isNull("parentTransactionId")) obj.getInt("parentTransactionId") else null,
            isCreditPayment = obj.optBoolean("isCreditPayment", false),
            paymentMethod = obj.optString("paymentMethod", PaymentNames.CASH),
            isCharge = obj.optBoolean("isCharge", false),
            chargeSource = if (obj.has("chargeSource") && !obj.isNull("chargeSource")) obj.getString("chargeSource") else null,
            memo = obj.optString("memo", "")
        )
    }
}