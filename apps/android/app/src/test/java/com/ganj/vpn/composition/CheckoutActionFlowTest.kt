package com.ganj.vpn.composition

import com.ganj.vpn.core.billing.BillingFailure
import com.ganj.vpn.core.billing.BillingFailureCode
import com.ganj.vpn.core.billing.BillingGateway
import com.ganj.vpn.core.billing.BillingGatewayRegistry
import com.ganj.vpn.core.billing.BillingProvider
import com.ganj.vpn.core.billing.CheckoutSessionId
import com.ganj.vpn.core.billing.GatewayCheckoutRequest
import com.ganj.vpn.core.billing.GatewayCheckoutResult
import com.ganj.vpn.core.billing.OwnedPurchasesRequest
import com.ganj.vpn.core.billing.OwnedPurchasesResult
import com.ganj.vpn.core.billing.ProviderCancellationRequest
import com.ganj.vpn.core.billing.ProviderCancellationResult
import com.ganj.vpn.core.billing.ProviderClientAction
import com.ganj.vpn.core.playbilling.PlayBillingLaunchResult
import com.ganj.vpn.presentation.BillingStartResult
import com.ganj.vpn.presentation.CheckoutActionHandle
import com.ganj.vpn.presentation.CheckoutEffectResult
import com.ganj.vpn.presentation.CoreBillingCheckoutCoordinator
import com.ganj.vpn.presentation.CurrentUserIdProvider
import com.ganj.vpn.presentation.GanjPresentationMapper
import com.ganj.vpn.presentation.GooglePlayActionLauncher
import com.ganj.vpn.presentation.GooglePlayCheckoutEffectExecutor
import com.ganj.vpn.presentation.PlanUiModel
import com.ganj.vpn.presentation.StableIdGenerator
import com.ganj.vpn.presentation.UiTier
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class CheckoutActionFlowTest {
    @Test
    fun `billing coordinator preserves real Play action only inside vault`() {
        val action = playAction()
        val vault = vault()
        var capturedRequest: GatewayCheckoutRequest? = null
        val gateway = object : BillingGateway {
            override val provider = BillingProvider.GOOGLE_PLAY
            override suspend fun prepareCheckout(request: GatewayCheckoutRequest): GatewayCheckoutResult {
                capturedRequest = request
                return GatewayCheckoutResult.UserActionRequired(action)
            }
            override suspend fun queryOwnedPurchases(request: OwnedPurchasesRequest) =
                OwnedPurchasesResult.None
            override suspend fun requestCancellation(request: ProviderCancellationRequest) =
                ProviderCancellationResult.StillPending
        }
        val registry = object : BillingGatewayRegistry {
            override fun gateway(provider: BillingProvider): BillingGateway? =
                gateway.takeIf { provider == BillingProvider.GOOGLE_PLAY }
        }
        val coordinator = CoreBillingCheckoutCoordinator(
            gateways = registry,
            currentUser = CurrentUserIdProvider { "authenticated-user" },
            ids = StableIdGenerator { "operation.0000000000000001" },
            mapper = GanjPresentationMapper(),
            actionVault = vault,
        )

        val result = runSuspend {
            coordinator.begin(plan(), "idempotency.0000000000001")
        }

        val safe = (result as BillingStartResult.Pending).action as
            com.ganj.vpn.presentation.CheckoutSafeAction.LaunchGooglePlay
        assertEquals(HANDLE, safe.handle)
        assertSame(action, vault.consume(safe.handle))
        assertEquals("premium-monthly", capturedRequest?.productId)
    }

    @Test
    fun `vault leases provider action exactly once`() {
        val vault = vault()
        val action = playAction()

        val handle = vault.store(action)

        assertEquals(HANDLE, handle)
        assertSame(action, vault.consume(handle))
        assertNull(vault.consume(handle))
    }

    @Test
    fun `effect executor rejects replay without launching provider twice`() {
        val vault = vault()
        val handle = vault.store(playAction())
        var launches = 0
        val executor = GooglePlayCheckoutEffectExecutor(vault)
        val launcher = GooglePlayActionLauncher {
            launches += 1
            PlayBillingLaunchResult.Launched
        }

        val first = runSuspend { executor.execute(handle, launcher) }
        val replay = runSuspend { executor.execute(handle, launcher) }

        assertEquals(CheckoutEffectResult.Launched, first)
        assertEquals(CheckoutEffectResult.MissingOrConsumed, replay)
        assertEquals(1, launches)
    }

    @Test
    fun `launch failure is safely mapped after one-time consume`() {
        val vault = vault()
        val handle = vault.store(playAction())
        val executor = GooglePlayCheckoutEffectExecutor(vault)
        val failure = BillingFailure(
            code = BillingFailureCode.PROVIDER_UNAVAILABLE,
            retryable = true,
            safeMessageKey = "billing.play.launch_unavailable",
        )

        val result = runSuspend {
            executor.execute(handle) { PlayBillingLaunchResult.Rejected(failure) }
        }

        assertTrue(result is CheckoutEffectResult.Failed)
        assertEquals("billing.play.launch_unavailable", (result as CheckoutEffectResult.Failed).failure.messageKey)
        assertTrue(result.failure.retryable)
        assertEquals(
            CheckoutEffectResult.MissingOrConsumed,
            runSuspend { executor.execute(handle) { PlayBillingLaunchResult.Launched } },
        )
    }

    @Test
    fun `compose-safe launch action contains only opaque handle`() {
        val fields = com.ganj.vpn.presentation.CheckoutSafeAction.LaunchGooglePlay::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name }

        assertEquals(listOf("handle"), fields)
        assertEquals("CheckoutActionHandle([OPAQUE])", HANDLE.toString())
    }

    private fun vault() = OneTimeCheckoutActionVault(
        handles = CheckoutActionHandleGenerator { HANDLE },
        maximumEntries = 2,
    )

    private fun playAction() = ProviderClientAction.LaunchGooglePlay(
        checkoutSessionId = CheckoutSessionId("gp.checkout.00000000000000000000000000000001"),
        productId = "premium-monthly",
        offerId = "monthly:base",
        obfuscatedAccountReference = "a".repeat(64),
    )

    private fun plan() = PlanUiModel(
        id = "10000000-0000-4000-8000-000000000001",
        code = "premium-monthly",
        title = "Premium Monthly",
        tier = UiTier.PREMIUM,
        durationDays = 30,
        trafficLimitBytes = null,
        deviceLimit = 3,
        benefits = listOf("Smart connect"),
        amountMinor = 999,
        currency = "EUR",
    )

    private companion object {
        val HANDLE = CheckoutActionHandle("gp_action_00000000000000000000000000000001")

        fun <T> runSuspend(block: suspend () -> T): T {
            var outcome: Result<T>? = null
            block.startCoroutine(
                object : Continuation<T> {
                    override val context = EmptyCoroutineContext
                    override fun resumeWith(result: Result<T>) {
                        outcome = result
                    }
                },
            )
            assertNotNull("Test coroutine must complete synchronously", outcome)
            return outcome!!.getOrThrow()
        }
    }
}
