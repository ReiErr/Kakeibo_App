package com.example.kakeiboapp.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Transaction::class, Category::class, PaymentMethod::class], version = 10, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun categoryDao(): CategoryDao
    abstract fun paymentMethodDao(): PaymentMethodDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN type TEXT NOT NULL DEFAULT 'OTHER'")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN manageBalance INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN closingDay INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN paymentDay INTEGER NOT NULL DEFAULT 27")
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN paymentMonthOffset INTEGER NOT NULL DEFAULT 1")
                
                // 現金はキャッシュタイプとして設定し、残高管理を有効化
                db.execSQL("UPDATE payment_methods SET type = '${PaymentTypes.CASH}', manageBalance = 1 WHERE name = '${PaymentNames.CASH}'")
                // クレジットはクレジットタイプとして設定し、残高管理を無効化
                db.execSQL("UPDATE payment_methods SET type = '${PaymentTypes.CREDIT}', manageBalance = 0 WHERE name = '${PaymentNames.CREDIT}'")
            }
        }
        
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // holidayPolicyカラムの追加
                db.execSQL("ALTER TABLE payment_methods ADD COLUMN holidayPolicy TEXT NOT NULL DEFAULT 'backward'")
                // 既存ユーザー向けに「銀行口座」をデフォルト決済手段として追加
                db.execSQL("INSERT OR IGNORE INTO payment_methods (name, isDefault, type, manageBalance, closingDay, paymentDay, paymentMonthOffset, holidayPolicy) VALUES ('銀行口座', 1, 'BANK', 1, 0, 27, 1, 'backward')")
            }
        }

        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // memoカラムの追加
                db.execSQL("ALTER TABLE transactions ADD COLUMN memo TEXT NOT NULL DEFAULT ''")
                // 以前のバージョンで追加された銀行口座の削除（存在する場合）
                db.execSQL("DELETE FROM payment_methods WHERE name = '銀行口座' OR type = 'BANK'")
            }
        }

        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // categoriesテーブルにisExpenseカラムを追加（デフォルトは支出: 1）
                db.execSQL("ALTER TABLE categories ADD COLUMN isExpense INTEGER NOT NULL DEFAULT 1")
                // 既存の「給料」があれば収入(0)に更新
                db.execSQL("UPDATE categories SET isExpense = 0 WHERE name = '給料'")
                // 収入用カテゴリーの補完
                db.execSQL("INSERT OR IGNORE INTO categories (name, isExpense) VALUES ('臨時収入', 0)")
            }
        }

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "kakeibo_database"
                )
                    .addMigrations(MIGRATION_6_7, MIGRATION_7_8, MIGRATION_8_9, MIGRATION_9_10)
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}