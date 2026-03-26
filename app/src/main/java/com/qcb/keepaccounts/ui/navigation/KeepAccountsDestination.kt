package com.qcb.keepaccounts.ui.navigation

data class BottomNavItem(
    val route: String,
    val label: String,
    val icon: String,
)

object KeepAccountsDestination {
    const val HOME = "home"
    const val CHAT = "chat"
    const val LEDGER = "ledger"
    const val PROFILE = "profile"
    const val MANUAL_ENTRY = "manual_entry"

    const val CATEGORY_MANAGEMENT = "category_management"
    const val LEDGER_SETTINGS = "ledger_settings"
    const val IMPORT_EXPORT = "import_export"
    const val CLEAR_CACHE = "clear_cache"
    const val THEME_APPEARANCE = "theme_appearance"
    const val HELP_FEEDBACK = "help_feedback"

    val bottomNavItems = listOf(
        BottomNavItem(route = HOME, label = "首页", icon = "🏠"),
        BottomNavItem(route = CHAT, label = "对话", icon = "💬"),
        BottomNavItem(route = LEDGER, label = "账本", icon = "📅"),
        BottomNavItem(route = PROFILE, label = "我的", icon = "⚙️"),
    )

    fun isBottomNavRoute(route: String?): Boolean {
        return route == HOME || route == CHAT || route == LEDGER || route == PROFILE
    }
}
