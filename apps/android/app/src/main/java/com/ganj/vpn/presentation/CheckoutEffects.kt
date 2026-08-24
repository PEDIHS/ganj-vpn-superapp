package com.ganj.vpn.presentation

import com.ganj.vpn.core.billing.ProviderClientAction
import com.ganj.vpn.core.playbilling.PlayBillingLaunchResult

fun interface GooglePlayActionLauncher {
    suspend fun launch(action: ProviderClientAction.LaunchGooglePlay): PlayBillingLaunchResult
}

sealed interface CheckoutEffectResult {
    data object Launched : CheckoutEffectResult
    data object AwaitingReconciliation : CheckoutEffectResult
    data object MissingOrConsumed : CheckoutEffectResult
    data class Failed(val failure: UiFailure) : CheckoutEffectResult
}

/** Consumes a provider action exactly once before crossing into the Activity-owned Play UI. */
class GooglePlayCheckoutEffectExecutor(
    private val actionVault: CheckoutActionVault,
    private val mapper: GanjPresentationMapper = GanjPresentationMapper(),
) {
    suspend fun execute(
        handle: CheckoutActionHandle,
        launcher: GooglePlayActionLauncher,
    ): CheckoutEffectResult {
        val action = actionVault.consume(handle) ?: return CheckoutEffectResult.MissingOrConsumed
        return when (val result = launcher.launch(action)) {
            PlayBillingLaunchResult.Launched -> CheckoutEffectResult.Launched
            is PlayBillingLaunchResult.AlreadyOwned -> CheckoutEffectResult.AwaitingReconciliation
            is PlayBillingLaunchResult.Rejected -> CheckoutEffectResult.Failed(
                mapper.billingFailure(result.failure),
            )
        }
    }
}
