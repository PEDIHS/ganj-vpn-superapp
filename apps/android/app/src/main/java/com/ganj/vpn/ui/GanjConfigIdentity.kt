package com.ganj.vpn.ui

import java.util.Locale

/** Presentation-only model; connection IDs and raw subscription data never change. */
internal data class GanjConfigIdentity(val title: String, val flag: String)

internal fun ganjConfigIdentity(rawName: String, countryCode: String?): GanjConfigIdentity {
    var start = -1
    var end = -1
    var flag: String? = null
    var i = 0
    while (i < rawName.length) {
        val first = Character.codePointAt(rawName, i)
        val next = i + Character.charCount(first)
        if (first in 0x1F1E6..0x1F1FF && next < rawName.length) {
            val second = Character.codePointAt(rawName, next)
            if (second in 0x1F1E6..0x1F1FF) {
                start = i
                end = next + Character.charCount(second)
                flag = rawName.substring(start, end)
                break
            }
        }
        i = next
    }
    val cleaned = if (start >= 0) rawName.removeRange(start, end) else rawName
    val title = cleaned.replace(Regex("\\s+"), " ")
        .trim(' ', '|', '•', '·', '—', '-', ':', '،')
        .ifBlank { rawName.ifBlank { "سرور" } }
    return GanjConfigIdentity(title, flag ?: ganjCountryFlag(countryCode))
}

internal fun ganjCountryFlag(countryCode: String?): String {
    val code = countryCode?.trim()?.uppercase(Locale.ROOT)?.let {
        if (it == "UK") "GB" else it
    }
    if (code == null || code.length != 2 || code.any { it !in 'A'..'Z' }) return "🌐"
    return buildString { code.forEach { appendCodePoint(0x1F1E6 + (it - 'A')) } }
}