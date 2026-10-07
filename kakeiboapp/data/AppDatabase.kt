package com.example.kakeiboapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

// ★バージョンを3に変更します
@Database(entities = [Transaction::class, Category::class], version = 3, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {

    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kakeibo_database"
                )
                    .fallbackToDestructiveMigration()
                    .addCallback(DatabaseCallback())
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }

    private class DatabaseCallback : RoomDatabase.Callback() {
        override fun onCreate(db: SupportSQLiteDatabase) {
            super.onCreate(db)
            INSTANCE?.let { database ->
                CoroutineScope(Dispatchers.IO).launch {
                    val dao = database.categoryDao()
                    val defaultCategories = listOf(
                        Category(name = "食費"),      // isDefaultを指定しない（=false）ので削除可能
                        Category(name = "日用品"),
                        Category(name = "交通費"),
                        Category(name = "クレジット", isDefault = true), // ★これだけシステム保護（削除不可）にする
                        Category(name = "給料"),
                        Category(name = "その他")
                    )
                    defaultCategories.forEach { dao.insert(it) }
                }
            }
        }
    }
}