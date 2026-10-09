package com.example.kakeiboapp.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions WHERE date LIKE :yearMonth || '%' ORDER BY date ASC, id ASC")
    fun getTransactionsByMonth(yearMonth: String): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE date LIKE :year || '%' ORDER BY date ASC, id ASC")
    fun getTransactionsByYear(year: String): Flow<List<Transaction>>

    // 残高計算のために全期間の取引を取得する
    @Query("SELECT * FROM transactions")
    fun getAllTransactions(): Flow<List<Transaction>>

    @Query("SELECT * FROM transactions WHERE parentTransactionId = :parentId LIMIT 1")
    suspend fun getCreditPaymentTransaction(parentId: Int): Transaction?

    // クレジットの未引き落としデータを再計算するために取得する（未来分や全て）
    @Query("SELECT * FROM transactions WHERE isCreditPayment = 1")
    suspend fun getAllCreditPaymentTransactions(): List<Transaction>

    // 削除時の警告用：カテゴリーの使用件数を取得
    @Query("SELECT COUNT(*) FROM transactions WHERE category = :categoryName")
    suspend fun getTransactionCountByCategory(categoryName: String): Int

    // 削除時の警告用：支払方法の使用件数を取得
    @Query("SELECT COUNT(*) FROM transactions WHERE paymentMethod = :methodName OR chargeSource = :methodName")
    suspend fun getTransactionCountByPaymentMethod(methodName: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Update
    suspend fun updateTransactions(transactions: List<Transaction>)

    @Delete
    suspend fun delete(transaction: Transaction)

    @Query("SELECT * FROM transactions")
    suspend fun getAllTransactionsSync(): List<Transaction>

    @Query("DELETE FROM transactions")
    suspend fun deleteAllTransactions()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<Transaction>)
}