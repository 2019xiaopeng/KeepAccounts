package com.qcb.keepaccounts.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: ImageVector,
)

object KeepAccountsDestination {
    const val HOME = "home"
    const val CHAT = "chat"
    const val LEDGER = "ledger"
    const val PROFILE = "profile"
    const val INITIAL_SETUP = "initial_setup"
    const val MANUAL_ENTRY = "manual_entry"
    const val SEARCH = "search"
    const val AI_SETTINGS = "ai_settings"
    const val SETTINGS = "settings"
    const val SETTINGS_ARG_TYPE = "type"

    const val SETTINGS_TYPE_EXPORT = "export"
    const val SETTINGS_TYPE_THEME = "theme"
    const val SETTINGS_TYPE_HELP = "help"
    const val SETTINGS_TYPE_LEDGER = "ledger-settings"
    const val SETTINGS_TYPE_MY_NAME = "my-name"

    const val CATEGORY_MANAGEMENT = "category_management"
    const val LEDGER_SETTINGS = "ledger_settings"
    const val IMPORT_EXPORT = "import_export"
    const val CLEAR_CACHE = "clear_cache"
    const val THEME_APPEARANCE = "theme_appearance"
    const val HELP_FEEDBACK = "help_feedback"

    fun settingsRoute(type: String): String = "$SETTINGS/$type"

    val bottomNavItems = listOf(
        BottomNavItem(route = HOME, label = "首页", icon = Icons.Outlined.Home),
        BottomNavItem(route = CHAT, label = "对话", icon = Icons.Outlined.ChatBubbleOutline),
        BottomNavItem(route = LEDGER, label = "账本", icon = Icons.Outlined.CalendarMonth),
        BottomNavItem(route = PROFILE, label = "设置", icon = Icons.Outlined.Settings),
    )

    fun isBottomNavRoute(route: String?): Boolean {
        return route == HOME || route == CHAT || route == LEDGER || route == PROFILE
    }
}
