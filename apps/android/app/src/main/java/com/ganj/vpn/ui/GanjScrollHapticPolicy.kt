package com.ganj.vpn.ui

import androidx.compose.ui.hapticfeedback.HapticFeedbackType

/** Lightweight detents only on actual list-row crossings, never per scroll pixel. */
internal object GanjScrollHapticPolicy {
    private const val MIN_TICK_INTERVAL_MS = 65L

    fun feedbackForSdk(sdk: Int): HapticFeedbackType =
        when {
            sdk >= 34 -> HapticFeedbackType.SegmentFrequentTick
            sdk >= 27 -> HapticFeedbackType.TextHandleMove
            else -> HapticFeedbackType.LongPress
        }

    fun shouldTick(rowChanged: Boolean, scrolling: Boolean, reduceMotion: Boolean,
                   nowMs: Long, lastTickMs: Long): Boolean =
        rowChanged && scrolling && !reduceMotion &&
            (lastTickMs < 0L || nowMs - lastTickMs >= MIN_TICK_INTERVAL_MS)
}
