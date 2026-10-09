package com.example.kakeiboapp

import com.example.kakeiboapp.data.Transaction
import java.io.OutputStreamWriter
import java.io.OutputStream

object CsvExporter {
    fun exportToCsv(transactions: List<Transaction>, outputStream: OutputStream) {
        val writer = OutputStreamWriter(outputStream, "UTF-8")
        // UTF-8 BOM
        writer.write("\uFEFF")
        
        // ヘッダー
        writer.write("ID,日付,内容,金額,収支種別,カテゴリー,支払方法,チャージ元\n")
        
        // データ行
        for (tx in transactions) {
            val id = tx.id
            val date = tx.date
            val title = escapeCsv(tx.title)
            val amount = tx.amount
            
            val typeStr = when {
                tx.isCharge -> "チャージ"
                tx.isExpense -> "支出"
                else -> "収入"
            }
            
            val category = escapeCsv(tx.category)
            val paymentMethod = escapeCsv(tx.paymentMethod)
            val chargeSource = escapeCsv(tx.chargeSource ?: "")
            
            writer.write("$id,$date,$title,$amount,$typeStr,$category,$paymentMethod,$chargeSource\n")
        }
        
        writer.flush()
        writer.close()
    }

    private fun escapeCsv(value: String): String {
        if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            return "\"" + value.replace("\"", "\"\"") + "\""
        }
        return value
    }
}
