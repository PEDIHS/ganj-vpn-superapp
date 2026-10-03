package com.ganj.vpn.ui

internal enum class GanjWidthClass {
    Compact,
    Regular,
    Wide,
}

internal object GanjResponsivePolicy {
    const val CompactWidthMaxDp = 359
    const val WideWidthMinDp = 600
    const val NavigationAllLabelsMinDp = 400
    const val LargeFontScale = 1.30f

    fun widthClass(widthDp: Int): GanjWidthClass = when {
        widthDp <= CompactWidthMaxDp -> GanjWidthClass.Compact
        widthDp >= WideWidthMinDp -> GanjWidthClass.Wide
        else -> GanjWidthClass.Regular
    }

    fun shouldStackPrimaryActions(widthDp: Int, fontScale: Float): Boolean =
        widthClass(widthDp) == GanjWidthClass.Compact || fontScale >= LargeFontScale

    fun shouldShowAllNavigationLabels(widthDp: Int, fontScale: Float): Boolean =
        widthDp >= NavigationAllLabelsMinDp && fontScale < LargeFontScale

    fun categoryColumns(widthDp: Int, fontScale: Float): Int =
        if (shouldStackPrimaryActions(widthDp, fontScale)) 1 else 2

    fun horizontalPagePaddingDp(widthDp: Int): Int = when (widthClass(widthDp)) {
        GanjWidthClass.Compact -> 16
        GanjWidthClass.Regular -> 20
        GanjWidthClass.Wide -> 28
    }

    /** Stitch mobile system: 18/20/22dp page gutters across the supported phone range. */
    fun stitchHorizontalPaddingDp(widthDp: Int): Int = when {
        widthDp <= 360 -> 18
        widthDp >= 412 -> 22
        else -> 20
    }

    /** Keeps the Connect control dominant without crowding 360dp devices. */
    fun stitchConnectOrbSizeDp(widthDp: Int): Int = when {
        widthDp <= 360 -> 180
        widthDp <= 400 -> 198
        else -> 212
    }

    /** Gives the floating bottom navigation additional breathing room on larger phones. */
    fun stitchNavigationHorizontalPaddingDp(widthDp: Int): Int = when {
        widthDp <= 360 -> 8
        widthDp >= 412 -> 14
        else -> 12
    }
}
