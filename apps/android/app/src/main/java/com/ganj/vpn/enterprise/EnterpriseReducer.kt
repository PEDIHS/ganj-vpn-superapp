package com.ganj.vpn.enterprise

sealed interface EnterpriseEvent {
    data object RefreshRequested : EnterpriseEvent
    data class ExperienceResolved(
        val availability: ProductAvailabilityUiState,
        val features: EnterpriseFeatureUiState,
    ) : EnterpriseEvent
    data object BugSubmissionStarted : EnterpriseEvent
    data object BugAuthenticationRequired : EnterpriseEvent
    data class BugSubmitted(val receipt: BugReportReceipt) : EnterpriseEvent
    data class BugFailed(val messageKey: String, val retryable: Boolean) : EnterpriseEvent
    data object DiagnosticStarted : EnterpriseEvent
    data object DiagnosticUploading : EnterpriseEvent
    data object DiagnosticAuthenticationRequired : EnterpriseEvent
    data class DiagnosticSubmitted(val receipt: DiagnosticReportReceipt) : EnterpriseEvent
    data class DiagnosticFailed(val messageKey: String, val retryable: Boolean) : EnterpriseEvent
    data object ClearBugResult : EnterpriseEvent
    data object ClearDiagnosticResult : EnterpriseEvent
}

class EnterpriseReducer {
    fun reduce(state: EnterpriseUiState, event: EnterpriseEvent): EnterpriseUiState = when (event) {
        EnterpriseEvent.RefreshRequested -> state.copy(refreshing = true)
        is EnterpriseEvent.ExperienceResolved -> state.copy(
            availability = event.availability,
            features = event.features,
            refreshing = false,
        )
        EnterpriseEvent.BugSubmissionStarted -> state.copy(bugReport = BugReportUiState.Submitting)
        EnterpriseEvent.BugAuthenticationRequired -> state.copy(bugReport = BugReportUiState.AuthRequired)
        is EnterpriseEvent.BugSubmitted -> state.copy(
            bugReport = BugReportUiState.Submitted(event.receipt.publicCode, event.receipt.status),
        )
        is EnterpriseEvent.BugFailed -> state.copy(
            bugReport = BugReportUiState.Failed(event.messageKey, event.retryable),
        )
        EnterpriseEvent.DiagnosticStarted -> state.copy(diagnostic = DiagnosticUiState.Running)
        EnterpriseEvent.DiagnosticUploading -> state.copy(diagnostic = DiagnosticUiState.Uploading)
        EnterpriseEvent.DiagnosticAuthenticationRequired -> state.copy(diagnostic = DiagnosticUiState.AuthRequired)
        is EnterpriseEvent.DiagnosticSubmitted -> state.copy(
            diagnostic = DiagnosticUiState.Submitted(
                event.receipt.redactionVersion,
                event.receipt.expiresAtEpochMillis,
            ),
        )
        is EnterpriseEvent.DiagnosticFailed -> state.copy(
            diagnostic = DiagnosticUiState.Failed(event.messageKey, event.retryable),
        )
        EnterpriseEvent.ClearBugResult -> state.copy(bugReport = BugReportUiState.Idle)
        EnterpriseEvent.ClearDiagnosticResult -> state.copy(diagnostic = DiagnosticUiState.Idle)
    }
}
