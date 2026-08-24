package com.ganj.vpn.core.subscription

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SubscriptionConnectionPolicyTest {
    private val policy = SubscriptionConnectionPolicy()

    @Test
    fun `valid owned subscription produces an opaque service command`() {
        val decision = policy.evaluate(
            service = service(),
            userId = USER_ID,
            serverId = "de-fra-01",
            nowEpochMillis = NOW,
        )

        assertTrue(decision is ConnectionDecision.Allowed)
        assertEquals(
            SubscriptionConnectionCommand(USER_ID, "service-vip", "de-fra-01"),
            (decision as ConnectionDecision.Allowed).command,
        )
    }

    @Test
    fun `a different user cannot connect another users service`() {
        val decision = policy.evaluate(service(), "attacker", "de-fra-01", NOW)

        assertEquals(
            ConnectionDenial.WRONG_OWNER,
            (decision as ConnectionDecision.Denied).reason,
        )
    }

    @Test
    fun `anonymous connection is denied before entitlement checks`() {
        val decision = policy.evaluate(service(), "", "de-fra-01", NOW)

        assertEquals(
            ConnectionDenial.NOT_AUTHENTICATED,
            (decision as ConnectionDecision.Denied).reason,
        )
    }

    @Test
    fun `expired service is rejected even when backend status is stale`() {
        val decision = policy.evaluate(
            service = service(validUntil = NOW),
            userId = USER_ID,
            serverId = "de-fra-01",
            nowEpochMillis = NOW,
        )

        assertEquals(
            ConnectionDenial.SUBSCRIPTION_EXPIRED,
            (decision as ConnectionDecision.Denied).reason,
        )
    }

    @Test
    fun `service cannot escape its server entitlement`() {
        val decision = policy.evaluate(service(), USER_ID, "us-nyc-vip", NOW)

        assertEquals(
            ConnectionDenial.SERVER_NOT_INCLUDED,
            (decision as ConnectionDecision.Denied).reason,
        )
    }

    @Test
    fun `zero traffic and full device quota are rejected`() {
        val exhausted = policy.evaluate(
            service = service(remainingBytes = 0),
            userId = USER_ID,
            serverId = "de-fra-01",
            nowEpochMillis = NOW,
        )
        val deviceLimited = policy.evaluate(
            service = service(activeDevices = 2),
            userId = USER_ID,
            serverId = "de-fra-01",
            nowEpochMillis = NOW,
        )

        assertEquals(ConnectionDenial.TRAFFIC_EXHAUSTED, (exhausted as ConnectionDecision.Denied).reason)
        assertEquals(ConnectionDenial.DEVICE_LIMIT_REACHED, (deviceLimited as ConnectionDecision.Denied).reason)
    }

    private fun service(
        validUntil: Long = NOW + 86_400_000,
        remainingBytes: Long? = 20_000_000_000,
        activeDevices: Int = 1,
    ) = UserService(
        id = "service-vip",
        ownerUserId = USER_ID,
        productId = "vip-monthly",
        displayName = "Germany VIP",
        tier = SubscriptionTier.VIP,
        status = UserServiceStatus.ACTIVE,
        validUntilEpochMillis = validUntil,
        remainingBytes = remainingBytes,
        maxDevices = 2,
        activeDevices = activeDevices,
        allowedServerIds = setOf("de-fra-01"),
    )

    private companion object {
        const val USER_ID = "telegram:10001"
        const val NOW = 1_800_000_000_000L
    }
}
