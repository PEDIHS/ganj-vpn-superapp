package com.ganj.vpn.core.observability

/**
 * Defence-in-depth redaction. Upstream collectors must still use an allowlist;
 * this class is not permission to collect arbitrary data and sanitise it later.
 */
class SensitiveDataRedactor {
    private val rules = listOf(
        Regex("(?i)\\b(?:vless|vmess|trojan|ss)://\\S+") to "[REDACTED_VPN_PROFILE]",
        Regex("(?i)\\bBearer\\s+[A-Za-z0-9._~+/-]+=*") to "Bearer [REDACTED]",
        Regex("\\beyJ[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\.[A-Za-z0-9_-]{8,}\\b") to "[REDACTED_JWT]",
        Regex("(?i)(purchase[_-]?token|access[_-]?token|refresh[_-]?token|private[_-]?key|password)\\s*[:=]\\s*[^\\s,;]+") to "\$1=[REDACTED]",
        Regex("-----BEGIN [A-Z ]*PRIVATE KEY-----[\\s\\S]{0,8192}?-----END [A-Z ]*PRIVATE KEY-----") to "[REDACTED_PRIVATE_KEY]",
    )

    fun redact(value: String, maxLength: Int = DEFAULT_MAX_LENGTH): String {
        require(maxLength in 1..ABSOLUTE_MAX_LENGTH)
        var result = value.take(maxLength)
        rules.forEach { (pattern, replacement) ->
            result = pattern.replace(result, replacement)
        }
        return result
    }

    fun containsForbiddenMaterial(value: String): Boolean {
        val redacted = redact(value, value.length.coerceIn(1, ABSOLUTE_MAX_LENGTH))
        return redacted != value.take(ABSOLUTE_MAX_LENGTH)
    }

    companion object {
        const val DEFAULT_MAX_LENGTH = 4_000
        const val ABSOLUTE_MAX_LENGTH = 16_000
    }
}
