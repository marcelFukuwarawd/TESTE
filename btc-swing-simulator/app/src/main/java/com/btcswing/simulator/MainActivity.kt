package com.btcswing.simulator

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.btcswing.simulator.di.ServiceLocator
import com.btcswing.simulator.service.TradingForegroundService
import com.btcswing.simulator.ui.screens.AnalysisScreen
import com.btcswing.simulator.ui.screens.DashboardScreen
import com.btcswing.simulator.ui.screens.ReportsScreen
import com.btcswing.simulator.ui.screens.SettingsScreen
import com.btcswing.simulator.ui.screens.TradesScreen
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val notifPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* usuário decide; o app funciona sem notificações */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Android 13+: permissão de notificação (oficial, em runtime)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Se o robô estava habilitado (ex.: app recém-atualizado), religa o serviço
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (ServiceLocator.settings.current().botEnabled &&
                    !TradingForegroundService.isRunning
                ) {
                    TradingForegroundService.start(applicationContext)
                }
            } catch (_: Exception) { }
        }

        setContent {
            MaterialTheme(colorScheme = BtcSwingColors) {
                AppRoot()
            }
        }
    }
}

val BtcSwingColors = darkColorScheme(
    primary = Color(0xFFF7931A),
    onPrimary = Color(0xFF1A1200),
    secondary = Color(0xFF4ADE80),
    tertiary = Color(0xFF60A5FA),
    background = Color(0xFF0B0E17),
    surface = Color(0xFF131826),
    surfaceVariant = Color(0xFF1E2536),
    onBackground = Color(0xFFE2E8F0),
    onSurface = Color(0xFFE2E8F0),
    error = Color(0xFFF87171)
)

private data class NavTab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    NavTab("dashboard", "Início", Icons.Filled.Home),
    NavTab("trades", "Operações", Icons.Filled.List),
    NavTab("analysis", "Análise", Icons.Filled.Search),
    NavTab("reports", "Relatórios", Icons.Filled.DateRange),
    NavTab("settings", "Ajustes", Icons.Filled.Settings)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppRoot(vm: MainViewModel = viewModel()) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route ?: "dashboard"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("BTC Swing Simulator", color = Color(0xFFF7931A)) },
                actions = {
                    Text(
                        text = if (vm.botRunning.value) "● robô ativo" else "○ robô parado",
                        color = if (vm.botRunning.value) Color(0xFF4ADE80) else Color(0xFF94A3B8),
                        modifier = Modifier.padding(end = 16.dp)
                    )
                }
            )
        },
        bottomBar = {
            NavigationBar {
                tabs.forEach { tab ->
                    NavigationBarItem(
                        selected = currentRoute == tab.route,
                        onClick = {
                            nav.navigate(tab.route) {
                                popUpTo("dashboard") { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(tab.icon, contentDescription = tab.label) },
                        label = { Text(tab.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(nav, startDestination = "dashboard", modifier = Modifier.padding(padding)) {
            composable("dashboard") { DashboardScreen(vm) }
            composable("trades") { TradesScreen(vm) }
            composable("analysis") { AnalysisScreen(vm) }
            composable("reports") { ReportsScreen(vm) }
            composable("settings") { SettingsScreen(vm) }
        }
    }
}
