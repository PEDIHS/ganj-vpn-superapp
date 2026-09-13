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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GanjEmerald = Color(0xFF1DA15D)
internal val GanjEmeraldBright = Color(0xFF52DF9C)
internal val GanjEmeraldDeep = Color(0xFF063E2F)
internal val GanjGold = Color(0xFFD5A63A)
internal val GanjGoldBright = Color(0xFFF0CD70)
internal val GanjJade = Color(0xFF3AA58D)
internal val GanjDanger = Color(0xFFD94A4A)
internal val GanjWarning = Color(0xFFE0A33C)

internal val GanjDarkCanvas = Color(0xFF07110D)
internal val GanjDarkSurface = Color(0xFF0B1812)
internal val GanjDarkSurfaceSecondary = Color(0xFF10231A)
internal val GanjDarkText = Color(0xFFF5F8F6)
internal val GanjDarkMuted = Color(0xFFA9BBB2)

internal val GanjLightCanvas = Color(0xFFF7F8F5)
internal val GanjLightSurface = Color(0xFFFFFFFF)
internal val GanjLightSurfaceSecondary = Color(0xFFECFDF5)
internal val GanjLightText = Color(0xFF0F1E19)
internal val GanjLightMuted = Color(0xFF5F6E67)

private val GanjLightColors = lightColorScheme(
    primary = Color(0xFF005133),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF006C45),
    onPrimaryContainer = Color(0xFF92EAB9),
    secondary = Color(0xFF7C5800),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFEC659),
    onSecondaryContainer = Color(0xFF5E4200),
    tertiary = Color(0xFF005132),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFF006C44),
    onTertiaryContainer = Color(0xFF7AEEB0),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    background = GanjLightCanvas,
    onBackground = GanjLightText,
    surface = GanjLightSurface,
    onSurface = GanjLightText,
    surfaceVariant = GanjLightSurfaceSecondary,
    onSurfaceVariant = GanjLightMuted,
    outline = Color(0xFF6F7A71),
)

private val GanjDarkColors = darkColorScheme(
    primary = Color(0xFF52DF9C),
    onPrimary = Color(0xFF002112),
    primaryContainer = Color(0xFF005133),
    onPrimaryContainer = Color(0xFF92EAB9),
    secondary = GanjGold,
    onSecondary = Color(0xFF271900),
    secondaryContainer = Color(0xFF5E4200),
    onSecondaryContainer = Color(0xFFFFDEA7),
    tertiary = Color(0xFF68DCA0),
    onTertiary = Color(0xFF002111),
    tertiaryContainer = Color(0xFF005232),
    onTertiaryContainer = Color(0xFF85F9BA),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    background = GanjDarkCanvas,
    onBackground = GanjDarkText,
    surface = GanjDarkSurface,
    onSurface = GanjDarkText,
    surfaceVariant = GanjDarkSurfaceSecondary,
    onSurfaceVariant = GanjDarkMuted,
    outline = Color(0xFF6F7A71),
)

private val GanjTypography = Typography().run {
    copy(
        displaySmall = displaySmall.copy(
            fontSize = 34.sp,
            lineHeight = 48.sp,
            letterSpacing = 0.sp,
        ),
        headlineLarge = headlineLarge.copy(
            fontSize = 30.sp,
            lineHeight = 42.sp,
            letterSpacing = 0.sp,
        ),
        headlineMedium = headlineMedium.copy(
            fontSize = 26.sp,
            lineHeight = 38.sp,
            letterSpacing = 0.sp,
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = 23.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
        ),
        titleLarge = titleLarge.copy(
            fontSize = 21.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 17.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
        ),
        titleSmall = titleSmall.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 27.sp,
            letterSpacing = 0.sp,
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
        ),
        bodySmall = bodySmall.copy(
            fontSize = 13.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.sp,
        ),
        labelLarge = labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.sp,
        ),
        labelMedium = labelMedium.copy(
            fontSize = 12.sp,
            lineHeight = 20.sp,
            letterSpacing = 0.sp,
        ),
        labelSmall = labelSmall.copy(
            fontSize = 11.sp,
            lineHeight = 18.sp,
            letterSpacing = 0.sp,
        ),
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
    emeraldTint = Color(0xFF52DF9C),
    goldTint = GanjGold,
    borderSoft = Color(0xFF405249),
    borderStrong = Color(0xFF718074),
    highlight = Color(0xFFF7FFF9),
    scrim = Color(0xFF050806),
    opaqueFallback = Color(0xFF101A14),
    clearBlur = 14.dp,
    regularBlur = 22.dp,
    denseBlur = 30.dp,
)

private val GanjLightGlass = GanjGlassPalette(
    neutralTint = Color(0xFFF9FCF8),
    emeraldTint = Color(0xFF005133),
    goldTint = Color(0xFF7C5800),
    borderSoft = Color(0xFFD6DED7),
    borderStrong = Color(0xFFBAC5BC),
    highlight = Color.White,
    scrim = Color(0xFFEFF2ED),
    opaqueFallback = Color(0xFFF3F7F4),
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
        LocalLayoutDirection provides LayoutDirection.Rtl,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) GanjDarkColors else GanjLightColors,
            typography = GanjTypography,
            content = content,
        )
    }
}
