package com.ganj.vpn.enterprise

sealed interface ProductAvailabilityUiState {
    data object Loading : ProductAvailabilityUiState
    data object Available : ProductAvailabilityUiState
    data class Maintenance(val message: String?) : ProductAvailabilityUiState
    data class OptionalUpdate(val latestVersion: String, val reasonCode: String?) : ProductAvailabilityUiState
    data class ForcedUpdate(val minimumVersion: String, val latestVersion: String, val reasonCode: String?) :
        ProductAvailabilityUiState
    data object AuthRequired : ProductAvailabilityUiState
    data class Failed(val messageKey: String, val retryable: Boolean) : ProductAvailabilityUiState
}

data class EnterpriseFeatureUiState(
    val bugReportsEnabled: Boolean = false,
    val diagnosticsEnabled: Boolean = false,
    val configVersion: Long? = null,
)

sealed interface BugReportUiState {
    data object Idle : BugReportUiState
    data object Submitting : BugReportUiState
    data object AuthRequired : BugReportUiState
    data class Submitted(val publicCode: String, val status: BugStatus) : BugReportUiState
    data class Failed(val messageKey: String, val retryable: Boolean) : BugReportUiState
}

sealed interface DiagnosticUiState {
    data object Idle : DiagnosticUiState
    data object Running : DiagnosticUiState
    data object Uploading : DiagnosticUiState
    data object AuthRequired : DiagnosticUiState
    data class Submitted(val redactionVersion: String, val expiresAtEpochMillis: Long) : DiagnosticUiState
    data class Failed(val messageKey: String, val retryable: Boolean) : DiagnosticUiState
}

data class EnterpriseUiState(
    val availability: ProductAvailabilityUiState = ProductAvailabilityUiState.Loading,
    val features: EnterpriseFeatureUiState = EnterpriseFeatureUiState(),
    val bugReport: BugReportUiState = BugReportUiState.Idle,
    val diagnostic: DiagnosticUiState = DiagnosticUiState.Idle,
    val refreshing: Boolean = false,
)

data class BugReportInput(
    val title: String,
    val description: String,
    val category: BugCategory,
    val automaticContextConsent: Boolean,
)
