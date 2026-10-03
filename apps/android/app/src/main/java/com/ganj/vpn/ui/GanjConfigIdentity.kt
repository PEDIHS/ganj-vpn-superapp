package com.ganj.vpn.ui

import java.util.Locale

/** Presentation-only metadata. Server IDs and unmodified connection data remain authoritative. */
internal data class GanjConfigIdentity(val title: String, val countryCode: String?)

internal fun ganjConfigIdentity(rawName: String, countryCode: String?): GanjConfigIdentity {
    var start = -1
    var end = -1
    var detectedCode: String? = null
    var i = 0
    while (i < rawName.length) {
        val first = Character.codePointAt(rawName, i)
        val next = i + Character.charCount(first)
        if (first in 0x1F1E6..0x1F1FF && next < rawName.length) {
            val second = Character.codePointAt(rawName, next)
            if (second in 0x1F1E6..0x1F1FF) {
                start = i
                end = next + Character.charCount(second)
                detectedCode = buildString {
                    append('A' + (first - 0x1F1E6))
                    append('A' + (second - 0x1F1E6))
                }
                break
            }
        }
        i = next
    }
    val cleaned = if (start >= 0) rawName.removeRange(start, end) else rawName
    val title = cleaned.replace(Regex("\\s+"), " ")
        .trim(' ', '|', '•', '·', '—', '-', ':', '،')
        .ifBlank { "سرور" }
    return GanjConfigIdentity(title, ganjNormalizedCountry(detectedCode ?: countryCode))
}

/** Do not synthesize emoji flags. Null is displayed as an in-app vector globe. */
internal fun ganjNormalizedCountry(countryCode: String?): String? {
    val code = countryCode?.trim()?.uppercase(Locale.ROOT) ?: return null
    val normalized = if (code == "UK") "GB" else code
    return normalized.takeIf { it.length == 2 && it.all { char -> char in 'A'..'Z' } }
}
