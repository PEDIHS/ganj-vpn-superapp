package com.ganj.vpn.core.observability

import java.security.MessageDigest

class BugReportEnvelopeFactory(
    private val redactor: SensitiveDataRedactor = SensitiveDataRedactor(),
) {
    fun create(draft: BugReportDraft): DiagnosticEnvelopeResult {
        if (draft.consent != SupportConsent.GRANTED_ONCE) {
            return DiagnosticEnvelopeResult.Rejected(RejectionReason.CONSENT_REQUIRED)
        }

        val description = redactor.redact(draft.description.trim())
        if (description.length !in 3..SensitiveDataRedactor.DEFAULT_MAX_LENGTH) {
            return DiagnosticEnvelopeResult.Rejected(RejectionReason.INVALID_DESCRIPTION)
        }

        if (!isSafeIdentifier(draft.device.installationId, 128) ||
            !isSafeLabel(draft.device.model, 100) ||
            !isSafeLabel(draft.device.osVersion, 60) ||
            !isSafeLabel(draft.device.appVersion, 60)
        ) {
            return DiagnosticEnvelopeResult.Rejected(RejectionReason.UNSAFE_DEVICE_DATA)
        }

        if ((draft.connection.serverPublicId != null &&
                !isSafeIdentifier(draft.connection.serverPublicId, 100)) ||
            (draft.connection.errorCode != null &&
                !isSafeIdentifier(draft.connection.errorCode, 80)) ||
            (draft.crashReference != null && !isSafeIdentifier(draft.crashReference, 128))
        ) {
            return DiagnosticEnvelopeResult.Rejected(RejectionReason.UNSAFE_CONNECTION_DATA)
        }

        val attachments = draft.attachments.map { attachment ->
            if (redactor.containsForbiddenMaterial(attachment.displayName) ||
                attachment.mediaType !in ALLOWED_MEDIA_TYPES
            ) {
                return DiagnosticEnvelopeResult.Rejected(RejectionReason.UNSAFE_ATTACHMENT)
            }
            SafeAttachmentMetadata(
                kind = attachment.kind,
                displayName = redactor.redact(attachment.displayName, 120),
                mediaType = attachment.mediaType,
                sizeBytes = attachment.sizeBytes,
            )
        }

        return DiagnosticEnvelopeResult.Ready(
            SafeBugReportEnvelope(
                description = description,
                installationReference = sha256(draft.device.installationId),
                deviceModel = draft.device.model,
                osVersion = draft.device.osVersion,
                appVersion = draft.device.appVersion,
                connectionStatus = draft.connection.status,
                serverPublicId = draft.connection.serverPublicId,
                networkKind = draft.connection.networkKind,
                errorCode = draft.connection.errorCode,
                crashReference = draft.crashReference,
                occurredAtEpochMillis = draft.occurredAtEpochMillis,
                attachments = attachments,
            ),
        )
    }

    private fun isSafeIdentifier(value: String, maxLength: Int): Boolean =
        value.length in 3..maxLength && SAFE_IDENTIFIER.matches(value)

    private fun isSafeLabel(value: String, maxLength: Int): Boolean =
        value.isNotBlank() && value.length <= maxLength &&
            !redactor.containsForbiddenMaterial(value) && !value.contains('\n')

    private fun sha256(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }

    companion object {
        private val SAFE_IDENTIFIER = Regex("[A-Za-z0-9][A-Za-z0-9._:-]*")
        private val ALLOWED_MEDIA_TYPES = setOf(
            "image/jpeg",
            "image/png",
            "video/mp4",
            "text/plain",
            "application/pdf",
        )
    }
}
