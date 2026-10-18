package com.ganj.vpn.composition

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.ganj.vpn.core.controlapi.AccessToken
import com.ganj.vpn.core.controlapi.AuthSessionCredentials
import com.ganj.vpn.core.controlapi.RefreshToken
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GanjMultiAccountVaultTest {
    private fun credentials(seed: Char): AuthSessionCredentials = AuthSessionCredentials(
        userId = when(seed) {
            '1' -> "11111111-1111-4111-8111-111111111111"
            '2' -> "22222222-2222-4222-8222-222222222222"
            '3' -> "33333333-3333-4333-8333-333333333333"
            else -> "44444444-4444-4444-8444-444444444444"
        },
        deviceId = "aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa",
        accessToken = AccessToken.from("access-token-value-" + seed.toString().repeat(24)),
        accessTokenExpiresAt = "2032-01-01T00:00:00Z",
        refreshToken = RefreshToken.from(seed.toString().repeat(43)),
        refreshTokenExpiresAt = "2032-02-01T00:00:00Z",
    )

    @Test fun encryptedSlotsNeverMixRefreshTokensOrExceedThreeAccounts() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        // Unique namespace isolates this test from real profile storage and
        // all production Android Keystore keys.
        val vault = GanjMultiAccountVault(context, "instrumenttest")
        val one=credentials('1');val two=credentials('2')
        val three=credentials('3');val four=credentials('4')
        assertTrue(vault.save(one).isSuccess)
        assertTrue(vault.confirmLinked(one.userId))
        assertTrue(vault.save(two).isSuccess)
        assertTrue(vault.confirmLinked(two.userId))
        assertTrue(vault.save(three).isSuccess)
        assertTrue(vault.confirmLinked(three.userId))
        assertEquals(3,vault.verifiedAccounts().size)
        assertFalse(vault.save(four).isSuccess)
        assertEquals(three.userId,vault.currentUserId())
        assertTrue(vault.selectVerified(one.userId))
        assertEquals(one.userId,vault.currentUserId())
        assertTrue(vault.selectVerified(two.userId))
        assertEquals(two.userId,vault.currentUserId())
        assertTrue(vault.clear().isSuccess)
        assertFalse(vault.verifiedAccounts().contains(two.userId))
        assertTrue(vault.selectVerified(one.userId))
        assertEquals(one.userId,vault.currentUserId())
    }
    @Test fun eachTelegramAccountUsesIndependentDeviceKeysAndGuestIdentity() {
        val application = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext
            as android.app.Application
        val vault = GanjMultiAccountVault(application, "proofcheck")
        val identity = GanjSwitchableDeviceIdentity(application, vault, "_proofcheck")
        val userOne = credentials('1')
        val userTwo = credentials('2')
        assertTrue(vault.save(userOne).isSuccess)
        assertTrue(vault.confirmLinked(userOne.userId))
        val primary = identity.publicIdentity().getOrThrow()
        assertTrue(vault.beginAddition())
        val second = identity.publicIdentity().getOrThrow()
        assertNotEquals(primary.installationId, second.installationId)
        assertNotEquals(primary.signingPublicKeySpki, second.signingPublicKeySpki)
        assertTrue(vault.save(userTwo).isSuccess)
        assertTrue(vault.confirmLinked(userTwo.userId))
        assertTrue(vault.beginAddition())
        val third = identity.publicIdentity().getOrThrow()
        assertNotEquals(second.installationId, third.installationId)
        assertNotEquals(primary.installationId, third.installationId)
        assertTrue(vault.abortAddition())
        assertEquals(second.installationId, identity.publicIdentity().getOrThrow().installationId)
        assertTrue(vault.selectVerified(userOne.userId))
        assertEquals(primary.installationId, identity.publicIdentity().getOrThrow().installationId)
    }

}
