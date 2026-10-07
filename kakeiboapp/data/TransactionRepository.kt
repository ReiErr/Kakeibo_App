package com.example.kakeiboapp.data

import kotlinx.coroutines.flow.Flow

class TransactionRepository(
    private val transactionDao: TransactionDao,
    private val categoryDao: CategoryDao,
    private val paymentMethodDao: PaymentMethodDao
) {
    // Transaction
    fun getTransactionsByMonth(yearMonth: String) = transactionDao.getTransactionsByMonth(yearMonth)
    fun getTransactionsByYear(year: String) = transactionDao.getTransactionsByYear(year)
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions() // ★追加

    suspend fun getCreditPaymentTransaction(parentId: Int) = transactionDao.getCreditPaymentTransaction(parentId)
    suspend fun insert(transaction: Transaction) = transactionDao.insert(transaction)
    suspend fun update(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun delete(transaction: Transaction) = transactionDao.delete(transaction)

    // Category
    val allCategories: Flow<List<Category>> = categoryDao.getAllCategories()
    suspend fun getCategoryCount() = categoryDao.getCategoryCount()
    suspend fun insertCategory(category: Category) = categoryDao.insert(category)
    suspend fun updateCategory(category: Category) = categoryDao.update(category)
    suspend fun deleteCategory(category: Category) = categoryDao.delete(category)

    // PaymentMethod
    val allPaymentMethods: Flow<List<PaymentMethod>> = paymentMethodDao.getAllPaymentMethods()
    suspend fun getPaymentMethodCount() = paymentMethodDao.getCount()
    suspend fun insertPaymentMethod(paymentMethod: PaymentMethod) = paymentMethodDao.insert(paymentMethod)
    suspend fun deletePaymentMethod(paymentMethod: PaymentMethod) = paymentMethodDao.delete(paymentMethod)
}