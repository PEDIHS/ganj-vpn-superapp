package com.ganj.vpn.core.xray

import org.junit.Assert.assertEquals
import org.junit.Test

class NativeFailureClassifierTest {
    @Test fun nativeErrorsAreClassifiedWithoutReturningPrivateContent() {
        val private = "vless://private-credential@example.invalid"
        assertEquals("probe.timeout", NativeFailureClassifier.probe("Get $private: context deadline exceeded"))
        assertEquals("probe.dns_failed", NativeFailureClassifier.probe("lookup example.invalid: no such host"))
        assertEquals("probe.tls_failed", NativeFailureClassifier.probe("TLS certificate validation failed: $private"))
        assertEquals("xray.config_invalid", NativeFailureClassifier.startup("failed to build outbound $private"))
        assertEquals("xray.native_rejected", NativeFailureClassifier.startup(private))
    }

    @Test fun failedPrimaryUsesFallbackAndZeroMillisIsSuccessful() {
        val attempted = mutableListOf<String>()
        val measured = ProxyProbeFallback.measure(listOf("primary", "fallback", "must-not-run")) {
            attempted.add(it)
            if (it == "primary") NativeProbeResult.Failed("probe.timeout") else NativeProbeResult.Measured(0)
        }
        assertEquals(NativeProbeResult.Measured(0), measured)
        assertEquals(listOf("primary", "fallback"), attempted)
    }

    @Test fun successfulPrimaryAndInvalidConfigDoNotRunFallback() {
        for (first in listOf(NativeProbeResult.Measured(43), NativeProbeResult.Failed("xray.config_invalid"))) {
            var calls = 0
            assertEquals(first, ProxyProbeFallback.measure {
                calls++
                first
            })
            assertEquals(1, calls)
        }
    }
}
