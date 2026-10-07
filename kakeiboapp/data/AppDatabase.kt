package com.example.kakeiboapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

// ★バージョンを「4」に変更
@Database(entities = [Transaction::class, Category::class], version = 4, exportSchema = false)
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
                    .fallbackToDestructiveMigration() // バージョン4になり、過去のデータがリセットされます
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}