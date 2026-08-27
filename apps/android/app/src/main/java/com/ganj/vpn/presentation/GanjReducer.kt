package com.ganj.vpn.presentation

sealed interface GanjUiEvent {
    data object RefreshRequested : GanjUiEvent
    data class CatalogResolved(val content: ContentState<PlanUiModel>) : GanjUiEvent
    data class ServicesResolved(val content: ContentState<ServiceUiModel>) : GanjUiEvent
    data class SelectPlan(val planId: String) : GanjUiEvent
    data class SelectService(val entitlementId: String) : GanjUiEvent
    data class CheckoutRequested(val planId: String) : GanjUiEvent
    data class CheckoutPending(
        val planId: String,
        val orderId: String,
        val action: CheckoutSafeAction,
    ) : GanjUiEvent
    data class CheckoutEffectConsumed(val planId: String) : GanjUiEvent
    data class CheckoutVerified(val planId: String, val orderId: String) : GanjUiEvent
    data class CheckoutActivated(val planId: String, val entitlementId: String) : GanjUiEvent
    data class CheckoutRejected(val planId: String?, val failure: UiFailure) : GanjUiEvent
    data object CheckoutAuthenticationRequired : GanjUiEvent
    data class ConnectionRequested(val entitlementId: String) : GanjUiEvent
    data class ConnectionProfileReady(
        val entitlementId: String,
        val profileId: String,
        val expiresAt: String,
        val action: ConnectionSafeAction,
    ) : GanjUiEvent
    data class ConnectionEstablished(
        val entitlementId: String,
        val profileId: String,
        val serverId: String,
    ) : GanjUiEvent
    data class ConnectionRejected(val entitlementId: String?, val failure: UiFailure) : GanjUiEvent
    data object ConnectionAuthenticationRequired : GanjUiEvent
    data object ClearConnection : GanjUiEvent
}

class GanjUiReducer {
    fun reduce(state: GanjUiState, event: GanjUiEvent): GanjUiState = when (event) {
        GanjUiEvent.RefreshRequested -> state.copy(
            catalog = ContentState.Loading,
            services = ContentState.Loading,
            refreshInProgress = true,
        )
        is GanjUiEvent.CatalogResolved -> {
            val selected = state.selectedPlanId?.takeIf { id ->
                (event.content as? ContentState.Ready<PlanUiModel>)?.items?.any { it.id == id } == true
            } ?: (event.content as? ContentState.Ready<PlanUiModel>)?.items?.firstOrNull()?.id
            state.copy(catalog = event.content, selectedPlanId = selected, refreshInProgress = false)
        }
        is GanjUiEvent.ServicesResolved -> {
            val selected = state.selectedEntitlementId?.takeIf { id ->
                (event.content as? ContentState.Ready<ServiceUiModel>)?.items?.any { it.entitlementId == id && it.isActive } == true
            } ?: (event.content as? ContentState.Ready<ServiceUiModel>)?.items?.firstOrNull { it.isActive }?.entitlementId
            state.copy(services = event.content, selectedEntitlementId = selected, refreshInProgress = false)
        }
        is GanjUiEvent.SelectPlan -> if (state.plans.any { it.id == event.planId }) {
            state.copy(selectedPlanId = event.planId)
        } else state
        is GanjUiEvent.SelectService -> if (state.serviceItems.any { it.entitlementId == event.entitlementId && it.isActive }) {
            state.copy(selectedEntitlementId = event.entitlementId, connection = ConnectionUiState.Idle)
        } else state
        is GanjUiEvent.CheckoutRequested -> if (state.plans.any { it.id == event.planId }) {
            state.copy(
                selectedPlanId = event.planId,
                checkout = CheckoutUiState.Pending(event.planId),
            )
        } else state.copy(
            checkout = CheckoutUiState.Failed(
                event.planId,
                UiFailure(UiFailureKind.PROTOCOL, "checkout.plan_unavailable", retryable = false),
            ),
        )
        is GanjUiEvent.CheckoutPending -> state.copy(
            checkout = CheckoutUiState.Pending(event.planId, event.orderId, event.action),
        )
        is GanjUiEvent.CheckoutEffectConsumed -> {
            val pending = state.checkout as? CheckoutUiState.Pending
            if (pending?.planId == event.planId) {
                state.copy(checkout = pending.copy(action = CheckoutSafeAction.WaitForProvider))
            } else state
        }
        is GanjUiEvent.CheckoutVerified -> state.copy(
            checkout = CheckoutUiState.Verified(event.planId, event.orderId),
        )
        is GanjUiEvent.CheckoutActivated -> state.copy(
            checkout = CheckoutUiState.Active(event.planId, event.entitlementId),
            selectedEntitlementId = event.entitlementId,
        )
        is GanjUiEvent.CheckoutRejected -> state.copy(
            checkout = CheckoutUiState.Failed(event.planId, event.failure),
        )
        GanjUiEvent.CheckoutAuthenticationRequired -> state.copy(checkout = CheckoutUiState.AuthRequired)
        is GanjUiEvent.ConnectionRequested -> {
            val service = state.serviceItems.firstOrNull { it.entitlementId == event.entitlementId }
            if (service?.isActive == true) {
                state.copy(
                    selectedEntitlementId = event.entitlementId,
                    connection = ConnectionUiState.Requesting(event.entitlementId),
                )
            } else {
                state.copy(
                    connection = ConnectionUiState.Failed(
                        event.entitlementId,
                        UiFailure(UiFailureKind.ENTITLEMENT, "connection.service_inactive", retryable = false),
                    ),
                )
            }
        }
        is GanjUiEvent.ConnectionProfileReady -> {
            val pending = state.connection as? ConnectionUiState.Requesting
            if (pending?.entitlementId == event.entitlementId) {
                state.copy(
                    connection = ConnectionUiState.ProfileReady(
                        entitlementId = event.entitlementId,
                        profileId = event.profileId,
                        expiresAt = event.expiresAt,
                        action = event.action,
                    ),
                )
            } else state
        }
        is GanjUiEvent.ConnectionEstablished -> {
            val ready = state.connection as? ConnectionUiState.ProfileReady
            if (ready?.entitlementId == event.entitlementId && ready.profileId == event.profileId) {
                state.copy(
                    connection = ConnectionUiState.Connected(
                        event.entitlementId,
                        event.profileId,
                        event.serverId,
                    ),
                )
            } else state
        }
        is GanjUiEvent.ConnectionRejected -> state.copy(
            connection = ConnectionUiState.Failed(event.entitlementId, event.failure),
        )
        GanjUiEvent.ConnectionAuthenticationRequired -> state.copy(connection = ConnectionUiState.AuthRequired)
        GanjUiEvent.ClearConnection -> state.copy(connection = ConnectionUiState.Idle)
    }
}
