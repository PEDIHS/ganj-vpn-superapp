package com.ganj.vpn.core.playbilling

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.fail
import org.junit.Test

class PlayPurchaseProofSecurityTest {
    @Test
    fun `sensitive proof redacts itself and cannot be used after close`() {
        val secret = "private-purchase-token"
        val proof = SensitivePlayPurchaseProof(secret.toByteArray())
        assertFalse(proof.toString().contains(secret))
        assertEquals(secret, proof.useBytes { it.toString(Charsets.UTF_8) })

        proof.close()

        try {
            proof.useBytes { Unit }
            fail("closed proof must not be reusable")
        } catch (_: IllegalStateException) {
            // Expected.
        }
    }
}
