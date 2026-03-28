package com.qcb.keepaccounts

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.core.content.ContextCompat
import com.google.accompanist.systemuicontroller.rememberSystemUiController
import com.qcb.keepaccounts.reminder.LedgerReminderScheduler
import com.qcb.keepaccounts.ui.components.BottomNavigationBar
import com.qcb.keepaccounts.ui.model.AiAssistantConfig
import com.qcb.keepaccounts.ui.model.AiChatRecord
import com.qcb.keepaccounts.ui.model.AppThemePreset
import com.qcb.keepaccounts.ui.model.ManualEntryPrefill
import com.qcb.keepaccounts.ui.model.ThemePalette
import com.qcb.keepaccounts.ui.model.defaultManualCategories
import com.qcb.keepaccounts.ui.model.paletteForTheme
import com.qcb.keepaccounts.ui.navigation.KeepAccountsDestination
import com.qcb.keepaccounts.ui.screens.AISettingsScreen
import com.qcb.keepaccounts.ui.screens.AppSettingsScreen
import com.qcb.keepaccounts.ui.screens.CacheCleanupScreen
import com.qcb.keepaccounts.ui.screens.CategoryManagementScreen
import com.qcb.keepaccounts.ui.screens.ChatScreen
import com.qcb.keepaccounts.ui.screens.HomeScreen
import com.qcb.keepaccounts.ui.screens.InitialSetupScreen
import com.qcb.keepaccounts.ui.screens.LedgerScreen
import com.qcb.keepaccounts.ui.screens.ManualEntryScreen
import com.qcb.keepaccounts.ui.screens.ProfileScreen
import com.qcb.keepaccounts.ui.screens.SearchScreen
import com.qcb.keepaccounts.ui.theme.KeepAccountsTheme
import com.qcb.keepaccounts.ui.viewmodel.ChatViewModel
import com.qcb.keepaccounts.ui.viewmodel.MainViewModel
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

private const val MAIN_TABS_ROUTE = "main_tabs"

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
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
    val appContext = LocalContext.current.applicationContext
    val appContainer = (LocalContext.current.applicationContext as KeepAccountsApplication).container
    val userSettingsRepository = appContainer.userSettingsRepository
    val storedSettings by produceState<com.qcb.keepaccounts.data.local.preferences.UserSettingsState?>(
        initialValue = null,
        key1 = userSettingsRepository,
    ) {
        userSettingsRepository.settingsFlow.collect { value = it }
    }

    if (storedSettings == null) {
        Box(modifier = Modifier.fillMaxSize())
        return
    }

    val settings = storedSettings!!

    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    val isOnMainTabs = currentRoute == MAIN_TABS_ROUTE
    val coroutineScope = rememberCoroutineScope()

    var chatInitialInput by remember { mutableStateOf<String?>(null) }
    var manualEntryPrefill by remember { mutableStateOf<ManualEntryPrefill?>(null) }
    var appTheme by remember { mutableStateOf(AppThemePreset.MINT) }
    var aiConfig by remember { mutableStateOf(AiAssistantConfig()) }
    var userName by remember { mutableStateOf("主人") }
    var homeSlogan by remember { mutableStateOf("劳动最光荣 💼") }
    var userAvatarUri by remember { mutableStateOf<String?>(null) }
    var manualCategories by remember { mutableStateOf(defaultManualCategories) }
    var ledgerCurrency by remember { mutableStateOf("人民币") }
    var defaultLedgerName by remember { mutableStateOf("日常账本") }
    var reminderTime by remember { mutableStateOf("21:00") }
    var monthlyBudget by remember { mutableStateOf(2000.0) }
    var hasRequestedNotificationPermission by rememberSaveable { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) {
        hasRequestedNotificationPermission = true
    }

    LaunchedEffect(settings) {
        appTheme = settings.theme
        aiConfig = settings.aiConfig
        userName = settings.userName
        homeSlogan = settings.homeSlogan
        userAvatarUri = settings.userAvatarUri
        manualCategories = settings.manualCategories
        ledgerCurrency = settings.ledgerCurrency
        defaultLedgerName = settings.defaultLedgerName
        reminderTime = settings.reminderTime
        monthlyBudget = settings.monthlyBudget
    }

    LaunchedEffect(settings.initialized, settings.reminderTime) {
        if (settings.initialized) {
            LedgerReminderScheduler.scheduleDailyReminder(appContext, settings.reminderTime)
        }
    }

    LaunchedEffect(settings.initialized) {
        if (!settings.initialized) return@LaunchedEffect
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return@LaunchedEffect

        val granted = ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.POST_NOTIFICATIONS,
        ) == PackageManager.PERMISSION_GRANTED

        if (!granted && !hasRequestedNotificationPermission) {
            hasRequestedNotificationPermission = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    val palette = remember(appTheme) { paletteForTheme(appTheme) }

    val mainViewModel: MainViewModel = viewModel(
        factory = MainViewModel.provideFactory(appContainer.transactionRepository),
    )
    val chatViewModel: ChatViewModel = viewModel(
        factory = ChatViewModel.provideFactory(appContainer.chatRepository),
    )
    val appUpdateRepository = appContainer.appUpdateRepository
    val transactions by mainViewModel.transactions.collectAsStateWithLifecycle()
    val aiChatRecords by chatViewModel.chatRecords.collectAsStateWithLifecycle()
    val aiIsSending by chatViewModel.isSending.collectAsStateWithLifecycle()
    val systemUiController = rememberSystemUiController()
    val usedCategoryCount = remember(transactions) {
        transactions
            .groupingBy { tx -> tx.categoryName.ifBlank { "其他" } }
            .eachCount()
    }
    val pagerState = rememberPagerState(
        initialPage = 0,
        pageCount = { KeepAccountsDestination.bottomNavItems.size },
    )
    var pendingUpdateVersionTag by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUpdateReleaseUrl by rememberSaveable { mutableStateOf<String?>(null) }

    fun animateToTab(index: Int) {
        if (index < 0 || index >= KeepAccountsDestination.bottomNavItems.size) return
        coroutineScope.launch {
            pagerState.animateScrollToPage(
                page = index,
                animationSpec = tween(
                    durationMillis = 280,
                    easing = FastOutSlowInEasing,
                ),
            )
        }
    }

    fun animateToTabRoute(route: String) {
        val index = KeepAccountsDestination.bottomNavItems.indexOfFirst { it.route == route }
        if (index >= 0) animateToTab(index)
    }

    fun navigateToSubPage(route: String) {
        navController.navigate(route) {
            popUpTo(MAIN_TABS_ROUTE) {
                saveState = true
            }
            launchSingleTop = true
        }
    }

    LaunchedEffect(Unit) {
        val updateInfo = runCatching {
            appUpdateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
        }.getOrNull()

        if (updateInfo != null) {
            pendingUpdateVersionTag = updateInfo.latestVersionTag
            pendingUpdateReleaseUrl = updateInfo.releaseUrl
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
                    .blur(60.dp)
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
                    .blur(56.dp)
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
                    if (isOnMainTabs) {
                        BottomNavigationBar(
                            items = KeepAccountsDestination.bottomNavItems,
                            selectedIndex = pagerState.currentPage,
                            onTabSelected = { animateToTab(it) },
                            activeColor = palette.primaryDark,
                            modifier = Modifier.navigationBarsPadding(),
                        )
                    }
                },
            ) { innerPadding ->
                NavHost(
                    navController = navController,
                    startDestination = if (settings.initialized) MAIN_TABS_ROUTE else KeepAccountsDestination.INITIAL_SETUP,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                ) {
                    composable(KeepAccountsDestination.INITIAL_SETUP) {
                        InitialSetupScreen(
                            initialUserName = userName,
                            initialUserAvatarUri = userAvatarUri,
                            initialTheme = appTheme,
                            initialAiConfig = aiConfig,
                            initialMonthlyBudget = monthlyBudget,
                            onComplete = { setupUserName, setupUserAvatarUri, setupTheme, setupAiConfig, setupMonthlyBudget ->
                                userName = setupUserName
                                userAvatarUri = setupUserAvatarUri
                                appTheme = setupTheme
                                aiConfig = setupAiConfig
                                monthlyBudget = setupMonthlyBudget

                                coroutineScope.launch {
                                    userSettingsRepository.saveInitialSetup(
                                        userName = setupUserName,
                                        userAvatarUri = setupUserAvatarUri,
                                        theme = setupTheme,
                                        aiConfig = setupAiConfig,
                                    )
                                    userSettingsRepository.saveLedgerSettings(
                                        ledgerCurrency = ledgerCurrency,
                                        defaultLedgerName = defaultLedgerName,
                                        reminderTime = reminderTime,
                                        monthlyBudget = setupMonthlyBudget,
                                    )
                                }

                                navController.navigate(MAIN_TABS_ROUTE) {
                                    popUpTo(KeepAccountsDestination.INITIAL_SETUP) { inclusive = true }
                                    launchSingleTop = true
                                }
                            },
                        )
                    }

                    composable(MAIN_TABS_ROUTE) {
                        MainTabsPager(
                            pagerState = pagerState,
                            viewModel = mainViewModel,
                            aiConfig = aiConfig,
                            userName = userName,
                            homeSlogan = homeSlogan,
                            userAvatarUri = userAvatarUri,
                            theme = appTheme,
                            ledgerCurrency = ledgerCurrency,
                            defaultLedgerName = defaultLedgerName,
                            monthlyBudget = monthlyBudget,
                            palette = palette,
                            aiChatRecords = aiChatRecords,
                            aiIsSending = aiIsSending,
                            initialChatInput = chatInitialInput,
                            onConsumedInitialInput = { chatInitialInput = null },
                            onSendChatMessage = { text ->
                                chatViewModel.sendMessage(
                                    text = text,
                                    aiConfig = aiConfig,
                                    userName = userName,
                                )
                            },
                            onDeleteChatMessage = { id -> chatViewModel.deleteMessage(id) },
                            onSearchClick = { navigateToSubPage(KeepAccountsDestination.SEARCH) },
                            onManualRecordClick = {
                                manualEntryPrefill = null
                                navigateToSubPage(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                            onAiRecordClick = {
                                chatInitialInput = null
                                animateToTabRoute(KeepAccountsDestination.CHAT)
                            },
                            onViewLedger = { animateToTabRoute(KeepAccountsDestination.LEDGER) },
                            onEditRecord = { prefill ->
                                manualEntryPrefill = prefill
                                navigateToSubPage(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                            onDeleteRecord = { id ->
                                if (id > 0L) mainViewModel.deleteTransaction(id)
                            },
                            onOpenAiSettings = { navigateToSubPage(KeepAccountsDestination.AI_SETTINGS) },
                            onOpenProfileRoute = { route -> navigateToSubPage(route) },
                        )
                    }

                    composable(KeepAccountsDestination.MANUAL_ENTRY) {
                        ManualEntryScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            categories = manualCategories,
                            selectedColor = palette.primaryDark,
                            ledgerCurrency = ledgerCurrency,
                            initialData = manualEntryPrefill,
                            onConsumedInitialData = { manualEntryPrefill = null },
                        )
                    }

                    composable(KeepAccountsDestination.SEARCH) {
                        SearchScreen(
                            viewModel = mainViewModel,
                            onBack = { navController.popBackStack() },
                            ledgerCurrency = ledgerCurrency,
                            accentColor = palette.primaryDark,
                            onOpenManualEntry = { prefill ->
                                manualEntryPrefill = prefill
                                navigateToSubPage(KeepAccountsDestination.MANUAL_ENTRY)
                            },
                        )
                    }

                    composable(KeepAccountsDestination.AI_SETTINGS) {
                        AISettingsScreen(
                            config = aiConfig,
                            chatRecords = aiChatRecords,
                            accentColor = palette.primaryDark,
                            onBack = { navController.popBackStack() },
                            onSave = {
                                aiConfig = it
                                coroutineScope.launch {
                                    userSettingsRepository.saveAiConfig(it)
                                }
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
                            homeSlogan = homeSlogan,
                            userAvatarUri = userAvatarUri,
                            ledgerCurrency = ledgerCurrency,
                            defaultLedgerName = defaultLedgerName,
                            reminderTime = reminderTime,
                            monthlyBudget = monthlyBudget,
                            accentColor = palette.primaryDark,
                            onBack = { navController.popBackStack() },
                            onThemeChange = {
                                appTheme = it
                                coroutineScope.launch {
                                    userSettingsRepository.saveTheme(it)
                                }
                            },
                            onUserNameChange = {
                                userName = it
                                coroutineScope.launch {
                                    userSettingsRepository.saveUserProfile(userName, userAvatarUri)
                                }
                            },
                            onUserAvatarChange = {
                                userAvatarUri = it
                                coroutineScope.launch {
                                    userSettingsRepository.saveUserProfile(userName, userAvatarUri)
                                }
                            },
                            onHomeSloganChange = {
                                homeSlogan = it
                                coroutineScope.launch {
                                    userSettingsRepository.saveHomeSlogan(it)
                                }
                            },
                            onLedgerSettingsChange = { currency, ledgerName, time, budget ->
                                ledgerCurrency = currency
                                defaultLedgerName = ledgerName
                                reminderTime = time
                                monthlyBudget = budget
                                LedgerReminderScheduler.scheduleDailyReminder(appContext, time)
                                coroutineScope.launch {
                                    userSettingsRepository.saveLedgerSettings(
                                        ledgerCurrency = currency,
                                        defaultLedgerName = ledgerName,
                                        reminderTime = time,
                                        monthlyBudget = budget,
                                    )
                                }
                            },
                        )
                    }

                    composable(KeepAccountsDestination.CATEGORY_MANAGEMENT) {
                        CategoryManagementScreen(
                            categories = manualCategories,
                            usedCategoryCount = usedCategoryCount,
                            accentColor = palette.primaryDark,
                            onBack = { navController.popBackStack() },
                            onAddCategory = { newCategory ->
                                val trimmed = newCategory.trim()
                                if (trimmed.isNotBlank() && manualCategories.none { it == trimmed }) {
                                    manualCategories = manualCategories + trimmed
                                    coroutineScope.launch {
                                        userSettingsRepository.saveManualCategories(manualCategories)
                                    }
                                }
                            },
                            onDeleteCategory = { category ->
                                if (manualCategories.size > 1) {
                                    manualCategories = manualCategories.filterNot { it == category }
                                    coroutineScope.launch {
                                        userSettingsRepository.saveManualCategories(manualCategories)
                                    }
                                }
                            },
                        )
                    }

                    composable(KeepAccountsDestination.CLEAR_CACHE) {
                        CacheCleanupScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                }

                if (!pendingUpdateVersionTag.isNullOrBlank() && !pendingUpdateReleaseUrl.isNullOrBlank()) {
                    AlertDialog(
                        onDismissRequest = {
                            pendingUpdateVersionTag = null
                            pendingUpdateReleaseUrl = null
                        },
                        title = { Text(text = "发现新版本") },
                        text = {
                            Text(
                                text = "有新版本啦：${pendingUpdateVersionTag}，当前版本 ${BuildConfig.VERSION_NAME}。是否去看看更新内容？",
                            )
                        },
                        confirmButton = {
                            TextButton(
                                onClick = {
                                    val url = pendingUpdateReleaseUrl
                                    if (!url.isNullOrBlank()) {
                                        runCatching {
                                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            appContext.startActivity(intent)
                                        }
                                    }
                                    pendingUpdateVersionTag = null
                                    pendingUpdateReleaseUrl = null
                                },
                            ) {
                                Text(text = "去看看")
                            }
                        },
                        dismissButton = {
                            TextButton(
                                onClick = {
                                    pendingUpdateVersionTag = null
                                    pendingUpdateReleaseUrl = null
                                },
                            ) {
                                Text(text = "稍后")
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun MainTabsPager(
    pagerState: PagerState,
    viewModel: MainViewModel,
    aiConfig: AiAssistantConfig,
    userName: String,
    homeSlogan: String,
    userAvatarUri: String?,
    theme: AppThemePreset,
    ledgerCurrency: String,
    defaultLedgerName: String,
    monthlyBudget: Double,
    palette: ThemePalette,
    aiChatRecords: List<AiChatRecord>,
    aiIsSending: Boolean,
    initialChatInput: String?,
    onConsumedInitialInput: () -> Unit,
    onSendChatMessage: (String) -> Unit,
    onDeleteChatMessage: (Long) -> Unit,
    onSearchClick: () -> Unit,
    onManualRecordClick: () -> Unit,
    onAiRecordClick: () -> Unit,
    onViewLedger: () -> Unit,
    onEditRecord: (ManualEntryPrefill) -> Unit,
    onDeleteRecord: (Long) -> Unit,
    onOpenAiSettings: () -> Unit,
    onOpenProfileRoute: (String) -> Unit,
) {
    HorizontalPager(
        state = pagerState,
        modifier = Modifier.fillMaxSize(),
    ) { page ->
        when (page) {
            0 -> HomeScreen(
                viewModel = viewModel,
                assistantName = aiConfig.name,
                assistantAvatar = aiConfig.avatar,
                homeSlogan = homeSlogan,
                ledgerCurrency = ledgerCurrency,
                defaultLedgerName = defaultLedgerName,
                monthlyBudget = monthlyBudget,
                accentColor = palette.primaryDark,
                onSearchClick = onSearchClick,
                onAiRecordClick = onAiRecordClick,
                onManualRecordClick = onManualRecordClick,
                onViewAllClick = onViewLedger,
                onEditRecord = onEditRecord,
                onDeleteRecord = onDeleteRecord,
            )

            1 -> ChatScreen(
                aiConfig = aiConfig,
                userName = userName,
                userAvatarUri = userAvatarUri,
                palette = palette,
                chatRecords = aiChatRecords,
                isSending = aiIsSending,
                initialInput = initialChatInput,
                onConsumedInitialInput = onConsumedInitialInput,
                onSendMessage = onSendChatMessage,
                onDeleteMessage = onDeleteChatMessage,
                onBack = {},
                onOpenAiSettings = onOpenAiSettings,
                onOpenManualEntry = onEditRecord,
            )

            2 -> LedgerScreen(
                viewModel = viewModel,
                ledgerCurrency = ledgerCurrency,
                defaultLedgerName = defaultLedgerName,
                monthlyBudget = monthlyBudget,
                onEditRecord = onEditRecord,
                onDeleteRecord = onDeleteRecord,
                accentColor = palette.primaryDark,
            )

            else -> ProfileScreen(
                aiConfig = aiConfig,
                userName = userName,
                userAvatarUri = userAvatarUri,
                theme = theme,
                highlightColor = palette.primaryDark,
                onNavigateToOption = onOpenProfileRoute,
            )
        }
    }
}
