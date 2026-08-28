package com.ganj.vpn.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GanjEmerald = Color(0xFF1DA15D)
internal val GanjEmeraldBright = Color(0xFF2DC774)
internal val GanjEmeraldDeep = Color(0xFF075C31)
internal val GanjGold = Color(0xFFD5A63A)
internal val GanjGoldBright = Color(0xFFF0CD70)
internal val GanjJade = Color(0xFF3AA58D)
internal val GanjDanger = Color(0xFFEA6269)
internal val GanjWarning = Color(0xFFE0A33C)

internal val GanjDarkCanvas = Color(0xFF070A08)
internal val GanjDarkSurface = Color(0xFF101712)
internal val GanjDarkSurfaceSecondary = Color(0xFF151F18)
internal val GanjDarkText = Color(0xFFF4F7F3)
internal val GanjDarkMuted = Color(0xFFB3BDB5)

internal val GanjLightCanvas = Color(0xFFF7F8F4)
internal val GanjLightSurface = Color(0xFFFFFFFF)
internal val GanjLightSurfaceSecondary = Color(0xFFF0F4EF)
internal val GanjLightText = Color(0xFF121713)
internal val GanjLightMuted = Color(0xFF5E6961)

private val GanjLightColors = lightColorScheme(
    primary = Color(0xFF08693A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE0F1E6),
    onPrimaryContainer = Color(0xFF04351F),
    secondary = Color(0xFF267E6D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDF1EC),
    onSecondaryContainer = Color(0xFF113E35),
    tertiary = Color(0xFFB98318),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E8B8),
    onTertiaryContainer = Color(0xFF4B3308),
    error = Color(0xFFC7434B),
    onError = Color.White,
    background = GanjLightCanvas,
    onBackground = GanjLightText,
    surface = GanjLightSurface,
    onSurface = GanjLightText,
    surfaceVariant = GanjLightSurfaceSecondary,
    onSurfaceVariant = GanjLightMuted,
    outline = Color(0xFFB8C4BA),
)

private val GanjDarkColors = darkColorScheme(
    primary = GanjEmerald,
    onPrimary = Color(0xFF041A0E),
    primaryContainer = GanjEmeraldDeep,
    onPrimaryContainer = Color(0xFFD9FBE6),
    secondary = GanjJade,
    onSecondary = Color(0xFF031C17),
    secondaryContainer = Color(0xFF154D42),
    onSecondaryContainer = Color(0xFFD9F6EF),
    tertiary = GanjGold,
    onTertiary = Color(0xFF211600),
    tertiaryContainer = Color(0xFF5A4211),
    onTertiaryContainer = Color(0xFFFFEDB4),
    error = GanjDanger,
    onError = Color(0xFF2B090D),
    background = GanjDarkCanvas,
    onBackground = GanjDarkText,
    surface = GanjDarkSurface,
    onSurface = GanjDarkText,
    surfaceVariant = GanjDarkSurfaceSecondary,
    onSurfaceVariant = GanjDarkMuted,
    outline = Color(0xFF4D5C50),
)

private val GanjTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(lineHeight = 44.sp),
        headlineLarge = headlineLarge.copy(lineHeight = 40.sp),
        headlineMedium = headlineMedium.copy(lineHeight = 34.sp),
        titleLarge = titleLarge.copy(lineHeight = 30.sp),
        titleMedium = titleMedium.copy(lineHeight = 26.sp),
        bodyLarge = bodyLarge.copy(lineHeight = 25.sp),
        bodyMedium = bodyMedium.copy(lineHeight = 23.sp),
        bodySmall = bodySmall.copy(lineHeight = 20.sp),
        labelLarge = labelLarge.copy(lineHeight = 22.sp),
    )
}

@Immutable
internal data class GanjGlassPalette(
    val neutralTint: Color,
    val emeraldTint: Color,
    val goldTint: Color,
    val borderSoft: Color,
    val borderStrong: Color,
    val highlight: Color,
    val scrim: Color,
    val opaqueFallback: Color,
    val clearBlur: Dp,
    val regularBlur: Dp,
    val denseBlur: Dp,
)

private val GanjDarkGlass = GanjGlassPalette(
    neutralTint = Color(0xFFD8E2DA),
    emeraldTint = GanjEmerald,
    goldTint = GanjGold,
    borderSoft = Color(0xFF4D5C50),
    borderStrong = Color(0xFF718074),
    highlight = Color(0xFFF7FFF9),
    scrim = Color(0xFF050806),
    opaqueFallback = Color(0xFF141B16),
    clearBlur = 14.dp,
    regularBlur = 22.dp,
    denseBlur = 30.dp,
)

private val GanjLightGlass = GanjGlassPalette(
    neutralTint = Color(0xFFF9FCF8),
    emeraldTint = Color(0xFF08693A),
    goldTint = Color(0xFFB98318),
    borderSoft = Color(0xFFD6DED7),
    borderStrong = Color(0xFFBAC5BC),
    highlight = Color.White,
    scrim = Color(0xFFEFF2ED),
    opaqueFallback = Color(0xFFF3F5F0),
    clearBlur = 14.dp,
    regularBlur = 22.dp,
    denseBlur = 30.dp,
)

internal val LocalGanjGlassPalette = staticCompositionLocalOf { GanjDarkGlass }

@Composable
internal fun GanjTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    visualEffectsPolicy: GanjVisualEffectsPolicy? = null,
    content: @Composable () -> Unit,
) {
    val effectiveVisualEffectsPolicy = visualEffectsPolicy ?: currentGanjVisualEffectsPolicy()
    CompositionLocalProvider(
        LocalGanjGlassPalette provides if (darkTheme) GanjDarkGlass else GanjLightGlass,
        LocalGanjVisualEffectsPolicy provides effectiveVisualEffectsPolicy,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) GanjDarkColors else GanjLightColors,
            typography = GanjTypography,
            content = content,
        )
    }
}
