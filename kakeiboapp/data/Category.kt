package com.example.kakeiboapp.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "categories")
data class Category(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val name: String,
    val isExpense: Boolean = true // true: 支出用, false: 収入用
) {
    fun toJson(): org.json.JSONObject = org.json.JSONObject().apply {
        put("id", id)
        put("name", name)
        put("isExpense", isExpense)
    }

    companion object {
        fun fromJson(obj: org.json.JSONObject): Category = Category(
            id = obj.getInt("id"),
            name = obj.getString("name"),
            isExpense = obj.optBoolean("isExpense", true)
        )
    }
}