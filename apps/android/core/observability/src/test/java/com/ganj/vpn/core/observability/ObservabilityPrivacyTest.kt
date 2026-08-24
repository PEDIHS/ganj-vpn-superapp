package com.ganj.vpn.core.observability

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ObservabilityPrivacyTest {
    private val factory = BugReportEnvelopeFactory()

    @Test
    fun `bug report requires one-time support consent`() {
        val result = factory.create(validDraft().copy(consent = SupportConsent.DENIED))

        assertEquals(
            RejectionReason.CONSENT_REQUIRED,
            (result as DiagnosticEnvelopeResult.Rejected).reason,
        )
    }

    @Test
    fun `valid bug report hashes installation identifier`() {
        val source = validDraft()
        val result = factory.create(source) as DiagnosticEnvelopeResult.Ready

        assertEquals(64, result.envelope.installationReference.length)
        assertNotEquals(source.device.installationId, result.envelope.installationReference)
        assertFalse(result.envelope.toString().contains(source.device.installationId))
    }

    @Test
    fun `vpn uri is removed from user description`() {
        val result = factory.create(
            validDraft().copy(description = "Fails with vless://uuid@example.test:443?security=tls"),
        ) as DiagnosticEnvelopeResult.Ready

        assertEquals("Fails with [REDACTED_VPN_PROFILE]", result.envelope.description)
    }

    @Test
    fun `bearer and jwt are removed from user description`() {
        val result = factory.create(
            validDraft().copy(
                description = "Bearer abcdefghijklmnop eyJabcdefgh.abcdefgh.abcdefgh",
            ),
        ) as DiagnosticEnvelopeResult.Ready

        assertFalse(result.envelope.description.contains("abcdefghijklmnop"))
        assertFalse(result.envelope.description.contains("eyJabcdefgh"))
    }

    @Test
    fun `unsafe server identifier is rejected`() {
        val result = factory.create(
            validDraft().copy(
                connection = validDraft().connection.copy(serverPublicId = "server with spaces"),
            ),
        )

        assertEquals(
            RejectionReason.UNSAFE_CONNECTION_DATA,
            (result as DiagnosticEnvelopeResult.Rejected).reason,
        )
    }

    @Test
    fun `attachment handle is never copied to payload or toString`() {
        val draft = validDraft().copy(
            attachments = listOf(
                PendingAttachment(
                    opaqueHandle = "private-content-handle",
                    kind = AttachmentKind.SCREENSHOT,
                    displayName = "screen.png",
                    mediaType = "image/png",
                    sizeBytes = 1200,
                ),
            ),
        )
        val result = factory.create(draft) as DiagnosticEnvelopeResult.Ready

        assertFalse(result.envelope.toString().contains("private-content-handle"))
        assertFalse(draft.attachments.single().toString().contains("private-content-handle"))
    }

    @Test
    fun `unsupported attachment content type is rejected`() {
        val result = factory.create(
            validDraft().copy(
                attachments = listOf(
                    PendingAttachment(
                        opaqueHandle = "handle-001",
                        kind = AttachmentKind.DOCUMENT,
                        displayName = "archive.zip",
                        mediaType = "application/zip",
                        sizeBytes = 1200,
                    ),
                ),
            ),
        )

        assertEquals(
            RejectionReason.UNSAFE_ATTACHMENT,
            (result as DiagnosticEnvelopeResult.Rejected).reason,
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun `oversized attachment is rejected at construction`() {
        PendingAttachment(
            opaqueHandle = "handle-001",
            kind = AttachmentKind.SCREEN_RECORDING,
            displayName = "recording.mp4",
            mediaType = "video/mp4",
            sizeBytes = PendingAttachment.MAX_ATTACHMENT_BYTES + 1,
        )
    }

    @Test
    fun `analytics is disabled without consent`() {
        val sink = RecordingAnalyticsSink()
        val analytics = ConsentAwareAnalytics(sink)

        val accepted = analytics.track(
            AnalyticsConsent.DENIED,
            ProductEvent(ProductEventName.APP_OPENED, 0L),
        )

        assertFalse(accepted)
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `analytics accepts allowlisted coarse property`() {
        val sink = RecordingAnalyticsSink()
        val analytics = ConsentAwareAnalytics(sink)

        val accepted = analytics.track(
            AnalyticsConsent.GRANTED,
            ProductEvent(
                ProductEventName.VPN_CONNECTED,
                0L,
                mapOf("network_type" to "wifi", "plan_tier" to "premium"),
            ),
        )

        assertTrue(accepted)
        assertEquals(1, sink.events.size)
    }

    @Test
    fun `analytics rejects unknown property and purchase token`() {
        val sink = RecordingAnalyticsSink()
        val analytics = ConsentAwareAnalytics(sink)

        assertFalse(
            analytics.track(
                AnalyticsConsent.GRANTED,
                ProductEvent(
                    ProductEventName.CHECKOUT_STARTED,
                    0L,
                    mapOf("purchase_token" to "secret-value"),
                ),
            ),
        )
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `analytics rejects secret hidden in allowed property`() {
        val sink = RecordingAnalyticsSink()
        val analytics = ConsentAwareAnalytics(sink)

        assertFalse(
            analytics.track(
                AnalyticsConsent.GRANTED,
                ProductEvent(
                    ProductEventName.VPN_CONNECTION_FAILED,
                    0L,
                    mapOf("error_code" to "access_token=abcdefghijklmnop"),
                ),
            ),
        )
    }

    @Test
    fun `logger accepts allowlisted operational metadata`() {
        val sink = RecordingLogSink()
        val logger = AllowlistedLogger(sink)

        assertTrue(
            logger.log(
                SafeLogEvent(
                    LogLevel.ERROR,
                    "VPN_CONNECT_FAILED",
                    0L,
                    mapOf("error_code" to "TIMEOUT", "retry_count" to "2"),
                ),
            ),
        )
        assertEquals(1, sink.events.size)
    }

    @Test
    fun `logger rejects unknown field and vpn profile`() {
        val sink = RecordingLogSink()
        val logger = AllowlistedLogger(sink)

        assertFalse(
            logger.log(
                SafeLogEvent(
                    LogLevel.DEBUG,
                    "VPN_PROFILE",
                    0L,
                    mapOf("config" to "vless://user@example.test"),
                ),
            ),
        )
        assertTrue(sink.events.isEmpty())
    }

    @Test
    fun `logger rejects secret even inside allowlisted attribute`() {
        val sink = RecordingLogSink()
        val logger = AllowlistedLogger(sink)

        assertFalse(
            logger.log(
                SafeLogEvent(
                    LogLevel.ERROR,
                    "CHECKOUT_FAILED",
                    0L,
                    mapOf("error_code" to "purchase_token=abcdefghijklmnop"),
                ),
            ),
        )
    }

    private fun validDraft() = BugReportDraft(
        consent = SupportConsent.GRANTED_ONCE,
        description = "Connection fails after switching networks",
        device = DeviceDiagnostics(
            installationId = "installation-123",
            model = "Pixel 10",
            osVersion = "Android 16",
            appVersion = "0.2.0",
        ),
        connection = ConnectionDiagnostics(
            status = ConnectionStatus.FAILED,
            serverPublicId = "de-fra-001",
            networkKind = NetworkKind.WIFI,
            errorCode = "VPN_TIMEOUT",
        ),
        crashReference = "crash-1024",
        occurredAtEpochMillis = 1_777_190_400_000L,
    )

    private class RecordingAnalyticsSink : ProductEventSink {
        val events = mutableListOf<ProductEvent>()
        override fun send(event: ProductEvent) {
            events += event
        }
    }

    private class RecordingLogSink : SafeLogSink {
        val events = mutableListOf<SafeLogEvent>()
        override fun write(event: SafeLogEvent) {
            events += event
        }
    }
}
