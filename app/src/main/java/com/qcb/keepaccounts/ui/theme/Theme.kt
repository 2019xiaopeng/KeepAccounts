package com.qcb.keepaccounts.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = MintGreen,
    secondary = MintGreenDeep,
    tertiary = WatermelonPink,
    background = Color(0xFF20201E),
    surface = Color(0xFF2B2A28),
    onPrimary = Color(0xFF1D1D1B),
    onSecondary = Color(0xFF1D1D1B),
    onBackground = Color(0xFFECE8E3),
    onSurface = Color(0xFFECE8E3),
)

private val LightColorScheme = lightColorScheme(
    primary = MintGreen,
    secondary = MintGreenDeep,
    tertiary = WatermelonPink,
    background = CreamBackground,
    surface = GlassSurface,
    onPrimary = WarmBrown,
    onSecondary = WarmBrown,
    onBackground = WarmBrown,
    onSurface = WarmBrown,
)

@Composable
fun KeepAccountsTheme(
    darkTheme: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}