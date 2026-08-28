package com.ganj.vpn.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjUiReducerTest {
    private val reducer = GanjUiReducer()

    @Test
    fun `refresh remains in progress until catalog services and servers resolve`() {
        val refreshing = reducer.reduce(readyState(), GanjUiEvent.RefreshRequested)

        assertEquals(ContentState.Loading, refreshing.catalog)
        assertEquals(ContentState.Loading, refreshing.services)
        assertEquals(ContentState.Loading, refreshing.servers)
        assertTrue(refreshing.refreshInProgress)

        val catalogResolved = reducer.reduce(refreshing, GanjUiEvent.CatalogResolved(ContentState.Empty))
        assertEquals(ContentState.Empty, catalogResolved.catalog)
        assertTrue(catalogResolved.refreshInProgress)

        val servicesResolved = reducer.reduce(catalogResolved, GanjUiEvent.ServicesResolved(ContentState.Empty))
        assertEquals(ContentState.Empty, servicesResolved.services)
        assertTrue(servicesResolved.refreshInProgress)

        val allResolved = reducer.reduce(servicesResolved, GanjUiEvent.ServersResolved(ContentState.Empty))
        assertEquals(ContentState.Empty, allResolved.servers)
        assertFalse(allResolved.refreshInProgress)
    }

    @Test
    fun `auth-required and error remain explicit content states`() {
        val auth = reducer.reduce(GanjUiState(), GanjUiEvent.ServicesResolved(ContentState.AuthRequired))
        val failure = UiFailure(UiFailureKind.NETWORK, "network.unavailable", true)
        val error = reducer.reduce(auth, GanjUiEvent.CatalogResolved(ContentState.Error(failure)))

        assertEquals(ContentState.AuthRequired, auth.services)
        assertEquals(ContentState.Error(failure), error.catalog)
    }

    @Test
    fun `only active entitlements can be selected`() {
        val active = service(ACTIVE_ID, ServiceUiStatus.ACTIVE)
        val expired = service(EXPIRED_ID, ServiceUiStatus.EXPIRED)
        val state = GanjUiState(
            services = ContentState.Ready(listOf(active, expired)),
            servers = ContentState.Ready(listOf(server())),
        )

        val denied = reducer.reduce(state, GanjUiEvent.SelectService(EXPIRED_ID))
        val selected = reducer.reduce(denied, GanjUiEvent.SelectService(ACTIVE_ID))

        assertEquals(null, denied.selectedEntitlementId)
        assertEquals(ACTIVE_ID, selected.selectedEntitlementId)
        assertEquals(SERVER_ID, selected.selectedServerId)
    }

    @Test
    fun `selecting a server also selects a compatible active entitlement`() {
        val free = service(FREE_ID, ServiceUiStatus.ACTIVE, UiTier.FREE)
        val premium = service(ACTIVE_ID, ServiceUiStatus.ACTIVE, UiTier.PREMIUM)
        val premiumServer = server(tier = UiTier.PREMIUM)
        val state = GanjUiState(
            services = ContentState.Ready(listOf(free, premium)),
            servers = ContentState.Ready(listOf(premiumServer)),
            selectedEntitlementId = FREE_ID,
        )

        val selected = reducer.reduce(state, GanjUiEvent.SelectServer(SERVER_ID))

        assertEquals(SERVER_ID, selected.selectedServerId)
        assertEquals(ACTIVE_ID, selected.selectedEntitlementId)
    }

    @Test
    fun `checkout progresses pending verified active`() {
        val plan = plan()
        val initial = GanjUiState(catalog = ContentState.Ready(listOf(plan)))
        val requested = reducer.reduce(
            initial,
            GanjUiEvent.CheckoutRequested(plan.id),
        )
        val pending = reducer.reduce(
            requested,
            GanjUiEvent.CheckoutPending(
                plan.id,
                "order-1",
                CheckoutSafeAction.LaunchGooglePlay(ACTION_HANDLE),
            ),
        )
        val verified = reducer.reduce(pending, GanjUiEvent.CheckoutVerified(plan.id, "order-1"))
        val active = reducer.reduce(verified, GanjUiEvent.CheckoutActivated(plan.id, ACTIVE_ID))

        assertTrue(requested.checkout is CheckoutUiState.Pending)
        assertEquals(
            CheckoutSafeAction.LaunchGooglePlay(ACTION_HANDLE),
            (pending.checkout as CheckoutUiState.Pending).action,
        )
        assertTrue(verified.checkout is CheckoutUiState.Verified)
        assertEquals(CheckoutUiState.Active(plan.id, ACTIVE_ID), active.checkout)
        assertEquals(ACTIVE_ID, active.selectedEntitlementId)
    }

    @Test
    fun `connection requires active entitlement compatible server and matching pending request`() {
        val state = readyState()
        val requested = reducer.reduce(state, GanjUiEvent.ConnectionRequested(ACTIVE_ID))
        val stale = reducer.reduce(
            requested,
            GanjUiEvent.ConnectionProfileReady(
                "different",
                "profile-stale",
                "2026-08-25T00:00:00Z",
                ConnectionSafeAction.StartTunnel(CONNECTION_HANDLE),
            ),
        )
        val ready = reducer.reduce(
            stale,
            GanjUiEvent.ConnectionProfileReady(
                ACTIVE_ID,
                "profile-1",
                "2026-08-25T00:00:00Z",
                ConnectionSafeAction.StartTunnel(CONNECTION_HANDLE),
            ),
        )
        val denied = reducer.reduce(state, GanjUiEvent.ConnectionRequested(EXPIRED_ID))

        assertEquals(ConnectionUiState.Requesting(ACTIVE_ID), requested.connection)
        assertEquals(SERVER_ID, requested.selectedServerId)
        assertEquals(requested.connection, stale.connection)
        assertEquals(
            ConnectionUiState.ProfileReady(
                ACTIVE_ID,
                "profile-1",
                "2026-08-25T00:00:00Z",
                ConnectionSafeAction.StartTunnel(CONNECTION_HANDLE),
            ),
            ready.connection,
        )
        assertTrue(denied.connection is ConnectionUiState.Failed)
        assertEquals(
            "connection.service_inactive",
            (denied.connection as ConnectionUiState.Failed).failure.messageKey,
        )
    }

    @Test
    fun `active entitlement without eligible server reports server unavailable`() {
        val active = service(ACTIVE_ID, ServiceUiStatus.ACTIVE)
        val state = GanjUiState(
            services = ContentState.Ready(listOf(active)),
            servers = ContentState.Empty,
            selectedEntitlementId = ACTIVE_ID,
        )

        val denied = reducer.reduce(state, GanjUiEvent.ConnectionRequested(ACTIVE_ID))

        val failure = (denied.connection as ConnectionUiState.Failed).failure
        assertEquals("connection.server_unavailable", failure.messageKey)
        assertTrue(failure.retryable)
    }

    private fun readyState(): GanjUiState = GanjUiState(
        catalog = ContentState.Ready(listOf(plan())),
        services = ContentState.Ready(
            listOf(
                service(ACTIVE_ID, ServiceUiStatus.ACTIVE),
                service(EXPIRED_ID, ServiceUiStatus.EXPIRED),
            ),
        ),
        servers = ContentState.Ready(listOf(server())),
        selectedPlanId = PLAN_ID,
        selectedEntitlementId = ACTIVE_ID,
        selectedServerId = SERVER_ID,
    )

    private fun plan() = PlanUiModel(
        id = PLAN_ID,
        code = "premium-monthly",
        title = "Premium Monthly",
        tier = UiTier.PREMIUM,
        durationDays = 30,
        trafficLimitBytes = 100_000,
        deviceLimit = 3,
        benefits = listOf("Smart connect"),
        amountMinor = 999,
        currency = "EUR",
    )

    private fun service(
        id: String,
        status: ServiceUiStatus,
        tier: UiTier = UiTier.PREMIUM,
    ) = ServiceUiModel(
        entitlementId = id,
        displayName = "Service $id",
        status = status,
        tier = tier,
        countryCode = "DE",
        trafficLimitBytes = 100_000,
        trafficUsedBytes = 1_000,
        expiresAt = "2027-01-01T00:00:00Z",
        deviceLimit = 3,
        allowedProtocols = setOf("VLESS"),
    )

    private fun server(tier: UiTier = UiTier.FREE) = ServerUiModel(
        id = SERVER_ID,
        code = "de-01",
        displayName = "Germany 01",
        countryCode = "DE",
        city = "Frankfurt",
        tier = tier,
        status = ServerUiStatus.ACTIVE,
        loadRatio = 0.25,
        latencyHintMs = 40,
        favorite = true,
        protocols = setOf("VLESS"),
    )

    private companion object {
        const val PLAN_ID = "10000000-0000-4000-8000-000000000001"
        const val ACTIVE_ID = "20000000-0000-4000-8000-000000000001"
        const val EXPIRED_ID = "20000000-0000-4000-8000-000000000002"
        const val FREE_ID = "20000000-0000-4000-8000-000000000003"
        const val SERVER_ID = "40000000-0000-4000-8000-000000000001"
        val ACTION_HANDLE = CheckoutActionHandle("gp_action_00000000000000000000000000000001")
        val CONNECTION_HANDLE = ConnectionActionHandle("vpn_action_00000000000000000000000000000001")
    }
}
