package com.example.kakeiboapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.kakeiboapp.ui.theme.KakeiboAppTheme

enum class ScreenType { Calendar, Analysis, YearlySummary, Settings }

class MainActivity : ComponentActivity() {
    private val viewModel: TransactionViewModel by viewModels()
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { KakeiboAppTheme { MainAppScreen(viewModel = viewModel) } }
    }
}

@Composable
fun MainAppScreen(viewModel: TransactionViewModel) {
    var currentScreen by remember { mutableStateOf(ScreenType.Calendar) }
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                NavigationBarItem(icon = { Icon(Icons.Filled.DateRange, "カレンダー") }, label = { Text("カレンダー") }, selected = currentScreen == ScreenType.Calendar, onClick = { currentScreen = ScreenType.Calendar })
                NavigationBarItem(icon = { Icon(Icons.Filled.PieChart, "分析") }, label = { Text("分析") }, selected = currentScreen == ScreenType.Analysis, onClick = { currentScreen = ScreenType.Analysis })
                NavigationBarItem(icon = { Icon(Icons.Filled.Assessment, "収支") }, label = { Text("収支") }, selected = currentScreen == ScreenType.YearlySummary, onClick = { currentScreen = ScreenType.YearlySummary })
                NavigationBarItem(icon = { Icon(Icons.Filled.Settings, "設定") }, label = { Text("設定") }, selected = currentScreen == ScreenType.Settings, onClick = { currentScreen = ScreenType.Settings })
            }
        }
    ) { innerPadding ->
        Box(modifier = Modifier.padding(innerPadding)) {
            when (currentScreen) {
                ScreenType.Calendar -> CalendarScreen(viewModel = viewModel)
                ScreenType.Analysis -> AnalysisScreen(viewModel = viewModel)
                ScreenType.YearlySummary -> YearlySummaryScreen(viewModel = viewModel)
                ScreenType.Settings -> SettingsScreen(viewModel = viewModel)
            }
        }
    }
}
