package com.example.kakeiboapp

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.YearMonth

@Composable
fun YearMonthPickerDialog(initialYearMonth: YearMonth, onDismissRequest: () -> Unit, onYearMonthSelected: (YearMonth) -> Unit) {
    var selectedYear by remember { mutableStateOf(initialYearMonth.year) }
    var selectedMonth by remember { mutableStateOf(initialYearMonth.monthValue) }
    val years = (2000..2050).toList()
    val months = (1..12).toList()
    val yearListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, years.indexOf(initialYearMonth.year) - 2))
    val monthListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, months.indexOf(initialYearMonth.monthValue) - 2))
    AlertDialog(onDismissRequest = onDismissRequest, title = { Text("年月を選択", fontWeight = FontWeight.Bold) }, text = {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            LazyColumn(state = yearListState, modifier = Modifier.weight(1f).height(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(years) { year -> val isSelected = year == selectedYear; Text(text = "${year}年", fontSize = if (isSelected) 22.sp else 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.Blue else Color.Gray, modifier = Modifier.fillMaxWidth().clickable { selectedYear = year }.padding(vertical = 12.dp), textAlign = TextAlign.Center) }
            }
            LazyColumn(state = monthListState, modifier = Modifier.weight(1f).height(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(months) { month -> val isSelected = month == selectedMonth; Text(text = "${month}月", fontSize = if (isSelected) 22.sp else 16.sp, fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, color = if (isSelected) Color.Blue else Color.Gray, modifier = Modifier.fillMaxWidth().clickable { selectedMonth = month }.padding(vertical = 12.dp), textAlign = TextAlign.Center) }
            }
        }
    }, confirmButton = { TextButton(onClick = { onYearMonthSelected(YearMonth.of(selectedYear, selectedMonth)) }) { Text("決定") } }, dismissButton = { TextButton(onClick = onDismissRequest) { Text("キャンセル") } })
}

// 年間収支画面用の「年」選択ダイアログ
@Composable
fun YearPickerDialog(initialYear: Int, onDismissRequest: () -> Unit, onYearSelected: (Int) -> Unit) {
    var selectedYear by remember { mutableStateOf(initialYear) }
    val years = (2000..2050).toList()
    val yearListState = androidx.compose.foundation.lazy.rememberLazyListState(initialFirstVisibleItemIndex = maxOf(0, years.indexOf(initialYear) - 2))

    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text("年を選択", fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn(state = yearListState, modifier = Modifier.fillMaxWidth().height(200.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                items(years) { year ->
                    val isSelected = year == selectedYear
                    Text(
                        text = "${year}年",
                        fontSize = if (isSelected) 22.sp else 16.sp,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = if (isSelected) Color.Blue else Color.Gray,
                        modifier = Modifier.fillMaxWidth().clickable { selectedYear = year }.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = { onYearSelected(selectedYear) }) { Text("決定") } },
        dismissButton = { TextButton(onClick = onDismissRequest) { Text("キャンセル") } }
    )
}
