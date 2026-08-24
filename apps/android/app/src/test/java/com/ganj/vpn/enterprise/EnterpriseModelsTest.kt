package com.ganj.vpn.enterprise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterpriseModelsTest {
    @Test
    fun `unsigned runtime config and update policy are rejected`() {
        assertThrows(IllegalArgumentException::class.java) {
            runtime(signatureVerified = false)
        }
        assertThrows(IllegalArgumentException::class.java) {
            update(signatureVerified = false)
        }
    }

    @Test
    fun `bug report rejects pasted VPN configuration and credentials`() {
        val unsafe = listOf(
            "vless://example.invalid/profile",
            "Subscription link https://example.invalid/config",
            "Here is my token=super-secret-value",
            "Bearer JWT eyJabcdefghijk.abcdefghijk.abcdefghijk",
            "WireGuard [Interface] private_key=secret",
        )

        unsafe.forEach { description ->
            assertThrows(IllegalArgumentException::class.java) {
                CreateBugReportCommand(
                    clientReportId = REPORT_ID,
                    title = "Connection issue",
                    description = description,
                    category = BugCategory.CONNECTION,
                    occurredAtEpochMillis = NOW,
                    automaticContextConsent = true,
                    context = context(),
                )
            }
        }
    }

    @Test
    fun `diagnostic contract exposes only allowlisted aggregate results`() {
        val test = PrivacySafeDiagnosticTest(
            kind = DiagnosticKind.PACKET_LOSS,
            outcome = DiagnosticOutcome.WARNING,
            latencyMillis = 240,
            packetLossRatio = 0.04,
            resultCode = "packet_loss_warning",
        )
        val fields = PrivacySafeDiagnosticTest::class.java.declaredFields
            .filterNot { java.lang.reflect.Modifier.isStatic(it.modifiers) || it.isSynthetic }
            .map { it.name.lowercase() }

        assertEquals(0.04, test.packetLossRatio!!, 0.0)
        assertFalse(fields.any { name ->
            listOf("host", "destination", "address", "ip", "query", "payload", "config", "uri")
                .any(name::contains)
        })
    }

    @Test
    fun `diagnostic upload requires explicit consent and bounded unique tests`() {
        assertThrows(IllegalArgumentException::class.java) {
            CreateDiagnosticReportCommand(
                clientReportId = REPORT_ID,
                deviceId = DEVICE_ID,
                explicitConsent = false,
                startedAtEpochMillis = NOW,
                completedAtEpochMillis = NOW + 10,
                tests = listOf(PrivacySafeDiagnosticTest(DiagnosticKind.NETWORK, DiagnosticOutcome.PASSED)),
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            CreateDiagnosticReportCommand(
                clientReportId = REPORT_ID,
                deviceId = DEVICE_ID,
                explicitConsent = true,
                startedAtEpochMillis = NOW,
                completedAtEpochMillis = NOW + 10,
                tests = listOf(
                    PrivacySafeDiagnosticTest(DiagnosticKind.NETWORK, DiagnosticOutcome.PASSED),
                    PrivacySafeDiagnosticTest(DiagnosticKind.NETWORK, DiagnosticOutcome.WARNING),
                ),
            )
        }
    }

    @Test
    fun `unknown remote values cannot enter typed feature state`() {
        val config = runtime()

        assertTrue(config.enabled(EnterpriseFeatureKey.BUG_REPORTS))
        assertFalse(config.enabled(EnterpriseFeatureKey.SUPPORT_DIAGNOSTICS))
        assertEquals(setOf(EnterpriseFeatureKey.BUG_REPORTS), config.assignments.map { it.key }.toSet())
    }

    private fun runtime(signatureVerified: Boolean = true) = VerifiedRuntimeConfiguration(
        version = 4,
        expiresAtEpochMillis = NOW + 60_000,
        maintenance = false,
        maintenanceMessage = null,
        assignments = listOf(
            FeatureAssignment(EnterpriseFeatureKey.BUG_REPORTS, true, "control", 4),
        ),
        signatureVerified = signatureVerified,
    )

    private fun update(signatureVerified: Boolean = true) = VerifiedUpdatePolicy(
        channel = ReleaseChannel.STABLE,
        currentVersion = "1.0.0",
        minimumVersion = "1.0.0",
        latestVersion = "1.1.0",
        requirement = UpdateRequirement.OPTIONAL,
        reasonCode = "feature_release",
        signatureVerified = signatureVerified,
    )

    private fun context() = PrivacySafeDeviceContext(
        deviceId = DEVICE_ID,
        appVersion = "1.0.0",
        osMajorVersion = "16",
        deviceClass = DeviceClass.PHONE,
        connectionState = SafeConnectionState.DISCONNECTED,
        networkType = SafeNetworkType.WIFI,
        serverId = null,
        errorCode = "connect_timeout",
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val REPORT_ID = "60000000-0000-4000-8000-000000000001"
        const val DEVICE_ID = "30000000-0000-4000-8000-000000000001"
    }
}
