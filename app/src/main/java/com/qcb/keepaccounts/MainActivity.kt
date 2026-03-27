package com.qcb.keepaccounts

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import com.qcb.keepaccounts.ui.model.defaultManualCategories
import com.qcb.keepaccounts.ui.model.paletteForTheme
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.navigation.edgeSwipeBack
import com.qcb.keepaccounts.ui.navigation.rememberSwipeTabNavigator
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
    var userAvatarUri by remember { mutableStateOf<String?>(null) }
    var manualCategories by remember { mutableStateOf(defaultManualCategories) }

    val palette = remember(appTheme) { paletteForTheme(appTheme) }

    val appContainer = (LocalContext.current.applicationContext as KeepAccountsApplication).container
    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(appContainer.transactionRepository),
    )
    val transactions by mainViewModel.transactions.collectAsStateWithLifecycle()
    val systemUiController = rememberSystemUiController()
    val usedCategoryCount = remember(transactions) {
        transactions
            .groupingBy { tx -> tx.categoryName.ifBlank { "其他" } }
            .eachCount()
    }
    val bottomRoutes = remember {
        listOf(
            KeepAccountsDestination.HOME,
            KeepAccountsDestination.CHAT,
            KeepAccountsDestination.LEDGER,
            KeepAccountsDestination.PROFILE,
        )
    }

    fun tabIndex(route: String?): Int = bottomRoutes.indexOf(route)

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

    val swipeTabModifier = rememberSwipeTabNavigator(
        currentRoute = currentRoute,
        navigateToTab = { route -> navigateToBottomTab(route) },
    )

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
                    enterTransition = {
                        val from = tabIndex(initialState.destination.route)
                        val to = tabIndex(targetState.destination.route)
                        if (from >= 0 && to >= 0) {
                            if (to > from) {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(220),
                                )
                            } else {
                                slideIntoContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(220),
                                )
                            }
                        } else {
                            fadeIn(animationSpec = tween(160))
                        }
                    },
                    exitTransition = {
                        val from = tabIndex(initialState.destination.route)
                        val to = tabIndex(targetState.destination.route)
                        if (from >= 0 && to >= 0) {
                            if (to > from) {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Left,
                                    animationSpec = tween(220),
                                )
                            } else {
                                slideOutOfContainer(
                                    AnimatedContentTransitionScope.SlideDirection.Right,
                                    animationSpec = tween(220),
                                )
                            }
                        } else {
                            fadeOut(animationSpec = tween(140))
                        }
                    },
                    popEnterTransition = {
                        slideIntoContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(220),
                        )
                    },
                    popExitTransition = {
                        slideOutOfContainer(
                            AnimatedContentTransitionScope.SlideDirection.Right,
                            animationSpec = tween(220),
                        )
                    },
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    composable(KeepAccountsDestination.HOME) {
                        HomeScreen(
                            modifier = swipeTabModifier,
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
                            modifier = swipeTabModifier,
                            aiConfig = aiConfig,
                            userName = userName,
                            userAvatarUri = userAvatarUri,
                            palette = palette,
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
                            modifier = swipeTabModifier,
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
                            modifier = swipeTabModifier,
                            aiConfig = aiConfig,
                            userName = userName,
                            userAvatarUri = userAvatarUri,
                            theme = appTheme,
                            highlightColor = palette.primaryDark,
                            onNavigateToOption = { route -> navController.navigate(route) },
                        )
                    }

                    composable(KeepAccountsDestination.MANUAL_ENTRY) {
                        ManualEntryScreen(
                            modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            categories = manualCategories,
                            selectedColor = palette.primaryDark,
                            initialData = manualEntryPrefill,
                            onConsumedInitialData = { manualEntryPrefill = null },
                        )
                    }

                    composable(KeepAccountsDestination.SEARCH) {
                        SearchScreen(
                            modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
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
                            modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
                            config = aiConfig,
                            accentColor = palette.primaryDark,
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
                            modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
                            type = type,
                            theme = appTheme,
                            userName = userName,
                            userAvatarUri = userAvatarUri,
                            accentColor = palette.primaryDark,
                            onBack = { navController.popBackStack() },
                            onThemeChange = { appTheme = it },
                            onUserNameChange = { userName = it },
                            onUserAvatarChange = { userAvatarUri = it },
                        )
                    }

                    composable(KeepAccountsDestination.CATEGORY_MANAGEMENT) {
                        CategoryManagementScreen(
                            modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
                            categories = manualCategories,
                            usedCategoryCount = usedCategoryCount,
                            accentColor = palette.primaryDark,
                            onBack = { navController.popBackStack() },
                            onAddCategory = { newCategory ->
                                val trimmed = newCategory.trim()
                                if (trimmed.isNotBlank() && manualCategories.none { it == trimmed }) {
                                    manualCategories = manualCategories + trimmed
                                }
                            },
                            onDeleteCategory = { category ->
                                if (manualCategories.size > 1 && (usedCategoryCount[category] ?: 0) == 0) {
                                    manualCategories = manualCategories.filterNot { it == category }
                                }
                            },
                        )
                    }

                    composable(KeepAccountsDestination.CLEAR_CACHE) {
                        CacheCleanupScreen(
                            modifier = Modifier.edgeSwipeBack { navController.popBackStack() },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
