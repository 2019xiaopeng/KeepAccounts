package com.qcb.keepaccounts.ui.model

import androidx.compose.ui.graphics.Color

enum class AppThemePreset {
    MINT,
    PINK,
    BLUE,
}

data class ThemePalette(
    val primary: Color,
    val primaryDark: Color,
    val primaryLight: Color,
    val background: Color,
    val backgroundLight: Color,
    val secondary: Color,
    val secondaryDark: Color,
)

fun paletteForTheme(theme: AppThemePreset): ThemePalette {
    return when (theme) {
        AppThemePreset.MINT -> ThemePalette(
            primary = Color(0xFF9BE7D8),
            primaryDark = Color(0xFF7FD6C6),
            primaryLight = Color(0xFFF3FFFB),
            background = Color(0xFFEAF8F4),
            backgroundLight = Color(0xFFF6FFFC),
            secondary = Color(0xFFEFF3FF),
            secondaryDark = Color(0xFFBED5FF),
        )

        AppThemePreset.PINK -> ThemePalette(
            primary = Color(0xFFFFB7B2),
            primaryDark = Color(0xFFFF9E99),
            primaryLight = Color(0xFFFFF0F5),
            background = Color(0xFFFFF0F0),
            backgroundLight = Color(0xFFFFE4E1),
            secondary = Color(0xFFFFDAC1),
            secondaryDark = Color(0xFFFFB7B2),
        )

        AppThemePreset.BLUE -> ThemePalette(
            primary = Color(0xFFA1C4FD),
            primaryDark = Color(0xFF8EB5F5),
            primaryLight = Color(0xFFF0F7FF),
            background = Color(0xFFF0F7FF),
            backgroundLight = Color(0xFFE2F0FF),
            secondary = Color(0xFFC2E9FB),
            secondaryDark = Color(0xFFA1C4FD),
        )
    }
}

enum class AiTone {
    HEALING,
    TSUNDERE,
    RATIONAL,
}

enum class ChatBackgroundPreset {
    NONE,
    OCEAN,
    FOREST,
    SUNSET,
}

data class AiAssistantConfig(
    val name: String = "Nanami",
    val avatar: String = "🌊",
    val avatarUri: String? = null,
    val tone: AiTone = AiTone.HEALING,
    val chatBackground: ChatBackgroundPreset = ChatBackgroundPreset.NONE,
    val customChatBackgroundUri: String? = null,
)

data class ManualEntryPrefill(
    val type: String = "expense",
    val category: String = "",
    val desc: String = "",
    val amount: String = "",
    val recordTimestamp: Long? = null,
)

data class AiChatRecord(
    val id: Long,
    val timestamp: Long,
    val role: String,
    val content: String,
    val isReceipt: Boolean = false,
    val transactionId: Long? = null,
    val receiptRecordTimestamp: Long? = null,
)

val defaultManualCategories = listOf(
    "餐饮美食",
    "交通出行",
    "购物消费",
    "居家生活",
    "娱乐休闲",
    "医疗健康",
    "人情交际",
    "其他",
)
