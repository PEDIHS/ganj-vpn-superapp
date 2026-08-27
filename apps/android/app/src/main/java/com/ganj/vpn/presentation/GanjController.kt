package com.ganj.vpn.presentation

import com.ganj.vpn.core.billing.BillingGatewayRegistry
import com.ganj.vpn.core.billing.BillingIdempotencyKey
import com.ganj.vpn.core.billing.BillingOperationId
import com.ganj.vpn.core.billing.BillingProvider
import com.ganj.vpn.core.billing.GatewayCheckoutRequest
import com.ganj.vpn.core.billing.GatewayCheckoutResult
import com.ganj.vpn.core.billing.ProviderClientAction
import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.CheckoutCommand
import com.ganj.vpn.core.controlapi.ConnectionProfileCommand
import com.ganj.vpn.core.controlapi.ControlApiRepository
import com.ganj.vpn.core.controlapi.OrderStatus
import com.ganj.vpn.core.controlapi.ProfileProvisioningBinding
import com.ganj.vpn.core.controlapi.PurchaseChannel
import com.ganj.vpn.core.playbilling.PlayPurchaseEvent
import java.util.UUID

fun interface StableIdGenerator {
    fun next(): String
}

fun interface CurrentUserIdProvider {
    fun currentUserId(): String?
}

data class ConnectionProfileContext(
    val deviceId: String,
    val serverId: String,
    val clientNonce: String,
    val deviceProof: String,
)

fun interface ConnectionProfileContextProvider {
    fun forEntitlement(entitlementId: String): ConnectionProfileContext?
}

fun interface AuthenticatedCheckoutSession {
    fun isReady(): Boolean
}

interface CheckoutActionVault {
    fun store(action: ProviderClientAction.LaunchGooglePlay): CheckoutActionHandle
    fun consume(handle: CheckoutActionHandle): ProviderClientAction.LaunchGooglePlay?
    fun clear()
}

sealed interface BillingStartResult {
    data class Pending(val action: CheckoutSafeAction) : BillingStartResult
    data object AuthRequired : BillingStartResult
    data class Rejected(val failure: UiFailure) : BillingStartResult
}

interface CheckoutBillingCoordinator {
    suspend fun begin(
        plan: PlanUiModel,
        idempotencyKey: String,
    ): BillingStartResult
}

class CoreBillingCheckoutCoordinator(
    private val gateways: BillingGatewayRegistry,
    private val currentUser: CurrentUserIdProvider,
    private val ids: StableIdGenerator,
    private val mapper: GanjPresentationMapper,
    private val actionVault: CheckoutActionVault,
) : CheckoutBillingCoordinator {
    override suspend fun begin(
        plan: PlanUiModel,
        idempotencyKey: String,
    ): BillingStartResult {
        val userId = currentUser.currentUserId()?.takeIf(String::isNotBlank)
            ?: return BillingStartResult.AuthRequired
        val gateway = gateways.gateway(BillingProvider.GOOGLE_PLAY)
            ?: return BillingStartResult.Rejected(
                UiFailure(UiFailureKind.BILLING, "billing.provider_unavailable", retryable = false),
            )
        val request = GatewayCheckoutRequest(
            operationId = BillingOperationId(ids.next()),
            userId = userId,
            productId = plan.code,
            offerId = null,
            idempotencyKey = BillingIdempotencyKey(idempotencyKey),
        )
        return when (val result = gateway.prepareCheckout(request)) {
            is GatewayCheckoutResult.UserActionRequired -> when (val action = result.action) {
                is ProviderClientAction.LaunchGooglePlay -> BillingStartResult.Pending(
                    CheckoutSafeAction.LaunchGooglePlay(actionVault.store(action)),
                )
                else -> BillingStartResult.Rejected(
                    UiFailure(UiFailureKind.BILLING, "billing.provider_unavailable", retryable = false),
                )
            }
            is GatewayCheckoutResult.Pending -> BillingStartResult.Pending(CheckoutSafeAction.WaitForProvider)
            is GatewayCheckoutResult.Rejected -> BillingStartResult.Rejected(mapper.billingFailure(result.failure))
        }
    }
}

class GanjController(
    private val repository: ControlApiRepository,
    private val billing: CheckoutBillingCoordinator,
    private val checkoutSession: AuthenticatedCheckoutSession,
    private val currentUser: CurrentUserIdProvider,
    private val connectionContext: ConnectionProfileContextProvider,
    private val connectionActions: ConnectionActionVault,
    private val mapper: GanjPresentationMapper = GanjPresentationMapper(),
    private val reducer: GanjUiReducer = GanjUiReducer(),
    private val ids: StableIdGenerator = StableIdGenerator { UUID.randomUUID().toString() },
) {
    fun refresh(state: GanjUiState): GanjUiState {
        var next = reducer.reduce(state, GanjUiEvent.CatalogResolved(mapper.catalog(repository.catalog(PurchaseChannel.PLAY))))
        next = reducer.reduce(next, GanjUiEvent.ServicesResolved(mapper.services(repository.myServices())))
        return next
    }

    suspend fun checkout(
        state: GanjUiState,
        planId: String,
    ): GanjUiState {
        val working = reducer.reduce(state, GanjUiEvent.CheckoutRequested(planId))
        val plan = working.plans.firstOrNull { it.id == planId }
            ?: return reducer.reduce(
                working,
                GanjUiEvent.CheckoutRejected(
                    planId,
                    UiFailure(UiFailureKind.PROTOCOL, "checkout.plan_unavailable", false),
                ),
            )
        if (!checkoutSession.isReady()) {
            return reducer.reduce(working, GanjUiEvent.CheckoutAuthenticationRequired)
        }
        val key = ids.next()
        return when (
            val order = repository.checkout(
                CheckoutCommand(
                    planId = plan.id,
                    channel = PurchaseChannel.PLAY,
                    idempotencyKey = key,
                ),
            )
        ) {
            is ApiResult.Failure -> reduceCheckoutFailure(working, plan.id, order.error)
            is ApiResult.Success -> {
                val entitlementServiceId = order.value.entitlementServiceId
                if (order.value.status == OrderStatus.FULFILLED && entitlementServiceId != null) {
                    activateVerifiedOrder(working, plan.id, order.value.id, entitlementServiceId)
                } else {
                    when (val billingResult = billing.begin(plan, key)) {
                        BillingStartResult.AuthRequired -> reducer.reduce(working, GanjUiEvent.CheckoutAuthenticationRequired)
                        is BillingStartResult.Rejected -> reducer.reduce(
                            working,
                            GanjUiEvent.CheckoutRejected(plan.id, billingResult.failure),
                        )
                        is BillingStartResult.Pending -> reducer.reduce(
                            working,
                            GanjUiEvent.CheckoutPending(plan.id, order.value.id, billingResult.action),
                        )
                    }
                }
            }
        }
    }

    fun onPlayPurchaseEvent(state: GanjUiState, event: PlayPurchaseEvent): GanjUiState {
        val pending = state.checkout as? CheckoutUiState.Pending ?: return state
        return when (event) {
            PlayPurchaseEvent.UserCancelledFlow -> reducer.reduce(
                state,
                GanjUiEvent.CheckoutRejected(
                    pending.planId,
                    UiFailure(UiFailureKind.BILLING, "billing.play.user_cancelled", true),
                ),
            )
            is PlayPurchaseEvent.Failed -> reducer.reduce(
                state,
                GanjUiEvent.CheckoutRejected(pending.planId, mapper.billingFailure(event.failure)),
            )
            is PlayPurchaseEvent.Observed -> reducer.reduce(
                state,
                GanjUiEvent.CheckoutPending(
                    planId = pending.planId,
                    orderId = pending.orderId ?: return state,
                    action = CheckoutSafeAction.WaitForProvider,
                ),
            )
        }
    }

    fun onCheckoutEffectResult(
        state: GanjUiState,
        planId: String,
        result: CheckoutEffectResult,
    ): GanjUiState = when (result) {
        CheckoutEffectResult.Launched,
        CheckoutEffectResult.AwaitingReconciliation,
        -> reducer.reduce(state, GanjUiEvent.CheckoutEffectConsumed(planId))
        CheckoutEffectResult.MissingOrConsumed -> reducer.reduce(
            state,
            GanjUiEvent.CheckoutRejected(
                planId,
                UiFailure(UiFailureKind.CONFLICT, "billing.play.action_expired", retryable = true),
            ),
        )
        is CheckoutEffectResult.Failed -> reducer.reduce(
            state,
            GanjUiEvent.CheckoutRejected(planId, result.failure),
        )
    }

    fun prepareConnection(state: GanjUiState, entitlementId: String): GanjUiState {
        val working = reducer.reduce(state, GanjUiEvent.ConnectionRequested(entitlementId))
        val service = working.serviceItems.firstOrNull { it.entitlementId == entitlementId && it.isActive }
            ?: return reducer.reduce(
                working,
                GanjUiEvent.ConnectionRejected(
                    entitlementId,
                    UiFailure(UiFailureKind.ENTITLEMENT, "connection.service_inactive", false),
                ),
            )
        val context = connectionContext.forEntitlement(service.entitlementId)
            ?: return reducer.reduce(
                working,
                GanjUiEvent.ConnectionRejected(
                    entitlementId,
                    UiFailure(UiFailureKind.CONFIGURATION, "connection.context_unavailable", true),
                ),
            )
        val userId = currentUser.currentUserId()?.takeIf(String::isNotBlank)
            ?: return reducer.reduce(working, GanjUiEvent.ConnectionAuthenticationRequired)
        return when (
            val profile = repository.prepareConnection(
                ConnectionProfileCommand(
                    serviceId = service.entitlementId,
                    deviceId = context.deviceId,
                    serverId = context.serverId,
                    clientNonce = context.clientNonce,
                    deviceProof = context.deviceProof,
                ),
            )
        ) {
            is ApiResult.Failure -> if (profile.error is ApiError.AuthenticationRequired || profile.error is ApiError.AuthenticationExpired) {
                reducer.reduce(working, GanjUiEvent.ConnectionAuthenticationRequired)
            } else {
                reducer.reduce(
                    working,
                    GanjUiEvent.ConnectionRejected(entitlementId, mapper.apiFailure(profile.error)),
                )
            }
            is ApiResult.Success -> {
                val binding = ProfileProvisioningBinding(
                    profileId = profile.value.profileId,
                    userId = userId,
                    serviceId = service.entitlementId,
                    deviceId = context.deviceId,
                    serverId = profile.value.serverId,
                    expiresAt = profile.value.expiresAt,
                )
                val handle = connectionActions.store(profile.value, binding)
                reducer.reduce(
                    working,
                    GanjUiEvent.ConnectionProfileReady(
                        entitlementId = entitlementId,
                        profileId = profile.value.profileId,
                        expiresAt = profile.value.expiresAt,
                        action = ConnectionSafeAction.StartTunnel(handle),
                    ),
                )
            }
        }
    }

    fun onConnectionEffectResult(
        state: GanjUiState,
        entitlementId: String,
        result: ConnectionEffectResult,
    ): GanjUiState = when (result) {
        is ConnectionEffectResult.Connected -> reducer.reduce(
            state,
            GanjUiEvent.ConnectionEstablished(entitlementId, result.profileId, result.serverId),
        )
        is ConnectionEffectResult.Failed -> reducer.reduce(
            state,
            GanjUiEvent.ConnectionRejected(entitlementId, result.failure),
        )
    }

    fun onDisconnectResult(state: GanjUiState, result: Result<Unit>): GanjUiState =
        if (result.isSuccess) {
            reducer.reduce(state, GanjUiEvent.ClearConnection)
        } else {
            reducer.reduce(
                state,
                GanjUiEvent.ConnectionRejected(
                    state.selectedEntitlementId,
                    UiFailure(UiFailureKind.SERVER, "connection.disconnect_failed", retryable = true),
                ),
            )
        }

    private fun activateVerifiedOrder(
        state: GanjUiState,
        planId: String,
        orderId: String,
        entitlementId: String,
    ): GanjUiState {
        var next = reducer.reduce(state, GanjUiEvent.CheckoutVerified(planId, orderId))
        next = reducer.reduce(next, GanjUiEvent.ServicesResolved(mapper.services(repository.myServices())))
        return if (next.serviceItems.any { it.entitlementId == entitlementId && it.isActive }) {
            reducer.reduce(next, GanjUiEvent.CheckoutActivated(planId, entitlementId))
        } else {
            next
        }
    }

    private fun reduceCheckoutFailure(state: GanjUiState, planId: String, error: ApiError): GanjUiState =
        if (error is ApiError.AuthenticationRequired || error is ApiError.AuthenticationExpired) {
            reducer.reduce(state, GanjUiEvent.CheckoutAuthenticationRequired)
        } else {
            reducer.reduce(state, GanjUiEvent.CheckoutRejected(planId, mapper.apiFailure(error)))
        }

}
