package com.ganj.vpn.ui

import android.content.Context
import android.net.Uri
import android.webkit.CookieManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URL
import java.net.URLEncoder
import javax.net.ssl.HttpsURLConnection

/** Native presentation of the existing bot API. The server remains the only financial authority. */
internal interface GanjBotStoreApi {
    suspend fun request(action: String, parameters: Map<String, String> = emptyMap()): JSONObject
    suspend fun uploadReceipt(context: Context, uri: Uri, order: String, crypto: Boolean = false, txLink: String = ""): JSONObject
}

internal class GanjBotStoreException(message: String) : Exception(message)

internal class GanjBotNativeStoreApi(private val authorization: () -> String?) : GanjBotStoreApi {
    private val sessionLock = Mutex()
    private var authenticated = false
    private var csrf: String? = null
    private val base = "https://bot.pedramhs.ir/site/api.php"
    private val reads = setOf("products", "product", "transactions", "wallet_methods", "payment_status", "wallet_card_session", "wallet_crypto_session")
    private val writes = setOf("purchase_quote", "purchase", "wallet_card", "wallet_crypto", "wallet_gateway")

    private suspend fun session(required: Boolean) = sessionLock.withLock {
        if (!authenticated) {
            val bearer = withContext(Dispatchers.IO) { authorization() }
            if (!bearer.isNullOrBlank()) authenticated = botStoreAndroidSso(bearer)
        }
        if (required && !authenticated) throw GanjBotStoreException("ابتدا حساب تلگرام خود را در بخش پروفایل به گنج متصل کنید و دوباره تلاش کنید.")
    }

    private suspend fun cookies(): String = if (!authenticated) "" else withContext(Dispatchers.Main) {
        CookieManager.getInstance().getCookie(base).orEmpty()
    }
    private suspend fun saveCookies(headers: Map<String?, List<String>>) {
        val values = headers.filterKeys { it?.equals("set-cookie", true) == true }.values.flatten()
        withContext(Dispatchers.Main) {
            val jar = CookieManager.getInstance()
            values.forEach { jar.setCookie(base, it) }
            if (values.isNotEmpty()) jar.flush()
        }
    }
    private fun form(parameters: Map<String, String>): String = parameters.entries.joinToString("&") {
        URLEncoder.encode(it.key, "UTF-8") + "=" + URLEncoder.encode(it.value, "UTF-8")
    }
    private suspend fun exchange(action: String, parameters: Map<String, String>, post: Boolean, token: String? = null): JSONObject {
        val cookie = cookies()
        return withContext(Dispatchers.IO) {
            val suffix = if (post || parameters.isEmpty()) "" else "&" + form(parameters)
            val conn = (URL("$base?action=$action$suffix").openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 8000
                readTimeout = if (action == "purchase") 45000 else 12000
                requestMethod = if (post) "POST" else "GET"
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Cache-Control", "no-store")
                setRequestProperty("User-Agent", "GANJ-Android-Native-Store/1")
                if (cookie.isNotBlank()) setRequestProperty("Cookie", cookie)
                if (post) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/x-www-form-urlencoded; charset=UTF-8")
                    setRequestProperty("X-CSRF-Token", token ?: error("Missing CSRF"))
                }
            }
            try {
                if (post) conn.outputStream.use { it.write(form(parameters).toByteArray(Charsets.UTF_8)) }
                response(conn)
            } finally { conn.disconnect() }
        }
    }
    private suspend fun response(conn: HttpsURLConnection): JSONObject {
        val status = conn.responseCode
        saveCookies(conn.headerFields)
        if (status == 401) { authenticated = false; csrf = null }
        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
            ?: throw GanjBotStoreException("ارتباط با فروشگاه برقرار نشد. دوباره تلاش کنید.")
        val text = stream.bufferedReader(Charsets.UTF_8).use { reader ->
            val raw = StringBuilder(); val buffer = CharArray(4096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                check(raw.length + count <= 512_000) { "Oversized bot response" }
                raw.append(buffer, 0, count)
            }
            raw.toString()
        }
        val envelope = JSONObject(text)
        if (status !in 200..299 || !envelope.optBoolean("status")) {
            throw GanjBotStoreException(envelope.optString("message").take(500)
                .ifBlank { "درخواست انجام نشد. دوباره تلاش کنید." })
        }
        return envelope
    }
    private suspend fun csrfToken(): String {
        csrf?.let { return it }
        val value = exchange("csrf", emptyMap(), false).getJSONObject("data").getString("csrf")
        check(value.matches(Regex("[0-9a-f]{64}")))
        csrf = value
        return value
    }
    override suspend fun request(action: String, parameters: Map<String, String>): JSONObject {
        require(action in reads || action in writes)
        session(required = action !in setOf("products", "product"))
        return exchange(action, parameters, action in writes, if (action in writes) csrfToken() else null)
    }
    override suspend fun uploadReceipt(context: Context, uri: Uri, order: String, crypto: Boolean, txLink: String): JSONObject {
        require(order.matches(Regex("[A-Za-z0-9_-]{6,64}")))
        session(required = true)
        val token = csrfToken(); val cookie = cookies()
        return withContext(Dispatchers.IO) {
            val type = context.contentResolver.getType(uri).orEmpty()
            val extension = when (type) {
                "image/jpeg" -> "jpg"; "image/png" -> "png"; "image/webp" -> "webp"; "application/pdf" -> "pdf"
                else -> throw GanjBotStoreException("رسید را با فرمت JPG، PNG، WebP یا PDF انتخاب کنید.")
            }
            val bytes = context.contentResolver.openInputStream(uri)?.use { input ->
                val out = ByteArrayOutputStream(); val buffer = ByteArray(8192)
                while (true) {
                    val count = input.read(buffer); if (count < 0) break
                    if (out.size() + count > 8 * 1024 * 1024) throw GanjBotStoreException("حجم رسید باید کمتر از ۸ مگابایت باشد.")
                    out.write(buffer, 0, count)
                }
                out.toByteArray()
            } ?: throw GanjBotStoreException("فایل رسید خوانده نشد. دوباره انتخاب کنید.")
            if (bytes.isEmpty()) throw GanjBotStoreException("فایل رسید خالی است.")
            val boundary = "ganj-" + java.util.UUID.randomUUID().toString()
            val conn = (URL("$base?action=" + if (crypto) "wallet_crypto_receipt" else "wallet_receipt").openConnection() as HttpsURLConnection).apply {
                instanceFollowRedirects = false; connectTimeout = 8000; readTimeout = 45000
                requestMethod = "POST"; doOutput = true
                setRequestProperty("Cookie", cookie); setRequestProperty("X-CSRF-Token", token)
                setRequestProperty("Accept", "application/json")
                setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
            }
            try {
                conn.outputStream.use { output ->
                    fun write(value: String) { output.write(value.toByteArray(Charsets.UTF_8)) }
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"order_id\"\r\n\r\n$order\r\n")
                    if (crypto) write("--$boundary\r\nContent-Disposition: form-data; name=\"tx_link\"\r\n\r\n${txLink.take(500)}\r\n")
                    write("--$boundary\r\nContent-Disposition: form-data; name=\"receipt\"; filename=\"receipt.$extension\"\r\nContent-Type: $type\r\n\r\n")
                    output.write(bytes); write("\r\n--$boundary--\r\n")
                }
                response(conn)
            } finally { conn.disconnect() }
        }
    }
}

internal data class GanjBotQuote(val price: Long, val balance: Long, val enough: Boolean, val saved: Long, val coupon: String) {
    companion object {
        fun parse(data: JSONObject): GanjBotQuote {
            fun money(key: String): Long {
                val n = data.getDouble(key); require(n.isFinite() && (n >= 0 || key == "balance") && n >= -1_000_000_000_000 && n <= 1_000_000_000_000)
                return n.toLong()
            }
            return GanjBotQuote(money("price"), money("balance"), data.getBoolean("enough"), money("saved"),
                data.optString("coupon_code").takeUnless { it == "null" }.orEmpty())
        }
    }
}

internal fun ganjBotStoreError(error: Exception): String = if (error is GanjBotStoreException) error.message.orEmpty()
    else "ارتباط با فروشگاه کامل نشد. اینترنت را بررسی و دوباره تلاش کنید. در صورت ارسال خرید، تلاش دوباره همان درخواست را پیگیری می‌کند."
