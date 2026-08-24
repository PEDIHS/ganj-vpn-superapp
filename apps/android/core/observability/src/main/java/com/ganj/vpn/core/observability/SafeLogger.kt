package com.ganj.vpn.core.observability

enum class LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR,
}

data class SafeLogEvent(
    val level: LogLevel,
    val code: String,
    val occurredAtEpochMillis: Long,
    val attributes: Map<String, String> = emptyMap(),
)

interface SafeLogSink {
    fun write(event: SafeLogEvent)
}

class AllowlistedLogger(
    private val sink: SafeLogSink,
    private val redactor: SensitiveDataRedactor = SensitiveDataRedactor(),
) {
    fun log(event: SafeLogEvent): Boolean {
        if (!SAFE_CODE.matches(event.code)) return false
        if (event.attributes.size > 10 || event.attributes.keys.any { it !in ALLOWED_ATTRIBUTES }) {
            return false
        }
        if (event.attributes.values.any { it.length > 120 || redactor.containsForbiddenMaterial(it) }) {
            return false
        }
        sink.write(event)
        return true
    }

    companion object {
        private val SAFE_CODE = Regex("[A-Z][A-Z0-9_]{2,79}")
        private val ALLOWED_ATTRIBUTES = setOf(
            "app_version",
            "operation_id",
            "error_code",
            "connection_phase",
            "network_type",
            "server_public_id",
            "retry_count",
            "module",
        )
    }
}
