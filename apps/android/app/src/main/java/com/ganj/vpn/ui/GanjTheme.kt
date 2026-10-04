package com.ganj.vpn.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.ganj.vpn.R
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val GanjEmerald = Color(0xFF087F68)
internal val GanjEmeraldBright = Color(0xFF52DF9C)
internal val GanjEmeraldDeep = Color(0xFF064C38)
internal val GanjGold = Color(0xFFC9992D)
internal val GanjGoldBright = Color(0xFFF1CE74)
internal val GanjJade = Color(0xFF3AA58D)
internal val GanjDanger = Color(0xFFD94A4A)
internal val GanjWarning = Color(0xFFE0A33C)

internal val GanjDarkCanvas = Color(0xFF0B1512)
internal val GanjDarkSurface = Color(0xFF14211C)
internal val GanjDarkSurfaceSecondary = Color(0xFF1D2D26)
internal val GanjDarkText = Color(0xFFF5F7F2)
internal val GanjDarkMuted = Color(0xFFB6C9C0)

internal val GanjLightCanvas = Color(0xFFF4F7F5)
internal val GanjLightSurface = Color(0xFFFFFFFF)
internal val GanjLightSurfaceSecondary = Color(0xFFEDF3EF)
internal val GanjLightText = Color(0xFF172D29)
internal val GanjLightMuted = Color(0xFF647570)

private val GanjLightColors = lightColorScheme(
    primary = Color(0xFF087F68),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFDAEEE6),
    onPrimaryContainer = Color(0xFF075747),
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
    primary = Color(0xFF78DFBD),
    onPrimary = Color(0xFF091D13),
    primaryContainer = Color(0xFF164737),
    onPrimaryContainer = Color(0xFFA5F0D3),
    secondary = GanjGoldBright,
    onSecondary = Color(0xFF271900),
    secondaryContainer = Color(0xFF4B3919),
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
    outline = Color(0xFF658273),
)

private val GanjPersianFont = FontFamily(
    Font(R.font.vazirmatn_regular, weight = FontWeight.Normal),
    Font(R.font.vazirmatn_bold, weight = FontWeight.Bold),
)

private val GanjTypography = Typography().run {
    copy(
        displayLarge = displayLarge.copy(fontFamily = GanjPersianFont, letterSpacing = 0.sp),
        displayMedium = displayMedium.copy(fontFamily = GanjPersianFont, letterSpacing = 0.sp),
        displaySmall = displaySmall.copy(
            fontSize = 34.sp,
            lineHeight = 48.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        headlineLarge = headlineLarge.copy(
            fontSize = 30.sp,
            lineHeight = 42.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        headlineMedium = headlineMedium.copy(
            fontSize = 26.sp,
            lineHeight = 38.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        headlineSmall = headlineSmall.copy(
            fontSize = 23.sp,
            lineHeight = 34.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        titleLarge = titleLarge.copy(
            fontSize = 21.sp,
            lineHeight = 32.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        titleMedium = titleMedium.copy(
            fontSize = 17.sp,
            lineHeight = 28.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        titleSmall = titleSmall.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        bodyLarge = bodyLarge.copy(
            fontSize = 16.sp,
            lineHeight = 27.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        bodyMedium = bodyMedium.copy(
            fontSize = 15.sp,
            lineHeight = 24.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        bodySmall = bodySmall.copy(
            fontSize = 14.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        labelLarge = labelLarge.copy(
            fontSize = 14.sp,
            lineHeight = 23.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        labelMedium = labelMedium.copy(
            fontSize = 13.sp,
            lineHeight = 21.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
        ),
        labelSmall = labelSmall.copy(
            fontSize = 12.sp,
            lineHeight = 19.sp,
            letterSpacing = 0.sp,
            fontFamily = GanjPersianFont,
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
    borderSoft = Color(0xFF496352),
    borderStrong = Color(0xFF8D8E6E),
    highlight = Color(0xFFF7FFF9),
    scrim = Color(0xFF050806),
    opaqueFallback = Color(0xFF17231B),
    clearBlur = 14.dp,
    regularBlur = 22.dp,
    denseBlur = 30.dp,
)

private val GanjLightGlass = GanjGlassPalette(
    neutralTint = Color(0xFFF9FCF8),
    emeraldTint = Color(0xFF087F68),
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

/** Content uses opaque, theme-aware surfaces; glass belongs to floating controls. */
@Immutable
internal data class GanjContentPalette(
    val selectedSurface: Color,
    val onSelected: Color,
    val selectedMuted: Color,
    val premiumText: Color,
)

internal val GanjDarkContent = GanjContentPalette(
    selectedSurface = Color(0xFF193E31),
    onSelected = GanjDarkText,
    selectedMuted = Color(0xFFD4E6D8),
    premiumText = GanjGoldBright,
)
internal val GanjLightContent = GanjContentPalette(
    selectedSurface = Color(0xFFE4F2E9),
    onSelected = GanjLightText,
    selectedMuted = Color(0xFF435D4D),
    premiumText = Color(0xFF76510D),
)
internal val LocalGanjContentPalette = staticCompositionLocalOf { GanjDarkContent }

@Composable
internal fun GanjTheme(
    darkTheme: Boolean = false,
    visualEffectsPolicy: GanjVisualEffectsPolicy? = null,
    content: @Composable () -> Unit,
) {
    val effectiveVisualEffectsPolicy = visualEffectsPolicy ?: currentGanjVisualEffectsPolicy()
    CompositionLocalProvider(
        LocalGanjGlassPalette provides if (darkTheme) GanjDarkGlass else GanjLightGlass,
        LocalGanjContentPalette provides if (darkTheme) GanjDarkContent else GanjLightContent,
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
