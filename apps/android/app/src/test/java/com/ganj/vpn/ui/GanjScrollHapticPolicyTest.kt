package com.ganj.vpn.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GanjScrollHapticPolicyTest {
    @Test fun compatiblePlatformHaptics() {
        assertEquals(HapticFeedbackType.TextHandleMove, GanjScrollHapticPolicy.feedbackForSdk(24))
        assertEquals(HapticFeedbackType.TextHandleMove, GanjScrollHapticPolicy.feedbackForSdk(33))
        assertEquals(HapticFeedbackType.SegmentFrequentTick, GanjScrollHapticPolicy.feedbackForSdk(34))
        assertEquals(HapticFeedbackType.SegmentFrequentTick, GanjScrollHapticPolicy.feedbackForSdk(36))
    }
    @Test fun boundedRowDetents() {
        val p = GanjScrollHapticPolicy
        assertFalse(p.shouldTick(false, true, false, 100, -1))
        assertFalse(p.shouldTick(true, false, false, 100, -1))
        assertFalse(p.shouldTick(true, true, true, 100, -1))
        assertTrue(p.shouldTick(true, true, false, 100, -1))
        assertFalse(p.shouldTick(true, true, false, 130, 100))
        assertTrue(p.shouldTick(true, true, false, 165, 100))
    }
}
