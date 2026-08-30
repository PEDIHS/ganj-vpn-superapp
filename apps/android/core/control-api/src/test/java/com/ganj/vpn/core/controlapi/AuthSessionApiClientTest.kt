package com.ganj.vpn.core.controlapi

import java.nio.charset.StandardCharsets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthSessionApiClientTest {
    private val sessionJson = """
        {
          "user_id":"00000000-0000-4000-8000-000000000010",
          "device_id":"$DEVICE_ID",
          "access_token":"header.payload.signature-value",
          "access_token_expires_at":"2026-08-28T04:15:00Z",
          "refresh_token":"rrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrrr",
          "refresh_token_expires_at":"2026-09-27T04:00:00Z",
          "token_type":"Bearer"
        }
    """.trimIndent()

    @Test
    fun `guest maps session and does not send bearer authorization`() {
        val transport = FakeTransport().apply { enqueue(201, successEnvelope(sessionJson)) }
        val api = DefaultAuthSessionApi(transport)

        val result = api.createGuest(
            GuestSessionCommand(
                deviceId = DEVICE_ID,
                keyVersion = "v1",
                signingPublicKeySpki = "S".repeat(96),
                encryptionPublicKeyRaw = "E".repeat(43),
                deviceProof = "gdp1." + "P".repeat(90),
            ),
        ).requireSuccess()

        assertEquals("00000000-0000-4000-8000-000000000010", result.value.userId)
        assertEquals(DEVICE_ID, result.value.deviceId)
        assertFalse(transport.requests.single().headers.containsKey("Authorization"))
        assertEquals("/auth/guest", transport.requests.single().pathAndQuery)
        assertFalse(result.value.toString().contains("signature-value"))
        assertFalse(result.value.toString().contains("rrrrr"))
    }

    @Test
    fun `refresh sends rotating secret only in redacted request body boundary`() {
        val transport = FakeTransport().apply { enqueue(200, successEnvelope(sessionJson)) }
        val api = DefaultAuthSessionApi(transport)
        val refresh = RefreshToken.from("q".repeat(43))

        api.refresh(
            RefreshSessionCommand(
                refreshToken = refresh,
                deviceId = DEVICE_ID,
                deviceProof = "gdp1." + "P".repeat(90),
            ),
        ).requireSuccess()

        val request = transport.requests.single()
        assertEquals("/auth/refresh", request.pathAndQuery)
        val rawBody = String(requireNotNull(request.body), StandardCharsets.UTF_8)
        assertTrue(rawBody.contains("\"refresh_token\":\"${"q".repeat(43)}\""))
        assertFalse(request.toString().contains("q".repeat(20)))
        assertFalse(request.headers.containsKey("Authorization"))
    }

    @Test
    fun `current account uses bearer and maps only privacy-safe identity fields`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope(
                    """{
                      "id":"00000000-0000-4000-8000-000000000010",
                      "status":"active",
                      "display_name":"کاربر گنج",
                      "locale":"fa-IR",
                      "telegram_linked":true,
                      "telegram_username":"ganj_user"
                    }""".trimIndent(),
                ),
            )
        }
        val api = DefaultAuthSessionApi(transport)

        val result = api.currentAccount(TOKEN).requireSuccess()

        assertEquals("00000000-0000-4000-8000-000000000010", result.value.id)
        assertEquals("کاربر گنج", result.value.displayName)
        assertEquals("fa-IR", result.value.locale)
        assertTrue(result.value.telegramLinked)
        assertEquals("ganj_user", result.value.telegramUsername)
        assertEquals("/me", transport.requests.single().pathAndQuery)
        assertEquals("Bearer header.payload.signature-value", transport.requests.single().headers["Authorization"])
        assertFalse(result.value.toString().contains("کاربر گنج"))
        assertFalse(result.value.toString().contains("ganj_user"))
    }

    @Test
    fun `Telegram start is bearer authenticated but exchange is public`() {
        val transport = FakeTransport().apply {
            enqueue(
                200,
                successEnvelope(
                    """{"authorization_url":"https://oauth.telegram.org/auth?state=abc","state":"${"s".repeat(43)}","expires_at":"2026-08-28T04:10:00Z"}""",
                ),
            )
            enqueue(200, successEnvelope(sessionJson))
        }
        val api = DefaultAuthSessionApi(transport)

        val authorization = api.beginTelegram(
            accessToken = TOKEN,
            command = TelegramAuthorizationCommand(
                codeChallenge = "c".repeat(43),
                redirectUri = "https://auth.ganj.example/ganj/telegram/callback",
            ),
        ).requireSuccess()
        assertEquals("s".repeat(43), authorization.value.state)
        assertEquals("Bearer header.payload.signature-value", transport.requests[0].headers["Authorization"])

        api.exchangeTelegram(
            TelegramExchangeCommand(
                code = "telegram-code",
                state = "s".repeat(43),
                codeVerifier = "v".repeat(43),
            ),
        ).requireSuccess()
        assertFalse(transport.requests[1].headers.containsKey("Authorization"))
        assertEquals("/auth/telegram/exchange", transport.requests[1].pathAndQuery)
    }

    @Test
    fun `logout uses bearer token and maps explicit revocation result`() {
        val transport = FakeTransport().apply {
            enqueue(200, successEnvelope("""{"logged_out":true}"""))
        }
        val api = DefaultAuthSessionApi(transport)

        val result = api.logout(TOKEN).requireSuccess()

        assertTrue(result.value)
        assertEquals("Bearer header.payload.signature-value", transport.requests.single().headers["Authorization"])
        assertEquals("/auth/logout", transport.requests.single().pathAndQuery)
    }

    @Test
    fun `auth errors preserve safe server code without exposing response body`() {
        val transport = FakeTransport().apply {
            enqueue(401, errorEnvelope("refresh_token_reuse_detected"))
        }
        val api = DefaultAuthSessionApi(transport)

        val result = api.refresh(
            RefreshSessionCommand(
                refreshToken = RefreshToken.from("q".repeat(43)),
                deviceId = DEVICE_ID,
                deviceProof = "gdp1." + "P".repeat(90),
            ),
        ).requireFailure()

        val error = result.error as ApiError.AuthenticationExpired
        assertEquals("refresh_token_reuse_detected", error.code)
        assertEquals(REQUEST_ID, error.requestId)
    }
}
