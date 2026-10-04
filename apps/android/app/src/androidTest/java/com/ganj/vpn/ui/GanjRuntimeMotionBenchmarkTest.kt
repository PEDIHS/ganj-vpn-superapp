package com.ganj.vpn.ui

import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Surface
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.rules.ActivityScenarioRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.ganj.vpn.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections
import java.util.concurrent.atomic.AtomicInteger

/** Actual Choreographer/Window frames, deliberately independent of Compose's synthetic test clock. */
@SdkSuppress(minSdkVersion = 31)
@RunWith(AndroidJUnit4::class)
class GanjRuntimeMotionBenchmarkTest {
    @get:Rule val activity = ActivityScenarioRule(MainActivity::class.java)

    @Test fun recordActualTunnelMotionFramesWithSystemAnimationsEnabled() {
        val phase = mutableStateOf(GanjConnectionVisualState.Disconnected)
        val disconnecting = mutableStateOf(false)
        val frames = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val dropped = AtomicInteger()
        val thread = HandlerThread("ganj-frame-metrics").apply { start() }
        var attached = false
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, drops ->
            val duration = metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            if (metrics.getMetric(FrameMetrics.FIRST_DRAW_FRAME) == 0L && duration > 0L) {
                frames.add(duration to metrics.getMetric(FrameMetrics.DEADLINE))
            }
            dropped.addAndGet(drops)
        }
        try {
            activity.scenario.onActivity { host ->
                host.window.addOnFrameMetricsAvailableListener(listener, Handler(thread.looper))
                attached = true
                host.setContent {
                    GanjTheme(darkTheme = false, visualEffectsPolicy = GanjVisualEffectsPolicy(GanjEffectsTier.Full, false, false, false)) {
                        Surface(Modifier.fillMaxSize()) {
                            Column(Modifier.fillMaxWidth().padding(20.dp)) {
                                GanjConnectionHero(phase.value, "اتصال آزمایشی", "آزمون حرکت", disconnecting.value, false, {})
                            }
                        }
                    }
                }
            }
            Thread.sleep(250)
            activity.scenario.onActivity { phase.value = GanjConnectionVisualState.Connecting }
            Thread.sleep(1800)
            activity.scenario.onActivity { phase.value = GanjConnectionVisualState.Connected }
            Thread.sleep(600)
            activity.scenario.onActivity { phase.value = GanjConnectionVisualState.Reconnecting }
            Thread.sleep(400)
            activity.scenario.onActivity { phase.value = GanjConnectionVisualState.Connected; disconnecting.value = true }
            Thread.sleep(350)
            activity.scenario.onActivity { phase.value = GanjConnectionVisualState.Disconnected; disconnecting.value = false }
            Thread.sleep(300)
            activity.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener); attached = false }
            thread.quitSafely(); thread.join(2000)
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            android.os.ParcelFileDescriptor.AutoCloseInputStream(
                instrumentation.uiAutomation.executeShellCommand("dumpsys gfxinfo com.ganj.vpn framestats")
            ).use { input ->
                File(instrumentation.targetContext.cacheDir, "gfxinfo-framestats.txt").outputStream().use { input.copyTo(it) }
            }
            val observed = synchronized(frames) { frames.toList() }
            assertTrue("The system-clock fixture must record actual rendered frames", observed.size > 10)
            val sorted = observed.map { it.first }.sorted()
            val missed = observed.count { it.second > 0L && it.first > it.second }
            val unavailableDeadlines = observed.count { it.second <= 0L }
            val report = "{\"scope\":\"API35 emulator tunnel-motion fixture; physical-device performance unverified\",\"clock\":\"system Choreographer\",\"frames\":${sorted.size},\"medianMs\":${sorted[sorted.size / 2] / 1e6},\"p95Ms\":${sorted[((sorted.size - 1) * .95).toInt()] / 1e6},\"overDeadline\":$missed,\"unavailableDeadlines\":$unavailableDeadlines,\"droppedCallbacks\":${dropped.get()}}"
            File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir, "motion-frame-metrics.json").writeText(report)
        } finally {
            if (attached) activity.scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            thread.quitSafely()
        }
    }
}
