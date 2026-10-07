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
    val paymentMethod: String = "現金",
    val isCharge: Boolean = false,
    val chargeSource: String? = null // ★追加: どこからチャージしたか（現金 or クレジット）を保存
)