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

    val bottomNavItems = listOf(
        BottomNavItem(route = HOME, label = "首页", icon = "🏠"),
        BottomNavItem(route = CHAT, label = "聊天", icon = "💬"),
        BottomNavItem(route = LEDGER, label = "账本", icon = "📅"),
        BottomNavItem(route = PROFILE, label = "我的", icon = "⚙️"),
    )
}
