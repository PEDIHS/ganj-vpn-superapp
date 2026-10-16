package com.ganj.vpn.ui

import android.content.Context
import com.ganj.vpn.core.controlapi.ConnectionServer
import com.ganj.vpn.core.controlapi.SubscriptionTier
import com.ganj.vpn.core.controlapi.VpnProtocol
import org.json.JSONArray
import org.json.JSONObject
import java.security.MessageDigest
import java.util.UUID

/**
 * Remembers only presentation metadata for each verified account and entitlement.
 * No VPN configuration, endpoint credentials, bearer tokens, or authorization grants
 * are written here. The profile endpoint MUST authorize and revalidate every connect.
 */
internal class GanjConnectionSelectionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("ganj_connection_selection_v1", Context.MODE_PRIVATE)

    private fun accountScope(accountId: String): String? {
        val canonical = runCatching { UUID.fromString(accountId).toString() }.getOrNull()
            ?.takeIf { it == accountId.lowercase() } ?: return null
        val digest = MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray(Charsets.UTF_8))
        return digest.take(12).joinToString("") { "%02x".format(it) }
    }

    private fun serviceKey(accountId: String) = accountScope(accountId)?.let { "account.$it.service" }
    private fun serverKey(accountId: String, serviceId: String): String? {
        if (!isUuid(serviceId)) return null
        return accountScope(accountId)?.let { "account.$it.server.$serviceId" }
    }
    private fun catalogKey(accountId: String, serviceId: String): String? {
        if (!isUuid(serviceId)) return null
        return accountScope(accountId)?.let { "account.$it.catalog.$serviceId" }
    }

    fun preferredService(accountId: String): String? =
        serviceKey(accountId)?.let { preferences.getStringSafe(it) }?.takeIf(::isUuid)

    fun preferredServer(accountId: String, serviceId: String): String? =
        serverKey(accountId, serviceId)?.let { preferences.getStringSafe(it) }?.takeIf(::isUuid)

    fun rememberService(accountId: String, serviceId: String) {
        if (isUuid(serviceId)) serviceKey(accountId)?.let { preferences.edit().putString(it, serviceId).apply() }
    }

    fun rememberServer(accountId: String, serviceId: String, serverId: String) {
        if (isUuid(serverId)) serverKey(accountId, serviceId)?.let {
            preferences.edit().putString(it, serverId).apply()
        }
    }

    fun rememberCatalog(accountId: String, serviceId: String, servers: List<ConnectionServer>) {
        val key = catalogKey(accountId, serviceId) ?: return
        // Empty is an authoritative response: do not resurrect deleted server entries.
        if (servers.size > 200) return
        val array = JSONArray()
        servers.forEach { server ->
            if (!isUuid(server.id)) return@forEach
            array.put(JSONObject().apply {
                put("id", server.id)
                put("code", server.code)
                put("name", server.name)
                put("country", server.countryCode)
                put("city", server.city ?: JSONObject.NULL)
                put("tier", server.tier.name)
                put("protocols", JSONArray(server.protocols.map { it.name }))
            })
        }
        // Private app storage; never cache leases, decrypted profiles or proxy endpoints.
        preferences.edit().putString(key, array.toString()).apply()
    }

    fun cachedCatalog(accountId: String, serviceId: String): List<ConnectionServer> {
        val raw = catalogKey(accountId, serviceId)?.let { preferences.getStringSafe(it) } ?: return emptyList()
        if (raw.length > 80_000) return emptyList()
        return runCatching {
            val list = JSONArray(raw)
            require(list.length() <= 200)
            List(list.length()) { index ->
                val obj = list.getJSONObject(index)
                val id = obj.getString("id")
                require(isUuid(id))
                val protocols = obj.getJSONArray("protocols")
                require(protocols.length() in 1..4)
                val parsed = (0 until protocols.length()).map {
                    VpnProtocol.valueOf(protocols.getString(it))
                }.toSet()
                val country = obj.getString("country")
                require(country.matches(Regex("[A-Z]{2}")))
                ConnectionServer(
                    id = id,
                    code = obj.getString("code").also { require(it.length in 1..128) },
                    name = obj.getString("name").also { require(it.length in 1..160) },
                    countryCode = country,
                    city = obj.optString("city").takeIf { it.isNotBlank() && it != "null" },
                    tier = SubscriptionTier.valueOf(obj.getString("tier")),
                    protocols = parsed,
                )
            }.distinctBy { it.id }
        }.getOrDefault(emptyList())
    }

    fun forgetAccount(accountId: String) {
        val prefix = accountScope(accountId)?.let { "account.$it." } ?: return
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith(prefix) }.forEach(editor::remove)
        editor.apply()
    }

    private fun isUuid(value: String) =
        value.length == 36 && runCatching { UUID.fromString(value).toString() == value.lowercase() }.getOrDefault(false)

    private fun android.content.SharedPreferences.getStringSafe(key: String) =
        runCatching { getString(key, null) }.getOrNull()
}
