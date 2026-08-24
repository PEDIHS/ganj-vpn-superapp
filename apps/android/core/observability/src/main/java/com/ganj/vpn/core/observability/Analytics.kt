package com.ganj.vpn.core.observability

enum class AnalyticsConsent {
    DENIED,
    GRANTED,
}

enum class ProductEventName {
    APP_OPENED,
    ONBOARDING_COMPLETED,
    FIRST_CONNECT_REQUESTED,
    VPN_CONNECTED,
    VPN_CONNECTION_FAILED,
    STORE_VIEWED,
    CHECKOUT_STARTED,
    CHECKOUT_COMPLETED,
    CHECKOUT_CANCELLED,
    SUBSCRIPTION_RESTORED,
    SUPPORT_REPORT_SUBMITTED,
}

data class ProductEvent(
    val name: ProductEventName,
    val occurredAtEpochMillis: Long,
    val properties: Map<String, String> = emptyMap(),
) {
    init {
        require(properties.size <= 12)
    }
}

interface ProductEventSink {
    fun send(event: ProductEvent)
}

class ConsentAwareAnalytics(
    private val sink: ProductEventSink,
    private val redactor: SensitiveDataRedactor = SensitiveDataRedactor(),
) {
    fun track(consent: AnalyticsConsent, event: ProductEvent): Boolean {
        if (consent != AnalyticsConsent.GRANTED) return false
        if (event.properties.keys.any { it !in ALLOWED_PROPERTIES }) return false
        if (event.properties.values.any(redactor::containsForbiddenMaterial)) return false
        if (event.properties.any { (key, value) -> value.length > MAX_VALUE_LENGTH || !isValidValue(key, value) }) {
            return false
        }
        sink.send(event)
        return true
    }

    private fun isValidValue(key: String, value: String): Boolean = when (key) {
        "plan_tier" -> value in setOf("free", "premium", "vip")
        "network_type" -> value in NetworkKind.entries.map { it.name.lowercase() }
        "result" -> value in setOf("success", "failure", "cancelled", "pending")
        else -> SAFE_VALUE.matches(value)
    }

    companion object {
        private const val MAX_VALUE_LENGTH = 80
        private val SAFE_VALUE = Regex("[A-Za-z0-9][A-Za-z0-9._:-]{0,79}")
        private val ALLOWED_PROPERTIES = setOf(
            "app_version",
            "plan_tier",
            "network_type",
            "result",
            "error_code",
            "server_region",
            "screen",
            "experiment_variant",
        )
    }
}
