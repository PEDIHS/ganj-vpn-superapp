package com.ganj.vpn.core.subscription

enum class PurchaseStatus {
    IDLE,
    CHECKOUT,
    VERIFYING,
    SUCCEEDED,
    FAILED,
}

enum class PurchaseFailure {
    PRODUCT_UNAVAILABLE,
    VERIFICATION_REJECTED,
    PAYMENT_FAILED,
    NETWORK_ERROR,
}

sealed interface ConnectionUiState {
    data object Idle : ConnectionUiState
    data class Requested(val command: SubscriptionConnectionCommand) : ConnectionUiState
    data class Connected(val command: SubscriptionConnectionCommand) : ConnectionUiState
    data class Blocked(val reason: ConnectionDenial) : ConnectionUiState
}

data class SubscriptionStoreState(
    val currentUserId: String = "",
    val products: List<SubscriptionProduct> = emptyList(),
    val services: List<UserService> = emptyList(),
    val selectedProductId: String? = null,
    val selectedServiceId: String? = null,
    val purchaseStatus: PurchaseStatus = PurchaseStatus.IDLE,
    val purchaseFailure: PurchaseFailure? = null,
    val connection: ConnectionUiState = ConnectionUiState.Idle,
) {
    val selectedProduct: SubscriptionProduct?
        get() = products.firstOrNull { it.id == selectedProductId }

    val selectedService: UserService?
        get() = services.firstOrNull { it.id == selectedServiceId }
}

sealed interface SubscriptionStoreAction {
    data class SelectProduct(val productId: String) : SubscriptionStoreAction
    data class BeginCheckout(val productId: String) : SubscriptionStoreAction
    data object VerifyPurchase : SubscriptionStoreAction
    data class CompletePurchase(val service: UserService) : SubscriptionStoreAction
    data class FailPurchase(val reason: PurchaseFailure) : SubscriptionStoreAction
    data class SelectService(val serviceId: String) : SubscriptionStoreAction
    data class RequestConnection(
        val serverId: String,
        val nowEpochMillis: Long,
    ) : SubscriptionStoreAction
    data object ConnectionEstablished : SubscriptionStoreAction
    data object Disconnect : SubscriptionStoreAction
    data object ClearPurchaseResult : SubscriptionStoreAction
}

class SubscriptionStoreReducer(
    private val connectionPolicy: SubscriptionConnectionPolicy = SubscriptionConnectionPolicy(),
) {
    fun reduce(
        state: SubscriptionStoreState,
        action: SubscriptionStoreAction,
    ): SubscriptionStoreState = when (action) {
        is SubscriptionStoreAction.SelectProduct -> {
            if (state.products.any { it.id == action.productId }) {
                state.copy(selectedProductId = action.productId)
            } else {
                state
            }
        }

        is SubscriptionStoreAction.BeginCheckout -> {
            if (state.products.any { it.id == action.productId }) {
                state.copy(
                    selectedProductId = action.productId,
                    purchaseStatus = PurchaseStatus.CHECKOUT,
                    purchaseFailure = null,
                )
            } else {
                state.copy(
                    purchaseStatus = PurchaseStatus.FAILED,
                    purchaseFailure = PurchaseFailure.PRODUCT_UNAVAILABLE,
                )
            }
        }

        SubscriptionStoreAction.VerifyPurchase -> {
            if (state.purchaseStatus == PurchaseStatus.CHECKOUT) {
                state.copy(purchaseStatus = PurchaseStatus.VERIFYING)
            } else {
                state
            }
        }

        is SubscriptionStoreAction.CompletePurchase -> {
            if (
                state.purchaseStatus == PurchaseStatus.VERIFYING &&
                action.service.productId == state.selectedProductId &&
                action.service.ownerUserId == state.currentUserId &&
                action.service.status == UserServiceStatus.ACTIVE
            ) {
                val services = state.services.filterNot { it.id == action.service.id } + action.service
                state.copy(
                    services = services,
                    selectedServiceId = action.service.id,
                    purchaseStatus = PurchaseStatus.SUCCEEDED,
                    purchaseFailure = null,
                )
            } else {
                state.copy(
                    purchaseStatus = PurchaseStatus.FAILED,
                    purchaseFailure = PurchaseFailure.VERIFICATION_REJECTED,
                )
            }
        }

        is SubscriptionStoreAction.FailPurchase -> state.copy(
            purchaseStatus = PurchaseStatus.FAILED,
            purchaseFailure = action.reason,
        )

        is SubscriptionStoreAction.SelectService -> {
            if (state.services.any { it.id == action.serviceId }) {
                state.copy(
                    selectedServiceId = action.serviceId,
                    connection = ConnectionUiState.Idle,
                )
            } else {
                state
            }
        }

        is SubscriptionStoreAction.RequestConnection -> {
            val service = state.selectedService
            if (service == null) {
                state.copy(connection = ConnectionUiState.Blocked(ConnectionDenial.SERVICE_NOT_ACTIVE))
            } else {
                when (
                    val decision = connectionPolicy.evaluate(
                        service = service,
                        userId = state.currentUserId,
                        serverId = action.serverId,
                        nowEpochMillis = action.nowEpochMillis,
                    )
                ) {
                    is ConnectionDecision.Allowed -> state.copy(
                        connection = ConnectionUiState.Requested(decision.command),
                    )
                    is ConnectionDecision.Denied -> state.copy(
                        connection = ConnectionUiState.Blocked(decision.reason),
                    )
                }
            }
        }

        SubscriptionStoreAction.ConnectionEstablished -> {
            val requested = state.connection as? ConnectionUiState.Requested
            if (requested == null) state else state.copy(
                connection = ConnectionUiState.Connected(requested.command),
            )
        }

        SubscriptionStoreAction.Disconnect -> state.copy(connection = ConnectionUiState.Idle)

        SubscriptionStoreAction.ClearPurchaseResult -> state.copy(
            purchaseStatus = PurchaseStatus.IDLE,
            purchaseFailure = null,
        )
    }
}
