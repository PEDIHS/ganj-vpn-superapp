package com.ganj.vpn.presentation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjUiReducerTest {
    private val reducer = GanjUiReducer()

    @Test
    fun `refresh enters loading and resolved empty clears progress`() {
        val refreshing = reducer.reduce(readyState(), GanjUiEvent.RefreshRequested)

        assertEquals(ContentState.Loading, refreshing.catalog)
        assertEquals(ContentState.Loading, refreshing.services)
        assertTrue(refreshing.refreshInProgress)

        val resolved = reducer.reduce(refreshing, GanjUiEvent.CatalogResolved(ContentState.Empty))
        assertEquals(ContentState.Empty, resolved.catalog)
        assertFalse(resolved.refreshInProgress)
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
        val state = GanjUiState(services = ContentState.Ready(listOf(active, expired)))

        val denied = reducer.reduce(state, GanjUiEvent.SelectService(EXPIRED_ID))
        val selected = reducer.reduce(denied, GanjUiEvent.SelectService(ACTIVE_ID))

        assertEquals(null, denied.selectedEntitlementId)
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
    fun `connection requires active entitlement and matching pending request`() {
        val state = readyState()
        val requested = reducer.reduce(state, GanjUiEvent.ConnectionRequested(ACTIVE_ID))
        val stale = reducer.reduce(
            requested,
            GanjUiEvent.ConnectionProfileReady("different", "profile-stale", "2026-08-25T00:00:00Z"),
        )
        val ready = reducer.reduce(
            stale,
            GanjUiEvent.ConnectionProfileReady(ACTIVE_ID, "profile-1", "2026-08-25T00:00:00Z"),
        )
        val denied = reducer.reduce(state, GanjUiEvent.ConnectionRequested(EXPIRED_ID))

        assertEquals(ConnectionUiState.Requesting(ACTIVE_ID), requested.connection)
        assertEquals(requested.connection, stale.connection)
        assertEquals(
            ConnectionUiState.ProfileReady(ACTIVE_ID, "profile-1", "2026-08-25T00:00:00Z"),
            ready.connection,
        )
        assertTrue(denied.connection is ConnectionUiState.Failed)
    }

    private fun readyState(): GanjUiState = GanjUiState(
        catalog = ContentState.Ready(listOf(plan())),
        services = ContentState.Ready(
            listOf(
                service(ACTIVE_ID, ServiceUiStatus.ACTIVE),
                service(EXPIRED_ID, ServiceUiStatus.EXPIRED),
            ),
        ),
        selectedPlanId = PLAN_ID,
        selectedEntitlementId = ACTIVE_ID,
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

    private fun service(id: String, status: ServiceUiStatus) = ServiceUiModel(
        entitlementId = id,
        displayName = "Service $id",
        status = status,
        tier = UiTier.PREMIUM,
        countryCode = "DE",
        trafficLimitBytes = 100_000,
        trafficUsedBytes = 1_000,
        expiresAt = "2027-01-01T00:00:00Z",
        deviceLimit = 3,
        allowedProtocols = setOf("VLESS"),
    )

    private companion object {
        const val PLAN_ID = "10000000-0000-4000-8000-000000000001"
        const val ACTIVE_ID = "20000000-0000-4000-8000-000000000001"
        const val EXPIRED_ID = "20000000-0000-4000-8000-000000000002"
        val ACTION_HANDLE = CheckoutActionHandle("gp_action_00000000000000000000000000000001")
    }
}
