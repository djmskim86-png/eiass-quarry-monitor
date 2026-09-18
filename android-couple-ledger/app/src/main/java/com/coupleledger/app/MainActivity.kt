package com.coupleledger.app

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.CreditCard
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.coupleledger.app.ui.cards.AddCardScreen
import com.coupleledger.app.ui.cards.CardListScreen
import com.coupleledger.app.ui.dashboard.DashboardScreen
import com.coupleledger.app.ui.navigation.Destination
import com.coupleledger.app.ui.onboarding.OnboardingScreen
import com.coupleledger.app.ui.settings.SettingsScreen
import com.coupleledger.app.ui.theme.OurLedgerTheme
import com.coupleledger.app.ui.transactions.TransactionListScreen
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val requestSmsPermissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* 결과와 무관하게 진행: 거부 시 자동 인식만 비활성화되고 수동 입력은 계속 가능 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestSmsPermissionsIfNeeded()

        setContent {
            OurLedgerTheme {
                OurLedgerApp()
            }
        }
    }

    private fun requestSmsPermissionsIfNeeded() {
        val needed = listOf(Manifest.permission.RECEIVE_SMS, Manifest.permission.READ_SMS)
            .filter { ContextCompat.checkSelfPermission(this, it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (needed.isNotEmpty()) requestSmsPermissions.launch(needed.toTypedArray())
    }
}

@Composable
private fun OurLedgerApp() {
    val navController = rememberNavController()

    Scaffold(bottomBar = { BottomNav(navController) }) { padding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Onboarding.route,
            modifier = Modifier.padding(padding),
        ) {
            composable(Destination.Onboarding.route) {
                OnboardingScreen(onReady = {
                    navController.navigate(Destination.Dashboard.route) {
                        popUpTo(Destination.Onboarding.route) { inclusive = true }
                    }
                })
            }
            composable(Destination.Dashboard.route) { DashboardScreen() }
            composable(Destination.Cards.route) {
                CardListScreen(onAddCardClick = { navController.navigate(Destination.AddCard.route) })
            }
            composable(Destination.AddCard.route) {
                AddCardScreen(onDone = { navController.popBackStack() })
            }
            composable(Destination.Transactions.route) { TransactionListScreen() }
            composable(Destination.Settings.route) { SettingsScreen() }
        }
    }
}

@Composable
private fun BottomNav(navController: NavHostController) {
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    val items = listOf(
        Triple(Destination.Dashboard, Icons.Default.AccountBalanceWallet, "홈"),
        Triple(Destination.Cards, Icons.Default.CreditCard, "카드"),
        Triple(Destination.Transactions, Icons.Default.List, "내역"),
        Triple(Destination.Settings, Icons.Default.Settings, "설정"),
    )

    if (currentRoute == Destination.Onboarding.route) return

    NavigationBar {
        items.forEach { (destination, icon, label) ->
            NavigationBarItem(
                selected = currentRoute == destination.route,
                onClick = {
                    navController.navigate(destination.route) {
                        popUpTo(Destination.Dashboard.route)
                        launchSingleTop = true
                    }
                },
                icon = { Icon(icon, contentDescription = label) },
                label = { Text(label) },
            )
        }
    }
}
