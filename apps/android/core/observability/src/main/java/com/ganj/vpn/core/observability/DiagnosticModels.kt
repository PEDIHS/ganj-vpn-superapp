package com.ganj.vpn.core.observability

enum class SupportConsent {
    NOT_ASKED,
    DENIED,
    GRANTED_ONCE,
}

enum class AttachmentKind {
    SCREENSHOT,
    SCREEN_RECORDING,
    DOCUMENT,
}

enum class NetworkKind {
    WIFI,
    MOBILE,
    ETHERNET,
    OFFLINE,
    UNKNOWN,
}

enum class ConnectionStatus {
    DISCONNECTED,
    CONNECTING,
    CONNECTED,
    RECONNECTING,
    FAILED,
}

enum class BugSeverity {
    CRITICAL,
    HIGH,
    MEDIUM,
    LOW,
}

enum class BugLifecycle {
    NEW,
    INVESTIGATING,
    ASSIGNED,
    FIXING,
    TESTING,
    RELEASED,
    CLOSED,
}

data class DeviceDiagnostics(
    val installationId: String,
    val model: String,
    val osVersion: String,
    val appVersion: String,
)

data class ConnectionDiagnostics(
    val status: ConnectionStatus,
    val serverPublicId: String?,
    val networkKind: NetworkKind,
    val errorCode: String?,
)

/**
 * A local content URI or file descriptor is never part of the report payload.
 * The upload coordinator resolves [opaqueHandle] only after separate consent.
 */
data class PendingAttachment(
    val opaqueHandle: String,
    val kind: AttachmentKind,
    val displayName: String,
    val mediaType: String,
    val sizeBytes: Long,
) {
    init {
        require(opaqueHandle.isNotBlank() && opaqueHandle.length <= 128)
        require(displayName.isNotBlank() && displayName.length <= 120)
        require(mediaType.length in 3..100)
        require(sizeBytes in 1..MAX_ATTACHMENT_BYTES)
    }

    override fun toString(): String =
        "PendingAttachment(kind=$kind, mediaType=$mediaType, sizeBytes=$sizeBytes)"

    companion object {
        const val MAX_ATTACHMENT_BYTES: Long = 25L * 1024L * 1024L
    }
}

data class BugReportDraft(
    val consent: SupportConsent,
    val description: String,
    val device: DeviceDiagnostics,
    val connection: ConnectionDiagnostics,
    val crashReference: String?,
    val occurredAtEpochMillis: Long,
    val attachments: List<PendingAttachment> = emptyList(),
) {
    init {
        require(attachments.size <= 5)
    }
}

data class SafeAttachmentMetadata(
    val kind: AttachmentKind,
    val displayName: String,
    val mediaType: String,
    val sizeBytes: Long,
)

data class SafeBugReportEnvelope(
    val description: String,
    val installationReference: String,
    val deviceModel: String,
    val osVersion: String,
    val appVersion: String,
    val connectionStatus: ConnectionStatus,
    val serverPublicId: String?,
    val networkKind: NetworkKind,
    val errorCode: String?,
    val crashReference: String?,
    val occurredAtEpochMillis: Long,
    val attachments: List<SafeAttachmentMetadata>,
)

sealed interface DiagnosticEnvelopeResult {
    data class Ready(val envelope: SafeBugReportEnvelope) : DiagnosticEnvelopeResult
    data class Rejected(val reason: RejectionReason) : DiagnosticEnvelopeResult
}

enum class RejectionReason {
    CONSENT_REQUIRED,
    INVALID_DESCRIPTION,
    UNSAFE_DEVICE_DATA,
    UNSAFE_CONNECTION_DATA,
    UNSAFE_ATTACHMENT,
}
