package com.example.kakeiboapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "payment_methods")
data class PaymentMethod(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isDefault: Boolean = false, // 「現金」「クレジット」「銀行口座」は削除不可
    val type: String = PaymentTypes.OTHER, // 決済種別
    val manageBalance: Boolean = true, // 残高管理の対象かどうか
    val closingDay: Int = 0, // 締め日 (0=月末, 1~31)
    val paymentDay: Int = 27, // 引き落とし日 (1~31)
    val paymentMonthOffset: Int = 1, // 1=翌月, 2=翌々月
    val holidayPolicy: String = "backward" // 休日の対応: forward or backward
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("isDefault", isDefault)
        put("type", type)
        put("manageBalance", manageBalance)
        put("closingDay", closingDay)
        put("paymentDay", paymentDay)
        put("paymentMonthOffset", paymentMonthOffset)
        put("holidayPolicy", holidayPolicy)
    }

    companion object {
        fun fromJson(obj: org.json.JSONObject): PaymentMethod = PaymentMethod(
            id = obj.getInt("id"),
            name = obj.getString("name"),
            isDefault = obj.optBoolean("isDefault", false),
            type = obj.optString("type", PaymentTypes.OTHER),
            manageBalance = obj.optBoolean("manageBalance", true),
            closingDay = obj.optInt("closingDay", 0),
            paymentDay = obj.optInt("paymentDay", 27),
            paymentMonthOffset = obj.optInt("paymentMonthOffset", 1),
            holidayPolicy = obj.optString("holidayPolicy", "backward")
        )
    }
}