package com.ganj.vpn.core.vpn

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnReconnectCoordinatorTest {
    @Test
    fun boundsAttemptsWithinOneNetworkEpoch() {
        val coordinator = VpnReconnectCoordinator(
            backoff = VpnReconnectBackoff(10, 40),
            maximumAttempts = 3,
        )
        val epoch = coordinator.beginEpoch()

        assertEquals(10, coordinator.nextPlan(epoch)?.delayMillis)
        assertEquals(20, coordinator.nextPlan(epoch)?.delayMillis)
        assertEquals(40, coordinator.nextPlan(epoch)?.delayMillis)
        assertNull(coordinator.nextPlan(epoch))
    }

    @Test
    fun newerNetworkEpochInvalidatesEveryOlderPlan() {
        val coordinator = VpnReconnectCoordinator(VpnReconnectBackoff(10, 40))
        val oldEpoch = coordinator.beginEpoch()
        val oldPlan = requireNotNull(coordinator.nextPlan(oldEpoch))

        val newEpoch = coordinator.beginEpoch()

        assertFalse(coordinator.isCurrent(oldPlan.epoch))
        assertNull(coordinator.nextPlan(oldEpoch))
        assertTrue(coordinator.isCurrent(newEpoch))
        assertEquals(0, coordinator.nextPlan(newEpoch)?.attempt)
    }

    @Test
    fun explicitInvalidationCancelsPendingEpoch() {
        val coordinator = VpnReconnectCoordinator()
        val epoch = coordinator.beginEpoch()
        coordinator.invalidate()

        assertFalse(coordinator.isCurrent(epoch))
        assertNull(coordinator.nextPlan(epoch))
    }

    @Test
    fun connectedStateResetsAttemptCounterForCurrentEpoch() {
        val coordinator = VpnReconnectCoordinator(VpnReconnectBackoff(10, 40))
        val epoch = coordinator.beginEpoch()
        assertEquals(0, coordinator.nextPlan(epoch)?.attempt)
        assertEquals(1, coordinator.nextPlan(epoch)?.attempt)

        coordinator.markConnected(epoch)

        assertEquals(0, coordinator.nextPlan(epoch)?.attempt)
    }
}
