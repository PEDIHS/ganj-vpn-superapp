package com.ganj.vpn.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalLayoutDirection

internal val GanjBlue = Color(0xFF1769E0)
internal val GanjGreen = Color(0xFF1B8F4E)
internal val GanjAmber = Color(0xFFC57A00)
internal val GanjRed = Color(0xFFC93645)

private val GanjLightColors = lightColorScheme(
    primary = GanjBlue,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD9E7FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = GanjGreen,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFB8F2CD),
    onSecondaryContainer = Color(0xFF00210F),
    error = GanjRed,
    background = Color(0xFFF6F7FB),
    onBackground = Color(0xFF171A20),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF171A20),
    surfaceVariant = Color(0xFFE7EAF0),
    onSurfaceVariant = Color(0xFF44474F),
    outline = Color(0xFF74777F),
)

private val GanjDarkColors = darkColorScheme(
    primary = Color(0xFF9BC7FF),
    onPrimary = Color(0xFF003060),
    primaryContainer = Color(0xFF004787),
    onPrimaryContainer = Color(0xFFD3E4FF),
    secondary = Color(0xFF82DFA8),
    onSecondary = Color(0xFF00391E),
    secondaryContainer = Color(0xFF00522E),
    onSecondaryContainer = Color(0xFFA1F7C2),
    error = Color(0xFFFFB2B8),
    background = Color(0xFF0A1019),
    onBackground = Color(0xFFE2E7F0),
    surface = Color(0xFF111822),
    onSurface = Color(0xFFE2E7F0),
    surfaceVariant = Color(0xFF252D39),
    onSurfaceVariant = Color(0xFFC3C7D0),
    outline = Color(0xFF8D919A),
)

private val GanjTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(lineHeight = 48.sp),
        headlineLarge = headlineLarge.copy(lineHeight = 40.sp),
        headlineMedium = headlineMedium.copy(lineHeight = 36.sp),
        titleLarge = titleLarge.copy(lineHeight = 30.sp),
        titleMedium = titleMedium.copy(lineHeight = 26.sp),
        bodyLarge = bodyLarge.copy(lineHeight = 26.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 23.sp),
        bodySmall = bodySmall.copy(lineHeight = 20.sp),
        labelLarge = labelLarge.copy(lineHeight = 22.sp),
    )
}

@Composable
internal fun GanjTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        MaterialTheme(
            colorScheme = if (darkTheme) GanjDarkColors else GanjLightColors,
            typography = GanjTypography,
            content = content,
        )
    }
}
