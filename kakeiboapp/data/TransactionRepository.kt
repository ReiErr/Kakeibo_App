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
    val allTransactions: Flow<List<Transaction>> = transactionDao.getAllTransactions() // 全期間の取引（残高計算用）

    suspend fun getCreditPaymentTransaction(parentId: Int) = transactionDao.getCreditPaymentTransaction(parentId)
    suspend fun getAllCreditPaymentTransactions() = transactionDao.getAllCreditPaymentTransactions()

    suspend fun getTransactionCountByCategory(categoryName: String) = transactionDao.getTransactionCountByCategory(categoryName)
    suspend fun getTransactionCountByPaymentMethod(methodName: String) = transactionDao.getTransactionCountByPaymentMethod(methodName)

    suspend fun insert(transaction: Transaction) = transactionDao.insert(transaction)
    suspend fun update(transaction: Transaction) = transactionDao.update(transaction)
    suspend fun updateTransactions(transactions: List<Transaction>) = transactionDao.updateTransactions(transactions)
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
    suspend fun updatePaymentMethod(paymentMethod: PaymentMethod) = paymentMethodDao.update(paymentMethod)
    suspend fun deletePaymentMethod(paymentMethod: PaymentMethod) = paymentMethodDao.delete(paymentMethod)

    // Backup & Restore
    suspend fun getAllTransactionsSync() = transactionDao.getAllTransactionsSync()
    suspend fun getAllCategoriesSync() = categoryDao.getAllCategoriesSync()
    suspend fun getAllPaymentMethodsSync() = paymentMethodDao.getAllPaymentMethodsSync()

    suspend fun restoreDatabase(transactions: List<Transaction>, categories: List<Category>, paymentMethods: List<PaymentMethod>) {
        transactionDao.deleteAllTransactions()
        categoryDao.deleteAllCategories()
        paymentMethodDao.deleteAllPaymentMethods()

        if (categories.isNotEmpty()) categoryDao.insertAll(categories)
        if (paymentMethods.isNotEmpty()) paymentMethodDao.insertAll(paymentMethods)
        if (transactions.isNotEmpty()) transactionDao.insertAll(transactions)
    }
}