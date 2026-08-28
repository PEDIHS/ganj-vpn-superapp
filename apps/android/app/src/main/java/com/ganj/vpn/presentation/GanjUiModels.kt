package com.ganj.vpn.presentation

sealed interface ContentState<out T> {
    data object Loading : ContentState<Nothing>
    data object Empty : ContentState<Nothing>
    data object AuthRequired : ContentState<Nothing>
    data class Ready<T>(val items: List<T>) : ContentState<T> {
        init {
            require(items.isNotEmpty())
        }
    }
    data class Error(val failure: UiFailure) : ContentState<Nothing>
}

enum class UiFailureKind {
    AUTHENTICATION,
    ENTITLEMENT,
    CONFLICT,
    RATE_LIMIT,
    NETWORK,
    SERVER,
    CONFIGURATION,
    PROTOCOL,
    BILLING,
}

data class UiFailure(
    val kind: UiFailureKind,
    val messageKey: String,
    val retryable: Boolean,
    val requestId: String? = null,
)

enum class UiTier { FREE, PREMIUM, VIP }

data class PlanUiModel(
    val id: String,
    val code: String,
    val title: String,
    val tier: UiTier,
    val durationDays: Int?,
    val trafficLimitBytes: Long?,
    val deviceLimit: Int,
    val benefits: List<String>,
    val amountMinor: Long,
    val currency: String,
)

enum class ServiceUiStatus { PENDING, ACTIVE, DISABLED, EXPIRED, REVOKED }

data class ServiceUiModel(
    val entitlementId: String,
    val displayName: String,
    val status: ServiceUiStatus,
    val tier: UiTier,
    val countryCode: String?,
    val trafficLimitBytes: Long?,
    val trafficUsedBytes: Long,
    val expiresAt: String?,
    val deviceLimit: Int,
    val allowedProtocols: Set<String>,
) {
    val isActive: Boolean get() = status == ServiceUiStatus.ACTIVE
    val remainingBytes: Long?
        get() = trafficLimitBytes?.let { (it - trafficUsedBytes).coerceAtLeast(0) }

    fun canUse(server: ServerUiModel): Boolean =
        isActive &&
            tier.rank() >= server.tier.rank() &&
            (countryCode == null || countryCode == server.countryCode) &&
            allowedProtocols.any(server.protocols::contains)
}

enum class ServerUiStatus { ACTIVE, BUSY, MAINTENANCE }

data class ServerUiModel(
    val id: String,
    val code: String,
    val displayName: String,
    val countryCode: String,
    val city: String?,
    val tier: UiTier,
    val status: ServerUiStatus,
    val loadRatio: Double,
    val latencyHintMs: Int?,
    val favorite: Boolean,
    val protocols: Set<String>,
) {
    val isSelectable: Boolean get() = status == ServerUiStatus.ACTIVE
    val loadPercent: Int get() = (loadRatio * 100.0).toInt().coerceIn(0, 100)
}

sealed interface CheckoutUiState {
    data object Idle : CheckoutUiState
    data class Pending(
        val planId: String,
        val orderId: String? = null,
        val action: CheckoutSafeAction? = null,
    ) : CheckoutUiState
    data class Verified(val planId: String, val orderId: String) : CheckoutUiState
    data class Active(val planId: String, val entitlementId: String) : CheckoutUiState
    data object AuthRequired : CheckoutUiState
    data class Failed(val planId: String?, val failure: UiFailure) : CheckoutUiState
}

@JvmInline
value class CheckoutActionHandle(val value: String) {
    init {
        require(value.matches(Regex("^gp_action_[a-f0-9]{32}$")))
    }

    override fun toString(): String = "CheckoutActionHandle([OPAQUE])"
}

sealed interface CheckoutSafeAction {
    data class LaunchGooglePlay(val handle: CheckoutActionHandle) : CheckoutSafeAction
    data object WaitForProvider : CheckoutSafeAction
}

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data class Requesting(val entitlementId: String) : ConnectionUiState
    data class ProfileReady(
        val entitlementId: String,
        val profileId: String,
        val expiresAt: String,
        val action: ConnectionSafeAction,
    ) : ConnectionUiState
    data class Connected(
        val entitlementId: String,
        val profileId: String,
        val serverId: String,
    ) : ConnectionUiState
    data object AuthRequired : ConnectionUiState
    data class Failed(val entitlementId: String?, val failure: UiFailure) : ConnectionUiState
}

data class GanjUiState(
    val catalog: ContentState<PlanUiModel> = ContentState.Loading,
    val services: ContentState<ServiceUiModel> = ContentState.Loading,
    val servers: ContentState<ServerUiModel> = ContentState.Loading,
    val selectedPlanId: String? = null,
    val selectedEntitlementId: String? = null,
    val selectedServerId: String? = null,
    val checkout: CheckoutUiState = CheckoutUiState.Idle,
    val connection: ConnectionUiState = ConnectionUiState.Idle,
    val refreshInProgress: Boolean = false,
) {
    val plans: List<PlanUiModel>
        get() = (catalog as? ContentState.Ready<PlanUiModel>)?.items.orEmpty()

    val serviceItems: List<ServiceUiModel>
        get() = (services as? ContentState.Ready<ServiceUiModel>)?.items.orEmpty()

    val serverItems: List<ServerUiModel>
        get() = (servers as? ContentState.Ready<ServerUiModel>)?.items.orEmpty()

    val selectedService: ServiceUiModel?
        get() = serviceItems.firstOrNull { it.entitlementId == selectedEntitlementId }

    val selectedServer: ServerUiModel?
        get() = serverItems.firstOrNull { it.id == selectedServerId }

    val selectedPlan: PlanUiModel?
        get() = plans.firstOrNull { it.id == selectedPlanId }

    fun compatibleServiceFor(server: ServerUiModel): ServiceUiModel? {
        selectedService?.takeIf { it.canUse(server) }?.let { return it }
        return serviceItems
            .asSequence()
            .filter { it.canUse(server) }
            .sortedWith(
                compareByDescending<ServiceUiModel> { it.tier.rank() }
                    .thenByDescending { it.remainingBytes ?: Long.MAX_VALUE },
            )
            .firstOrNull()
    }

    fun compatibleServerFor(service: ServiceUiModel): ServerUiModel? {
        selectedServer?.takeIf { it.isSelectable && service.canUse(it) }?.let { return it }
        return serverItems
            .asSequence()
            .filter { it.isSelectable && service.canUse(it) }
            .sortedWith(
                compareByDescending<ServerUiModel> { it.favorite }
                    .thenBy { it.latencyHintMs ?: Int.MAX_VALUE }
                    .thenBy { it.loadRatio },
            )
            .firstOrNull()
    }
}

private fun UiTier.rank(): Int = when (this) {
    UiTier.FREE -> 0
    UiTier.PREMIUM -> 1
    UiTier.VIP -> 2
}
