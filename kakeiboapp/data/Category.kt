package com.example.kakeiboapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isDefault: Boolean = false, // 「クレジット」など消されては困るデフォルトカテゴリーの判定用
    val isVisible: Boolean = true
)