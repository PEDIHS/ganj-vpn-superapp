package com.ganj.vpn.enterprise

import com.ganj.vpn.presentation.StableIdGenerator
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterpriseControllerTest {
    @Test
    fun `forced update takes precedence over maintenance`() {
        val repository = FakeRepository(
            runtime = EnterpriseResult.Success(runtime(maintenance = true)),
            update = EnterpriseResult.Success(update(UpdateRequirement.FORCED)),
        )

        val state = controller(repository).refresh(EnterpriseUiState(refreshing = true))

        assertTrue(state.availability is ProductAvailabilityUiState.ForcedUpdate)
        assertTrue(state.features.bugReportsEnabled)
        assertFalse(state.refreshing)
    }

    @Test
    fun `expired config disables all remote features fail closed`() {
        val repository = FakeRepository(
            runtime = EnterpriseResult.Success(runtime(expiresAt = NOW)),
            update = EnterpriseResult.Success(update(UpdateRequirement.NONE)),
        )

        val state = controller(repository).refresh(EnterpriseUiState())

        assertEquals(
            ProductAvailabilityUiState.Failed("runtime_config.expired", true),
            state.availability,
        )
        assertEquals(EnterpriseFeatureUiState(), state.features)
    }

    @Test
    fun `optional update preserves allowlisted feature assignments`() {
        val repository = FakeRepository()

        val state = controller(repository).refresh(EnterpriseUiState())

        assertEquals(
            ProductAvailabilityUiState.OptionalUpdate("1.2.0", "feature_release"),
            state.availability,
        )
        assertEquals(9L, state.features.configVersion)
        assertTrue(state.features.bugReportsEnabled)
        assertTrue(state.features.diagnosticsEnabled)
    }

    @Test
    fun `missing session creates no report id and performs no repository write`() {
        val repository = FakeRepository()
        var generated = false
        val controller = controller(
            repository,
            session = EnterpriseSessionGate { false },
            ids = StableIdGenerator {
                generated = true
                REPORT_ID
            },
        )

        val state = controller.submitBug(enabledState(), bugInput())

        assertEquals(BugReportUiState.AuthRequired, state.bugReport)
        assertFalse(generated)
        assertNull(repository.bugCommand)
    }

    @Test
    fun `bug submission sends sanitized bounded context after consent`() {
        val repository = FakeRepository(
            bugResult = EnterpriseResult.Success(bugReceipt()),
        )
        val controller = controller(repository)

        val state = controller.submitBug(enabledState(), bugInput())

        assertEquals(BugReportUiState.Submitted("BUG-2026", BugStatus.NEW), state.bugReport)
        val command = repository.bugCommand!!
        assertEquals(REPORT_ID, command.clientReportId)
        assertEquals(DEVICE_ID, command.context.deviceId)
        assertEquals(DeviceClass.PHONE, command.context.deviceClass)
        assertTrue(command.automaticContextConsent)
    }

    @Test
    fun `diagnostic consent denial runs no collector and generates no id`() {
        val repository = FakeRepository()
        var collected = false
        var generated = false
        val controller = controller(
            repository,
            collector = PrivacySafeDiagnosticCollector {
                collected = true
                diagnosticTests()
            },
            ids = StableIdGenerator {
                generated = true
                REPORT_ID
            },
        )

        val state = controller.submitDiagnostics(enabledState(), explicitConsent = false)

        assertEquals(DiagnosticUiState.Failed("privacy.consent_required", false), state.diagnostic)
        assertFalse(collected)
        assertFalse(generated)
        assertNull(repository.diagnosticCommand)
    }

    @Test
    fun `diagnostic success uploads only aggregate allowlisted tests`() {
        val repository = FakeRepository(
            diagnosticResult = EnterpriseResult.Success(diagnosticReceipt()),
        )
        val controller = controller(repository)

        val state = controller.submitDiagnostics(enabledState(), explicitConsent = true)

        assertEquals(
            DiagnosticUiState.Submitted("redaction-v3", NOW + 86_400_000),
            state.diagnostic,
        )
        val command = repository.diagnosticCommand!!
        assertEquals(REPORT_ID, command.clientReportId)
        assertEquals(2, command.tests.size)
        assertEquals(setOf(DiagnosticKind.NETWORK, DiagnosticKind.PACKET_LOSS), command.tests.map { it.kind }.toSet())
    }

    private fun controller(
        repository: FakeRepository,
        session: EnterpriseSessionGate = EnterpriseSessionGate { true },
        collector: PrivacySafeDiagnosticCollector = PrivacySafeDiagnosticCollector { diagnosticTests() },
        ids: StableIdGenerator = StableIdGenerator { REPORT_ID },
    ) = EnterpriseController(
        repository = repository,
        session = session,
        deviceContext = EnterpriseDeviceContextProvider { context() },
        diagnostics = collector,
        ids = ids,
        nowEpochMillis = { NOW + 1 },
    )

    private fun enabledState() = EnterpriseUiState(
        availability = ProductAvailabilityUiState.Available,
        features = EnterpriseFeatureUiState(true, true, 9),
    )

    private fun bugInput() = BugReportInput(
        title = "Connection timeout",
        description = "Connection remains in the preparing state.",
        category = BugCategory.CONNECTION,
        automaticContextConsent = true,
    )

    private fun context() = PrivacySafeDeviceContext(
        deviceId = DEVICE_ID,
        appVersion = "1.0.0",
        osMajorVersion = "16",
        deviceClass = DeviceClass.PHONE,
        connectionState = SafeConnectionState.CONNECTING,
        networkType = SafeNetworkType.WIFI,
        serverId = SERVER_ID,
        errorCode = "connect_timeout",
    )

    private fun diagnosticTests() = listOf(
        PrivacySafeDiagnosticTest(DiagnosticKind.NETWORK, DiagnosticOutcome.PASSED, latencyMillis = 40),
        PrivacySafeDiagnosticTest(
            DiagnosticKind.PACKET_LOSS,
            DiagnosticOutcome.WARNING,
            packetLossRatio = 0.03,
            resultCode = "packet_loss_warning",
        ),
    )

    private fun bugReceipt() = BugReportReceipt(
        id = BUG_ID,
        publicCode = "BUG-2026",
        severity = BugSeverity.MEDIUM,
        status = BugStatus.NEW,
    )

    private fun diagnosticReceipt() = DiagnosticReportReceipt(
        id = DIAGNOSTIC_ID,
        redactionVersion = "redaction-v3",
        expiresAtEpochMillis = NOW + 86_400_000,
    )

    private class FakeRepository(
        var runtime: EnterpriseResult<VerifiedRuntimeConfiguration> = EnterpriseResult.Success(runtime()),
        var update: EnterpriseResult<VerifiedUpdatePolicy> = EnterpriseResult.Success(update(UpdateRequirement.OPTIONAL)),
        var bugResult: EnterpriseResult<BugReportReceipt> = EnterpriseResult.Failure(EnterpriseError.Server(true)),
        var diagnosticResult: EnterpriseResult<DiagnosticReportReceipt> =
            EnterpriseResult.Failure(EnterpriseError.Server(true)),
    ) : EnterpriseExperienceRepository {
        var bugCommand: CreateBugReportCommand? = null
        var diagnosticCommand: CreateDiagnosticReportCommand? = null

        override fun runtimeConfiguration() = runtime
        override fun updatePolicy() = update
        override fun createBugReport(command: CreateBugReportCommand): EnterpriseResult<BugReportReceipt> {
            bugCommand = command
            return bugResult
        }
        override fun createDiagnosticReport(
            command: CreateDiagnosticReportCommand,
        ): EnterpriseResult<DiagnosticReportReceipt> {
            diagnosticCommand = command
            return diagnosticResult
        }
    }

    private companion object {
        const val NOW = 1_800_000_000_000L
        const val REPORT_ID = "60000000-0000-4000-8000-000000000001"
        const val BUG_ID = "70000000-0000-4000-8000-000000000001"
        const val DIAGNOSTIC_ID = "80000000-0000-4000-8000-000000000001"
        const val DEVICE_ID = "30000000-0000-4000-8000-000000000001"
        const val SERVER_ID = "40000000-0000-4000-8000-000000000001"

        fun runtime(
            maintenance: Boolean = false,
            expiresAt: Long = NOW + 60_000,
        ) = VerifiedRuntimeConfiguration(
            version = 9,
            expiresAtEpochMillis = expiresAt,
            maintenance = maintenance,
            maintenanceMessage = if (maintenance) "Scheduled maintenance" else null,
            assignments = listOf(
                FeatureAssignment(EnterpriseFeatureKey.BUG_REPORTS, true, "control", 9),
                FeatureAssignment(EnterpriseFeatureKey.SUPPORT_DIAGNOSTICS, true, "control", 9),
            ),
            signatureVerified = true,
        )

        fun update(requirement: UpdateRequirement) = VerifiedUpdatePolicy(
            channel = ReleaseChannel.STABLE,
            currentVersion = "1.0.0",
            minimumVersion = "1.0.0",
            latestVersion = "1.2.0",
            requirement = requirement,
            reasonCode = "feature_release",
            signatureVerified = true,
        )
    }
}
