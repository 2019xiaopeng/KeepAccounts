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
            primary = Color(0xFFA8E6CF),
            primaryDark = Color(0xFF95DCB4),
            primaryLight = Color(0xFFF0FDF4),
            background = Color(0xFFE8F0EB),
            backgroundLight = Color(0xFFE0F2E9),
            secondary = Color(0xFFF3D2C1),
            secondaryDark = Color(0xFFF6B98D),
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
    val tone: AiTone = AiTone.HEALING,
    val chatBackground: ChatBackgroundPreset = ChatBackgroundPreset.NONE,
)

data class ManualEntryPrefill(
    val type: String = "expense",
    val category: String = "",
    val desc: String = "",
    val amount: String = "",
)
