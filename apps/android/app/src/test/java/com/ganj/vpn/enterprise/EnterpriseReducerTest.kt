package com.ganj.vpn.enterprise

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnterpriseReducerTest {
    private val reducer = EnterpriseReducer()

    @Test
    fun `refresh and experience resolution are explicit`() {
        val loading = reducer.reduce(EnterpriseUiState(), EnterpriseEvent.RefreshRequested)
        val ready = reducer.reduce(
            loading,
            EnterpriseEvent.ExperienceResolved(
                ProductAvailabilityUiState.Available,
                EnterpriseFeatureUiState(true, true, 9),
            ),
        )

        assertTrue(loading.refreshing)
        assertEquals(ProductAvailabilityUiState.Available, ready.availability)
        assertEquals(9L, ready.features.configVersion)
        assertEquals(false, ready.refreshing)
    }

    @Test
    fun `bug report lifecycle supports submitting success failure and reset`() {
        val submitting = reducer.reduce(EnterpriseUiState(), EnterpriseEvent.BugSubmissionStarted)
        val submitted = reducer.reduce(submitting, EnterpriseEvent.BugSubmitted(bugReceipt()))
        val reset = reducer.reduce(submitted, EnterpriseEvent.ClearBugResult)
        val failed = reducer.reduce(
            reset,
            EnterpriseEvent.BugFailed("network.unavailable", retryable = true),
        )

        assertEquals(BugReportUiState.Submitting, submitting.bugReport)
        assertEquals(BugReportUiState.Submitted("BUG-1024", BugStatus.NEW), submitted.bugReport)
        assertEquals(BugReportUiState.Idle, reset.bugReport)
        assertEquals(BugReportUiState.Failed("network.unavailable", true), failed.bugReport)
    }

    @Test
    fun `diagnostic lifecycle distinguishes running uploading and submitted`() {
        val running = reducer.reduce(EnterpriseUiState(), EnterpriseEvent.DiagnosticStarted)
        val uploading = reducer.reduce(running, EnterpriseEvent.DiagnosticUploading)
        val submitted = reducer.reduce(uploading, EnterpriseEvent.DiagnosticSubmitted(diagnosticReceipt()))

        assertEquals(DiagnosticUiState.Running, running.diagnostic)
        assertEquals(DiagnosticUiState.Uploading, uploading.diagnostic)
        assertEquals(DiagnosticUiState.Submitted("redaction-v2", NOW + 60_000), submitted.diagnostic)
    }

    private fun bugReceipt() = BugReportReceipt(
        id = "70000000-0000-4000-8000-000000000001",
        publicCode = "BUG-1024",
        severity = BugSeverity.MEDIUM,
        status = BugStatus.NEW,
    )

    private fun diagnosticReceipt() = DiagnosticReportReceipt(
        id = "80000000-0000-4000-8000-000000000001",
        redactionVersion = "redaction-v2",
        expiresAtEpochMillis = NOW + 60_000,
    )

    private companion object {
        const val NOW = 1_800_000_000_000L
    }
}
