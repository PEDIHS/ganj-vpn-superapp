package com.ganj.vpn.ui

internal object UiAccessibilityPolicy {
    const val MinimumTouchTargetDp = 48

    fun destinationDescription(label: String, selected: Boolean): String = if (selected) {
        "$label، انتخاب‌شده"
    } else {
        label
    }

    fun connectionStateDescription(
        connected: Boolean,
        busy: Boolean,
        hasActiveService: Boolean,
    ): String = when {
        connected -> "وی‌پی‌ان متصل است؛ برای قطع اتصال دو بار ضربه بزنید"
        busy -> "در حال آماده‌سازی اتصال امن"
        hasActiveService -> "آماده اتصال؛ برای اتصال دو بار ضربه بزنید"
        else -> "اتصال دردسترس نیست؛ ابتدا یک سرویس فعال انتخاب کنید"
    }
}
