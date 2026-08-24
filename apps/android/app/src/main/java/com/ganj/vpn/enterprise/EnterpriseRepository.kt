package com.ganj.vpn.enterprise

sealed interface EnterpriseResult<out T> {
    data class Success<T>(val value: T) : EnterpriseResult<T>
    data class Failure(val error: EnterpriseError) : EnterpriseResult<Nothing>
}

sealed interface EnterpriseError {
    data object AuthenticationRequired : EnterpriseError
    data class Network(val retryable: Boolean = true) : EnterpriseError
    data class RateLimited(val retryAfterSeconds: Long?) : EnterpriseError
    data class Server(val retryable: Boolean) : EnterpriseError
    data class Protocol(val reason: String) : EnterpriseError
    data class Configuration(val reason: String) : EnterpriseError
}

interface EnterpriseExperienceRepository {
    fun runtimeConfiguration(): EnterpriseResult<VerifiedRuntimeConfiguration>
    fun updatePolicy(): EnterpriseResult<VerifiedUpdatePolicy>
    fun createBugReport(command: CreateBugReportCommand): EnterpriseResult<BugReportReceipt>
    fun createDiagnosticReport(
        command: CreateDiagnosticReportCommand,
    ): EnterpriseResult<DiagnosticReportReceipt>
}

class FailClosedEnterpriseRepository(
    private val reason: String = "enterprise_repository_not_configured",
) : EnterpriseExperienceRepository {
    override fun runtimeConfiguration(): EnterpriseResult<VerifiedRuntimeConfiguration> = failure()
    override fun updatePolicy(): EnterpriseResult<VerifiedUpdatePolicy> = failure()
    override fun createBugReport(command: CreateBugReportCommand): EnterpriseResult<BugReportReceipt> = failure()
    override fun createDiagnosticReport(
        command: CreateDiagnosticReportCommand,
    ): EnterpriseResult<DiagnosticReportReceipt> = failure()

    private fun failure(): EnterpriseResult.Failure =
        EnterpriseResult.Failure(EnterpriseError.Configuration(reason))
}
