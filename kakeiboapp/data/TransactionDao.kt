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

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(transaction: Transaction): Long

    @Update
    suspend fun update(transaction: Transaction)

    @Delete
    suspend fun delete(transaction: Transaction)
}