package com.ganj.vpn.enterprise

import com.ganj.vpn.presentation.StableIdGenerator

fun interface EnterpriseSessionGate {
    fun isReady(): Boolean
}

fun interface EnterpriseDeviceContextProvider {
    fun current(): PrivacySafeDeviceContext?
}

fun interface PrivacySafeDiagnosticCollector {
    fun collect(): List<PrivacySafeDiagnosticTest>
}

class EnterpriseController(
    private val repository: EnterpriseExperienceRepository,
    private val session: EnterpriseSessionGate,
    private val deviceContext: EnterpriseDeviceContextProvider,
    private val diagnostics: PrivacySafeDiagnosticCollector,
    private val ids: StableIdGenerator,
    private val nowEpochMillis: () -> Long,
    private val reducer: EnterpriseReducer = EnterpriseReducer(),
) {
    fun refresh(state: EnterpriseUiState): EnterpriseUiState {
        val runtime = repository.runtimeConfiguration()
        val update = repository.updatePolicy()
        val availability = mapAvailability(runtime, update)
        val features = mapFeatures(runtime)
        return reducer.reduce(state, EnterpriseEvent.ExperienceResolved(availability, features))
    }

    fun submitBug(state: EnterpriseUiState, input: BugReportInput): EnterpriseUiState {
        val working = reducer.reduce(state, EnterpriseEvent.BugSubmissionStarted)
        if (!state.features.bugReportsEnabled) {
            return reducer.reduce(working, EnterpriseEvent.BugFailed("bug_report.disabled", false))
        }
        if (!session.isReady()) return reducer.reduce(working, EnterpriseEvent.BugAuthenticationRequired)
        if (!input.automaticContextConsent) {
            return reducer.reduce(working, EnterpriseEvent.BugFailed("privacy.consent_required", false))
        }
        val context = deviceContext.current()
            ?: return reducer.reduce(working, EnterpriseEvent.BugFailed("diagnostic.context_unavailable", true))
        val title = input.title.trim()
        val description = input.description.trim()
        val command = try {
            CreateBugReportCommand(
                clientReportId = ids.next(),
                title = title,
                description = description,
                category = input.category,
                occurredAtEpochMillis = nowEpochMillis(),
                automaticContextConsent = true,
                context = context,
            )
        } catch (_: IllegalArgumentException) {
            return reducer.reduce(working, EnterpriseEvent.BugFailed("bug_report.invalid_or_sensitive", false))
        }
        return when (val result = repository.createBugReport(command)) {
            is EnterpriseResult.Success -> reducer.reduce(working, EnterpriseEvent.BugSubmitted(result.value))
            is EnterpriseResult.Failure -> reduceBugFailure(working, result.error)
        }
    }

    fun submitDiagnostics(state: EnterpriseUiState, explicitConsent: Boolean): EnterpriseUiState {
        var working = reducer.reduce(state, EnterpriseEvent.DiagnosticStarted)
        if (!state.features.diagnosticsEnabled) {
            return reducer.reduce(working, EnterpriseEvent.DiagnosticFailed("diagnostic.disabled", false))
        }
        if (!session.isReady()) return reducer.reduce(working, EnterpriseEvent.DiagnosticAuthenticationRequired)
        if (!explicitConsent) {
            return reducer.reduce(working, EnterpriseEvent.DiagnosticFailed("privacy.consent_required", false))
        }
        val context = deviceContext.current()
            ?: return reducer.reduce(working, EnterpriseEvent.DiagnosticFailed("diagnostic.context_unavailable", true))
        val started = nowEpochMillis()
        val tests = try {
            diagnostics.collect()
        } catch (_: RuntimeException) {
            return reducer.reduce(working, EnterpriseEvent.DiagnosticFailed("diagnostic.collection_failed", true))
        }
        val completed = nowEpochMillis().coerceAtLeast(started)
        val command = try {
            CreateDiagnosticReportCommand(
                clientReportId = ids.next(),
                deviceId = context.deviceId,
                explicitConsent = true,
                startedAtEpochMillis = started,
                completedAtEpochMillis = completed,
                tests = tests,
            )
        } catch (_: IllegalArgumentException) {
            return reducer.reduce(working, EnterpriseEvent.DiagnosticFailed("diagnostic.invalid", false))
        }
        working = reducer.reduce(working, EnterpriseEvent.DiagnosticUploading)
        return when (val result = repository.createDiagnosticReport(command)) {
            is EnterpriseResult.Success -> reducer.reduce(working, EnterpriseEvent.DiagnosticSubmitted(result.value))
            is EnterpriseResult.Failure -> reduceDiagnosticFailure(working, result.error)
        }
    }

    private fun mapAvailability(
        runtime: EnterpriseResult<VerifiedRuntimeConfiguration>,
        update: EnterpriseResult<VerifiedUpdatePolicy>,
    ): ProductAvailabilityUiState {
        if (update is EnterpriseResult.Success && update.value.requirement == UpdateRequirement.FORCED) {
            return ProductAvailabilityUiState.ForcedUpdate(
                update.value.minimumVersion,
                update.value.latestVersion,
                update.value.reasonCode,
            )
        }
        if (runtime is EnterpriseResult.Success) {
            if (runtime.value.expiresAtEpochMillis <= nowEpochMillis()) {
                return ProductAvailabilityUiState.Failed("runtime_config.expired", true)
            }
            if (runtime.value.maintenance) {
                return ProductAvailabilityUiState.Maintenance(runtime.value.maintenanceMessage)
            }
        }
        if (update is EnterpriseResult.Success && update.value.requirement == UpdateRequirement.OPTIONAL) {
            return ProductAvailabilityUiState.OptionalUpdate(update.value.latestVersion, update.value.reasonCode)
        }
        if (runtime is EnterpriseResult.Failure) return mapAvailabilityFailure(runtime.error)
        if (update is EnterpriseResult.Failure) return mapAvailabilityFailure(update.error)
        return ProductAvailabilityUiState.Available
    }

    private fun mapFeatures(
        runtime: EnterpriseResult<VerifiedRuntimeConfiguration>,
    ): EnterpriseFeatureUiState = if (
        runtime is EnterpriseResult.Success && runtime.value.expiresAtEpochMillis > nowEpochMillis()
    ) {
        EnterpriseFeatureUiState(
            bugReportsEnabled = runtime.value.enabled(EnterpriseFeatureKey.BUG_REPORTS),
            diagnosticsEnabled = runtime.value.enabled(EnterpriseFeatureKey.SUPPORT_DIAGNOSTICS),
            configVersion = runtime.value.version,
        )
    } else {
        EnterpriseFeatureUiState()
    }

    private fun reduceBugFailure(state: EnterpriseUiState, error: EnterpriseError): EnterpriseUiState =
        if (error is EnterpriseError.AuthenticationRequired) {
            reducer.reduce(state, EnterpriseEvent.BugAuthenticationRequired)
        } else {
            val mapped = mapFailure(error)
            reducer.reduce(state, EnterpriseEvent.BugFailed(mapped.first, mapped.second))
        }

    private fun reduceDiagnosticFailure(
        state: EnterpriseUiState,
        error: EnterpriseError,
    ): EnterpriseUiState = if (error is EnterpriseError.AuthenticationRequired) {
        reducer.reduce(state, EnterpriseEvent.DiagnosticAuthenticationRequired)
    } else {
        val mapped = mapFailure(error)
        reducer.reduce(state, EnterpriseEvent.DiagnosticFailed(mapped.first, mapped.second))
    }

    private fun mapAvailabilityFailure(error: EnterpriseError): ProductAvailabilityUiState = when (error) {
        EnterpriseError.AuthenticationRequired -> ProductAvailabilityUiState.AuthRequired
        else -> mapFailure(error).let { ProductAvailabilityUiState.Failed(it.first, it.second) }
    }

    private fun mapFailure(error: EnterpriseError): Pair<String, Boolean> = when (error) {
        EnterpriseError.AuthenticationRequired -> "auth.required" to false
        is EnterpriseError.Network -> "network.unavailable" to error.retryable
        is EnterpriseError.RateLimited -> "request.rate_limited" to true
        is EnterpriseError.Server -> "server.unavailable" to error.retryable
        is EnterpriseError.Protocol -> "response.invalid" to false
        is EnterpriseError.Configuration -> "enterprise.unavailable" to false
    }
}
