package com.ganj.vpn.ui

import android.webkit.CookieManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/** App-authorized identity exchange. Tokens never reach WebView JS or URLs. */
internal suspend fun botStoreAndroidSso(bearer: String): Boolean {
    if (!bearer.startsWith("Bearer ") || bearer.length !in 24..8199 ||
        !bearer.drop(7).matches(Regex("[A-Za-z0-9._~+/=-]+"))) return false

    val base = "https://bot.pedramhs.ir/site/api.php"
    val cookieJar = CookieManager.getInstance()
    suspend fun getCookie() = withContext(Dispatchers.Main) { cookieJar.getCookie(base) ?: "" }
    suspend fun rememberCookies(headers: Map<String?, List<String>>) {
        val setCookies = headers.filterKeys { it?.equals("set-cookie", true) == true }
            .values.flatten()
        withContext(Dispatchers.Main) {
            for (cookie in setCookies) cookieJar.setCookie(base, cookie)
            cookieJar.flush()
        }
    }
    suspend fun endpoint(action: String, csrf: String? = null): JSONObject? {
        val existingCookies = getCookie()
        return withContext(Dispatchers.IO) {
            val connection = (URL("$base?action=$action").openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 6500
                readTimeout = 7000
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-store")
                setRequestProperty("User-Agent", "GANJ-Android-Store/1")
                if (existingCookies.isNotBlank()) setRequestProperty("Cookie", existingCookies)
            }
            try {
                if (csrf != null) {
                    val encoded = "access_token=" +
                        URLEncoder.encode(bearer.removePrefix("Bearer "), "UTF-8")
                    connection.requestMethod = "POST"
                    connection.doOutput = true
                    connection.setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    connection.setRequestProperty("X-CSRF-Token", csrf)
                    connection.outputStream.use { it.write(encoded.toByteArray(Charsets.UTF_8)) }
                }
                val status = connection.responseCode
                val headers = connection.headerFields
                rememberCookies(headers)
                if (status != 200) return@withContext null
                val payload = connection.inputStream.bufferedReader(Charsets.UTF_8).use {
                    it.readText().take(16_384)
                }
                JSONObject(payload)
            } finally {
                connection.disconnect()
            }
        }
    }

    val csrf = endpoint("csrf")?.optJSONObject("data")?.optString("csrf")
        ?.takeIf { it.matches(Regex("[0-9a-f]{64}")) } ?: return false
    val exchange = endpoint("android_sso", csrf) ?: return false
    if (!exchange.optBoolean("status", false)) return false
    val who = exchange.optJSONObject("data") ?: return false
    return who.optString("id").matches(Regex("[0-9]{4,24}"))
}

internal fun botStoreClearSession() {
    val jar = CookieManager.getInstance()
    val root = "https://bot.pedramhs.ir/site/"
    for (name in listOf("GANJWEB", "GANJREM")) {
        jar.setCookie(root, "$name=; Max-Age=0; Path=/site/; Secure; HttpOnly; SameSite=Lax")
    }
    jar.flush()
}
