package com.qcb.keepaccounts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.qcb.keepaccounts.ui.components.BottomNavigationBar
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AppThemePreset
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.model.paletteForTheme
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.screens.AISettingsScreen
import com.qcb.keepaccounts.ui.screens.AppSettingsScreen
import com.qcb.keepaccounts.ui.screens.CacheCleanupScreen
import com.qcb.keepaccounts.ui.screens.CategoryManagementScreen
import com.qcb.keepaccounts.ui.screens.ChatScreen
import com.qcb.keepaccounts.ui.screens.HomeScreen
import com.qcb.keepaccounts.ui.screens.LedgerScreen
import com.qcb.keepaccounts.ui.screens.ManualEntryScreen
import com.qcb.keepaccounts.ui.screens.ProfileScreen
import com.qcb.keepaccounts.ui.screens.SearchScreen
import com.qcb.keepaccounts.ui.theme.KeepAccountsTheme
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel

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
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    var chatInitialInput by remember { mutableStateOf<String?>(null) }
    var manualEntryPrefill by remember { mutableStateOf<ManualEntryPrefill?>(null) }
    var appTheme by remember { mutableStateOf(AppThemePreset.MINT) }
    var aiConfig by remember { mutableStateOf(AiAssistantConfig()) }
    var userName by remember { mutableStateOf("主人") }

    val palette = remember(appTheme) { paletteForTheme(appTheme) }

    val appContainer = (LocalContext.current.applicationContext as KeepAccountsApplication).container
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(appContainer.transactionRepository),
    )
    val systemUiController = rememberSystemUiController()

    fun navigateToBottomTab(route: String) {
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    SideEffect {
        systemUiController.setStatusBarColor(color = Color.Transparent, darkIcons = true)
        systemUiController.setNavigationBarColor(color = Color.Transparent, darkIcons = true)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            palette.backgroundLight.copy(alpha = 0.94f),
                            palette.background.copy(alpha = 0.96f),
                            Color.White,
                        ),
                    ),
                ),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(220.dp)
                    .blur(95.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.primary.copy(alpha = 0.35f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .size(200.dp)
                    .blur(90.dp)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                palette.secondary.copy(alpha = 0.28f),
                                Color.Transparent,
                            ),
                        ),
                    ),
            )
            Scaffold(
                modifier = Modifier.fillMaxSize(),
                containerColor = Color.Transparent,
                contentWindowInsets = WindowInsets(0, 0, 0, 0),
                bottomBar = {
                    if (KeepAccountsDestination.isBottomNavRoute(currentRoute)) {
                        BottomNavigationBar(
                            navController = navController,
                            items = KeepAccountsDestination.bottomNavItems,
                            activeColor = palette.primaryDark,
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = KeepAccountsDestination.HOME,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    composable(KeepAccountsDestination.HOME) {
                        HomeScreen(
                            viewModel = mainViewModel,
                            assistantName = aiConfig.name,
                            assistantAvatar = aiConfig.avatar,
                            onSearchClick = { navController.navigate(KeepAccountsDestination.SEARCH) },
                            onAiRecordClick = {
                                chatInitialInput = "牛肉粉丝汤 22"
                                navigateToBottomTab(KeepAccountsDestination.CHAT)
                            },
                            onManualRecordClick = {
                                manualEntryPrefill = null
                                navController.navigate(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                            onViewAllClick = { navigateToBottomTab(KeepAccountsDestination.LEDGER) },
                            onEditRecord = { prefill ->
                                manualEntryPrefill = prefill
                                navController.navigate(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                            onDeleteRecord = { id ->
                                if (id > 0L) mainViewModel.deleteTransaction(id)
                            },
                        )
                    }

                    composable(KeepAccountsDestination.CHAT) {
                        ChatScreen(
                            aiConfig = aiConfig,
                            userName = userName,
                            initialInput = chatInitialInput,
                            onConsumedInitialInput = { chatInitialInput = null },
                            onBack = { navController.popBackStack() },
                            onOpenAiSettings = { navController.navigate(KeepAccountsDestination.AI_SETTINGS) },
                            onOpenManualEntry = { prefill ->
                                manualEntryPrefill = prefill
                                navController.navigate(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                        )
                    }

                    composable(KeepAccountsDestination.LEDGER) {
                        LedgerScreen(
                            viewModel = mainViewModel,
                            onEditRecord = { prefill ->
                                manualEntryPrefill = prefill
                                navController.navigate(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                            onDeleteRecord = { id ->
                                if (id > 0L) mainViewModel.deleteTransaction(id)
                            },
                        )
                    }

                    composable(KeepAccountsDestination.PROFILE) {
                        ProfileScreen(
                            aiConfig = aiConfig,
                            userName = userName,
                            onNavigateToOption = { route -> navController.navigate(route) },
                        )
                    }

                    composable(KeepAccountsDestination.MANUAL_ENTRY) {
                        ManualEntryScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            initialData = manualEntryPrefill,
                            onConsumedInitialData = { manualEntryPrefill = null },
                        )
                    }

                    composable(KeepAccountsDestination.SEARCH) {
                        SearchScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            onOpenManualEntry = { prefill ->
                                manualEntryPrefill = prefill
                                navController.navigate(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                        )
                    }

                    composable(KeepAccountsDestination.AI_SETTINGS) {
                        AISettingsScreen(
                            config = aiConfig,
                            onBack = { navController.popBackStack() },
                            onSave = {
                                aiConfig = it
                                navController.popBackStack()
                            },
                        )
                    }

                    composable(
                        route = "${KeepAccountsDestination.SETTINGS}/{${KeepAccountsDestination.SETTINGS_ARG_TYPE}}",
                        arguments = listOf(
                            navArgument(KeepAccountsDestination.SETTINGS_ARG_TYPE) {
                                type = NavType.StringType
                            },
                        ),
                    ) { entry ->
                        val type = entry.arguments?.getString(KeepAccountsDestination.SETTINGS_ARG_TYPE)
                            ?: KeepAccountsDestination.SETTINGS_TYPE_HELP

                        AppSettingsScreen(
                            type = type,
                            theme = appTheme,
                            userName = userName,
                            onBack = { navController.popBackStack() },
                            onThemeChange = { appTheme = it },
                            onUserNameChange = { userName = it },
                        )
                    }

                    composable(KeepAccountsDestination.CATEGORY_MANAGEMENT) {
                        CategoryManagementScreen(onBack = { navController.popBackStack() })
                    }

                    composable(KeepAccountsDestination.CLEAR_CACHE) {
                        CacheCleanupScreen(onBack = { navController.popBackStack() })
                    }
                }
            }
        }
    }
}
