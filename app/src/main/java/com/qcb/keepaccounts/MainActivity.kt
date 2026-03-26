package com.qcb.keepaccounts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Surface
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.qcb.keepaccounts.ui.components.BottomNavigationBar
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.screens.ChatScreen
import com.qcb.keepaccounts.ui.screens.HomeScreen
import com.qcb.keepaccounts.ui.screens.LedgerScreen
import com.qcb.keepaccounts.ui.screens.ProfileScreen
import com.qcb.keepaccounts.ui.theme.KeepAccountsTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            KeepAccountsTheme {
                KeepAccountsApp()
            }
        }
    }
}

@Composable
fun KeepAccountsApp() {
    val navController = rememberNavController()

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFFF7FDF9),
                            Color(0xFFF2FBF6),
                            Color(0xFFFFFBF3),
                        ),
                    ),
                ),
        ) {
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                bottomBar = {
                    BottomNavigationBar(
                        navController = navController,
                        items = KeepAccountsDestination.bottomNavItems,
                    )
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = KeepAccountsDestination.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    composable(KeepAccountsDestination.HOME) { HomeScreen() }
                    composable(KeepAccountsDestination.CHAT) { ChatScreen() }
                    composable(KeepAccountsDestination.LEDGER) { LedgerScreen() }
                    composable(KeepAccountsDestination.PROFILE) { ProfileScreen() }
                }
            }
        }
    }
}