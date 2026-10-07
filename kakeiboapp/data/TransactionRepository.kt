package com.example.kakeiboapp.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao
) {
    val allTransactions: Flow<List<Transaction>> = transactionDao.getTransactionsByMonth("")

    suspend fun insert(transaction: Transaction): Long {
        return transactionDao.insert(transaction)
    }

    suspend fun update(transaction: Transaction) {
        transactionDao.update(transaction)
    }

    suspend fun delete(transaction: Transaction) {
        transactionDao.delete(transaction)
    }

    fun getTransactionsByMonth(yearMonth: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByMonth(yearMonth)
    }

    suspend fun getCreditPaymentTransaction(parentId: Int): Transaction? {
        return transactionDao.getCreditPaymentTransaction(parentId)
    }

    fun getTransactionsByYear(year: String): Flow<List<Transaction>> {
        return transactionDao.getTransactionsByYear(year)
    }

    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()

    suspend fun insertCategory(category: Category) {
        categoryDao.insert(category)
    }

    // ★これを追加
    suspend fun updateCategory(category: Category) {
        categoryDao.update(category)
    }

    suspend fun deleteCategory(category: Category) {
        categoryDao.delete(category)
    }

    suspend fun getCategoryCount(): Int {
        return categoryDao.getCategoryCount()
    }
}