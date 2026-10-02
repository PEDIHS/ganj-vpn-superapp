package com.ganj.vpn.ui

private const val LTR_ISOLATE = '\u2066'
private const val POP_DIRECTIONAL_ISOLATE = '\u2069'

private val PersianDigits = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')

/** Converts user-facing ASCII digits to Persian digits without touching other characters. */
internal fun String.toPersianDigits(): String = buildString(length) {
    for (character in this@toPersianDigits) {
        if (character in '0'..'9') {
            append(PersianDigits[character - '0'])
        } else {
            append(character)
        }
    }
}

internal fun Int.toPersianDigits(): String = toString().toPersianDigits()

internal fun Long.toPersianDigits(): String = toString().toPersianDigits()

/**
 * Isolates technical LTR values inside the Persian RTL UI.
 *
 * This prevents strings such as IPs, protocol names, `24 ms` and `256 Mbps` from being visually
 * reordered when they are placed next to Persian labels.
 */
internal fun isolateTechnicalLtr(value: String): String = if (value.isBlank()) {
    value
} else {
    "$LTR_ISOLATE$value$POP_DIRECTIONAL_ISOLATE"
}

internal fun persianTechnicalMetric(value: String, unit: String): String =
    isolateTechnicalLtr("$value $unit")
