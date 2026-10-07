package com.example.kakeiboapp.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    // 新規登録 (追加したデータのIDが返ってきます)
    @Insert
    suspend fun insert(transaction: Transaction): Long

    // 更新
    @Update
    suspend fun update(transaction: Transaction)

    // 削除
    @Delete
    suspend fun delete(transaction: Transaction)

    // カレンダー表示用: 指定した月（例 "2026-10%"）のデータを取得
    // Flowを使うことで、データが更新されるとUI(カレンダー)も自動で再描画されます
    @Query("SELECT * FROM transactions WHERE date LIKE :yearMonth || '%' ORDER BY date ASC")
    fun getTransactionsByMonth(yearMonth: String): Flow<List<Transaction>>

    // クレジットカード連動処理用: 元のデータIDから、生成された支払日データを取得
    @Query("SELECT * FROM transactions WHERE parentTransactionId = :parentId")
    suspend fun getCreditPaymentTransaction(parentId: Int): Transaction?

    // 年間収支用: 指定した年（例 "2026%"）のデータを取得
    @Query("SELECT * FROM transactions WHERE date LIKE :year || '%' ORDER BY date ASC")
    fun getTransactionsByYear(year: String): Flow<List<Transaction>>
}