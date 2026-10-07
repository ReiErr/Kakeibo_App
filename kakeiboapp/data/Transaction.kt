package com.example.kakeiboapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "transactions")
data class Transaction(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val title: String,          // 例: "スーパーでの買い物"
    val amount: Int,            // 例: 5000
    val isExpense: Boolean,     // trueなら支出、falseなら収入
    val category: String,       // 例: "食費", "クレジット"
    val date: String,           // 例: "2026-10-04"

    // クレジットカード連動用
    val parentTransactionId: Int? = null, // 自動生成された支払日データの場合、元の利用データIDを入れる
    val isCreditPayment: Boolean = false  // クレジットの引き落としデータかどうか
)