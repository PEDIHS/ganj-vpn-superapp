package com.ganj.vpn.ui

internal enum class GanjWidthClass {
    Compact,
    Regular,
    Wide,
}

internal object GanjResponsivePolicy {
    const val CompactWidthMaxDp = 359
    const val WideWidthMinDp = 600
    const val LargeFontScale = 1.30f

    fun widthClass(widthDp: Int): GanjWidthClass = when {
        widthDp <= CompactWidthMaxDp -> GanjWidthClass.Compact
        widthDp >= WideWidthMinDp -> GanjWidthClass.Wide
        else -> GanjWidthClass.Regular
    }

    fun shouldStackPrimaryActions(widthDp: Int, fontScale: Float): Boolean =
        widthClass(widthDp) == GanjWidthClass.Compact || fontScale >= LargeFontScale

    fun shouldShowAllNavigationLabels(widthDp: Int, fontScale: Float): Boolean =
        widthDp >= 360 && fontScale < LargeFontScale

    fun categoryColumns(widthDp: Int, fontScale: Float): Int =
        if (shouldStackPrimaryActions(widthDp, fontScale)) 1 else 2

    fun horizontalPagePaddingDp(widthDp: Int): Int = when (widthClass(widthDp)) {
        GanjWidthClass.Compact -> 16
        GanjWidthClass.Regular -> 20
        GanjWidthClass.Wide -> 28
    }
}
