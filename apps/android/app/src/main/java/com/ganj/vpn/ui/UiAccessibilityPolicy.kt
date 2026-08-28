package com.ganj.vpn.ui

internal object UiAccessibilityPolicy {
    const val MinimumTouchTargetDp = 48

    fun destinationDescription(
        label: String,
        selected: Boolean,
        selectedSuffix: String,
    ): String = if (selected) {
        "$label، $selectedSuffix"
    } else {
        label
    }

    fun connectionStateDescription(
        connected: Boolean,
        busy: Boolean,
        hasActiveService: Boolean,
        connectedText: String,
        busyText: String,
        readyText: String,
        unavailableText: String,
    ): String = when {
        connected -> connectedText
        busy -> busyText
        hasActiveService -> readyText
        else -> unavailableText
    }
}
