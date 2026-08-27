package com.ganj.vpn.presentation

import com.ganj.vpn.core.controlapi.ApiError
import com.ganj.vpn.core.controlapi.ApiResult
import com.ganj.vpn.core.controlapi.CatalogProduct
import com.ganj.vpn.core.controlapi.CheckoutCommand
import com.ganj.vpn.core.controlapi.CheckoutOrder
import com.ganj.vpn.core.controlapi.ConnectionProfileCommand
import com.ganj.vpn.core.controlapi.ConnectionProfileLease
import com.ganj.vpn.core.controlapi.ControlApiRepository
import com.ganj.vpn.core.controlapi.Money
import com.ganj.vpn.core.controlapi.OrderStatus
import com.ganj.vpn.core.controlapi.PurchaseChannel
import com.ganj.vpn.core.controlapi.ResponseMetadata
import com.ganj.vpn.core.controlapi.ServiceStatus
import com.ganj.vpn.core.controlapi.SubscriptionTier
import com.ganj.vpn.core.controlapi.UserService
import com.ganj.vpn.core.controlapi.VpnProtocol
import kotlin.coroutines.Continuation
import kotlin.coroutines.EmptyCoroutineContext
import kotlin.coroutines.startCoroutine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjControllerTest {
    @Test
    fun `refresh resolves catalog and services without mock state`() {
        val repository = FakeRepository(
            catalogResult = success(listOf(product())),
            servicesResult = success(listOf(service())),
        )
        val controller = controller(repository)

        val state = controller.refresh(GanjUiState(refreshInProgress = true))

        assertEquals(PLAN_ID, state.selectedPlanId)
        assertEquals(SERVICE_ID, state.selectedEntitlementId)
        assertFalse(state.refreshInProgress)
        assertEquals(1, state.plans.size)
        assertEquals(1, state.serviceItems.size)
    }

    @Test
    fun `checkout maps 401 to auth required and never invokes billing`() {
        val repository = FakeRepository(
            checkoutResult = ApiResult.Failure(ApiError.AuthenticationRequired("request-401")),
        )
        val billing = FakeBilling(BillingStartResult.Pending(CheckoutSafeAction.LaunchGooglePlay(ACTION_HANDLE)))
        val controller = controller(repository, billing = billing)

        val state = runSuspend {
            controller.checkout(stateWithPlan(), PLAN_ID)
        }

        assertEquals(CheckoutUiState.AuthRequired, state.checkout)
        assertEquals(0, billing.calls)
    }

    @Test
    fun `missing authenticated session creates no checkout identifier or API request`() {
        val repository = FakeRepository(
            checkoutResult = success(order(OrderStatus.PENDING, entitlementId = null)),
        )
        val billing = FakeBilling(BillingStartResult.Pending(CheckoutSafeAction.LaunchGooglePlay(ACTION_HANDLE)))
        var generated = false
        val controller = controller(
            repository = repository,
            billing = billing,
            checkoutSession = AuthenticatedCheckoutSession { false },
            ids = StableIdGenerator {
                generated = true
                IDEMPOTENCY_KEY
            },
        )

        val state = runSuspend { controller.checkout(stateWithPlan(), PLAN_ID) }

        assertEquals(CheckoutUiState.AuthRequired, state.checkout)
        assertFalse(generated)
        assertNull(repository.checkoutCommand)
        assertEquals(0, billing.calls)
    }

    @Test
    fun `checkout maps 409 429 and 5xx to retryable safe failure`() {
        val errors = listOf(
            ApiError.Conflict("request-409", "duplicate") to UiFailureKind.CONFLICT,
            ApiError.RateLimited("request-429", 60) to UiFailureKind.RATE_LIMIT,
            ApiError.Server("request-503", 503, "unavailable", true) to UiFailureKind.SERVER,
        )

        errors.forEach { (error, expectedKind) ->
            val controller = controller(FakeRepository(checkoutResult = ApiResult.Failure(error)))
            val state = runSuspend {
                controller.checkout(stateWithPlan(), PLAN_ID)
            }
            val failure = (state.checkout as CheckoutUiState.Failed).failure
            assertEquals(expectedKind, failure.kind)
            assertTrue(failure.retryable)
        }
    }

    @Test
    fun `pending checkout shares retry-stable idempotency key with billing`() {
        val repository = FakeRepository(
            checkoutResult = success(order(OrderStatus.PENDING, entitlementId = null)),
        )
        val billing = FakeBilling(BillingStartResult.Pending(CheckoutSafeAction.LaunchGooglePlay(ACTION_HANDLE)))
        val controller = controller(repository, billing = billing)

        val state = runSuspend {
            controller.checkout(stateWithPlan(), PLAN_ID)
        }

        val pending = state.checkout as CheckoutUiState.Pending
        assertEquals("order-1", pending.orderId)
        assertEquals(CheckoutSafeAction.LaunchGooglePlay(ACTION_HANDLE), pending.action)
        assertEquals(repository.checkoutCommand?.idempotencyKey, billing.idempotencyKey)
        assertEquals(PurchaseChannel.PLAY, repository.checkoutCommand?.channel)
    }

    @Test
    fun `fulfilled checkout becomes active only after backend service sync`() {
        val repository = FakeRepository(
            servicesResult = success(listOf(service())),
            checkoutResult = success(order(OrderStatus.FULFILLED, SERVICE_ID)),
        )
        val billing = FakeBilling(BillingStartResult.Pending(CheckoutSafeAction.WaitForProvider))
        val controller = controller(repository, billing = billing)

        val state = runSuspend {
            controller.checkout(stateWithPlan(), PLAN_ID)
        }

        assertEquals(CheckoutUiState.Active(PLAN_ID, SERVICE_ID), state.checkout)
        assertEquals(SERVICE_ID, state.selectedEntitlementId)
        assertEquals(0, billing.calls)
    }

    @Test
    fun `connection UI supplies only entitlement and controller creates bound command`() {
        val repository = FakeRepository(
            profileResult = ApiResult.Failure(ApiError.Forbidden("request-403", "device_denied")),
        )
        var contextInput: String? = null
        val contextProvider = ConnectionProfileContextProvider { entitlementId ->
            contextInput = entitlementId
            ConnectionProfileContext(DEVICE_ID, SERVER_ID, NONCE, DEVICE_PROOF)
        }
        val controller = controller(repository, connectionContext = contextProvider)

        val state = controller.prepareConnection(stateWithService(), SERVICE_ID)

        assertEquals(SERVICE_ID, contextInput)
        assertEquals(SERVICE_ID, repository.profileCommand?.serviceId)
        assertEquals(DEVICE_ID, repository.profileCommand?.deviceId)
        assertEquals(SERVER_ID, repository.profileCommand?.serverId)
        assertTrue(state.connection is ConnectionUiState.Failed)
        assertEquals(UiFailureKind.ENTITLEMENT, (state.connection as ConnectionUiState.Failed).failure.kind)
    }

    @Test
    fun `inactive or unknown entitlement fails before context and profile API`() {
        var contextCalled = false
        val repository = FakeRepository()
        val controller = controller(
            repository,
            connectionContext = ConnectionProfileContextProvider {
                contextCalled = true
                null
            },
        )

        val state = controller.prepareConnection(stateWithService(ServiceStatus.EXPIRED), SERVICE_ID)

        assertFalse(contextCalled)
        assertNull(repository.profileCommand)
        assertEquals(
            "connection.service_inactive",
            (state.connection as ConnectionUiState.Failed).failure.messageKey,
        )
    }

    @Test
    fun `missing device context fails closed without requesting a profile`() {
        val repository = FakeRepository()
        val controller = controller(repository, connectionContext = ConnectionProfileContextProvider { null })

        val state = controller.prepareConnection(stateWithService(), SERVICE_ID)

        assertNull(repository.profileCommand)
        val failed = state.connection as ConnectionUiState.Failed
        assertEquals(UiFailureKind.CONFIGURATION, failed.failure.kind)
        assertEquals("connection.context_unavailable", failed.failure.messageKey)
    }

    private fun controller(
        repository: FakeRepository,
        billing: FakeBilling = FakeBilling(BillingStartResult.AuthRequired),
        checkoutSession: AuthenticatedCheckoutSession = AuthenticatedCheckoutSession { true },
        connectionContext: ConnectionProfileContextProvider = ConnectionProfileContextProvider { null },
        ids: StableIdGenerator = StableIdGenerator { IDEMPOTENCY_KEY },
        currentUser: CurrentUserIdProvider = CurrentUserIdProvider { USER_ID },
        connectionActions: ConnectionActionVault = InMemoryConnectionActionVault(
            tokenFactory = { "00000000000000000000000000000002" },
        ),
    ) = GanjController(
        repository = repository,
        billing = billing,
        checkoutSession = checkoutSession,
        currentUser = currentUser,
        connectionContext = connectionContext,
        connectionActions = connectionActions,
        ids = ids,
    )

    private fun stateWithPlan() = GanjUiState(
        catalog = ContentState.Ready(listOf(planUi())),
        services = ContentState.Empty,
        selectedPlanId = PLAN_ID,
    )

    private fun stateWithService(status: ServiceStatus = ServiceStatus.ACTIVE): GanjUiState {
        val mapped = GanjPresentationMapper().services(success(listOf(service(status))))
        return GanjUiState(
            catalog = ContentState.Empty,
            services = mapped,
            selectedEntitlementId = if (status == ServiceStatus.ACTIVE) SERVICE_ID else null,
        )
    }

    private fun planUi() = (GanjPresentationMapper().catalog(success(listOf(product()))) as ContentState.Ready).items.single()

    private fun product() = CatalogProduct(
        id = PLAN_ID,
        code = "premium-monthly",
        name = "Premium Monthly",
        tier = SubscriptionTier.PREMIUM,
        durationDays = 30,
        trafficLimitBytes = 100_000,
        deviceLimit = 3,
        features = listOf("Smart connect"),
        price = Money(999, "EUR"),
    )

    private fun service(status: ServiceStatus = ServiceStatus.ACTIVE) = UserService(
        id = SERVICE_ID,
        name = "Germany Premium",
        status = status,
        tier = SubscriptionTier.PREMIUM,
        countryCode = "DE",
        trafficLimitBytes = 100_000,
        trafficUsedBytes = 10_000,
        expiresAt = "2027-01-01T00:00:00Z",
        deviceLimit = 3,
        allowedProtocols = setOf(VpnProtocol.VLESS),
    )

    private fun order(status: OrderStatus, entitlementId: String?) = CheckoutOrder(
        id = "order-1",
        status = status,
        channel = PurchaseChannel.PLAY,
        total = Money(999, "EUR"),
        entitlementServiceId = entitlementId,
    )

    private class FakeBilling(
        private val result: BillingStartResult,
    ) : CheckoutBillingCoordinator {
        var calls: Int = 0
        var plan: PlanUiModel? = null
        var idempotencyKey: String? = null

        override suspend fun begin(
            plan: PlanUiModel,
            idempotencyKey: String,
        ): BillingStartResult {
            calls += 1
            this.plan = plan
            this.idempotencyKey = idempotencyKey
            return result
        }
    }

    private class FakeRepository(
        var catalogResult: ApiResult<List<CatalogProduct>> = success(emptyList()),
        var servicesResult: ApiResult<List<UserService>> = success(emptyList()),
        var checkoutResult: ApiResult<CheckoutOrder> = ApiResult.Failure(
            ApiError.Server(null, 503, "not_configured", true),
        ),
        var profileResult: ApiResult<ConnectionProfileLease> = ApiResult.Failure(
            ApiError.Server(null, 503, "not_configured", true),
        ),
    ) : ControlApiRepository {
        var checkoutCommand: CheckoutCommand? = null
        var profileCommand: ConnectionProfileCommand? = null

        override fun catalog(channel: PurchaseChannel): ApiResult<List<CatalogProduct>> = catalogResult

        override fun myServices(): ApiResult<List<UserService>> = servicesResult

        override fun checkout(command: CheckoutCommand): ApiResult<CheckoutOrder> {
            checkoutCommand = command
            return checkoutResult
        }

        override fun prepareConnection(command: ConnectionProfileCommand): ApiResult<ConnectionProfileLease> {
            profileCommand = command
            return profileResult
        }
    }

    private companion object {
        const val PLAN_ID = "10000000-0000-4000-8000-000000000001"
        const val SERVICE_ID = "20000000-0000-4000-8000-000000000001"
        const val USER_ID = "10000000-0000-4000-8000-000000000099"
        const val DEVICE_ID = "30000000-0000-4000-8000-000000000001"
        const val SERVER_ID = "40000000-0000-4000-8000-000000000001"
        const val IDEMPOTENCY_KEY = "50000000-0000-4000-8000-000000000001"
        const val NONCE = "nonce-opaque-device-bound-value-00000001"
        const val DEVICE_PROOF = "proof-opaque-device-bound-value-00000001"
        val ACTION_HANDLE = CheckoutActionHandle("gp_action_00000000000000000000000000000001")

        fun <T> success(value: T): ApiResult.Success<T> = ApiResult.Success(
            value,
            ResponseMetadata("request-id", "2026-08-24T00:00:00Z"),
        )

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
