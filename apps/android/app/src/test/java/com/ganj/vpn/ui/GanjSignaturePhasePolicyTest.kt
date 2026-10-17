package com.ganj.vpn.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjSignaturePhasePolicyTest {
    @Test fun activeGoldShimmerRemainsOnNormalDevices() {
        assertTrue(shouldRunSignaturePhase(true, false, GanjEffectsTier.Full))
        assertTrue(shouldRunSignaturePhase(true, false, GanjEffectsTier.Balanced))
    }

    @Test fun disabledButtonsAndAccessibilityDoNotAnimate() {
        assertFalse(shouldRunSignaturePhase(false, false, GanjEffectsTier.Full))
        assertFalse(shouldRunSignaturePhase(true, true, GanjEffectsTier.Full))
        assertFalse(shouldRunSignaturePhase(true, false, GanjEffectsTier.Reduced))
        assertFalse(shouldRunSignaturePhase(false, true, GanjEffectsTier.Reduced))
    }
}
