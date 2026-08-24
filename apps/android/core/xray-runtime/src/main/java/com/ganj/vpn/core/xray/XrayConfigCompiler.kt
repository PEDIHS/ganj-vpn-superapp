package com.ganj.vpn.core.xray

import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.vpn.ProvisionedSecurity
import com.ganj.vpn.core.vpn.ProvisionedTransport
import com.ganj.vpn.core.vpn.VpnProtocol

/** Compiles only trusted typed profiles; there is intentionally no raw JSON/share-link entrypoint. */
class XrayConfigCompiler(
    private val clock: () -> Long = System::currentTimeMillis,
) {
    fun compile(
        profile: ProvisionedProfile,
        tunFileDescriptor: Int,
        mtu: Int = 1500,
    ): SensitiveXrayConfig {
        require(tunFileDescriptor >= 0)
        require(mtu in 1280..9000)
        check(!profile.isExpired(clock())) { "Provisioned profile has expired" }

        return profile.useCredential { credential ->
            val stream = streamSettings(profile.transport, profile.security)
            val outbound = outbound(profile, credential, stream)
            SensitiveXrayConfig(
                """{"env":{"xray.tun.fd":"$tunFileDescriptor"},"log":{"loglevel":"warning"},"dns":{"servers":["1.1.1.1","8.8.8.8"]},"inbounds":[{"tag":"ganj-tun","protocol":"tun","settings":{"mtu":$mtu}}],"outbounds":[$outbound,{"tag":"direct","protocol":"freedom"},{"tag":"blocked","protocol":"blackhole"}],"routing":{"domainStrategy":"IPIfNonMatch","rules":[]}}""",
            )
        }
    }

    private fun outbound(profile: ProvisionedProfile, credential: String, stream: String): String {
        val address = json(profile.endpoint)
        val id = json(credential)
        val settings = when (profile.protocol) {
            VpnProtocol.VLESS -> {
                val flow = profile.flow?.let { ",\"flow\":${json(it)}" }.orEmpty()
                "{\"vnext\":[{\"address\":$address,\"port\":${profile.port},\"users\":[{\"id\":$id,\"encryption\":\"none\"$flow}]}]}"
            }
            VpnProtocol.VMESS ->
                "{\"vnext\":[{\"address\":$address,\"port\":${profile.port},\"users\":[{\"id\":$id,\"security\":\"auto\"}]}]}"
            VpnProtocol.TROJAN ->
                "{\"servers\":[{\"address\":$address,\"port\":${profile.port},\"password\":$id}]}"
            VpnProtocol.SHADOWSOCKS ->
                "{\"servers\":[{\"address\":$address,\"port\":${profile.port},\"password\":$id,\"method\":${json(requireNotNull(profile.shadowsocksMethod))}}]}"
        }
        return "{\"tag\":\"proxy\",\"protocol\":${json(profile.protocol.name.lowercase())},\"settings\":$settings,\"streamSettings\":$stream}"
    }

    private fun streamSettings(transport: ProvisionedTransport, security: ProvisionedSecurity): String {
        val network = when (transport) {
            ProvisionedTransport.Tcp -> "tcp"
            is ProvisionedTransport.WebSocket -> "ws"
            is ProvisionedTransport.Grpc -> "grpc"
        }
        val transportSettings = when (transport) {
            ProvisionedTransport.Tcp -> ""
            is ProvisionedTransport.WebSocket -> {
                val host = transport.host?.let { ",\"headers\":{\"Host\":${json(it)}}" }.orEmpty()
                ",\"wsSettings\":{\"path\":${json(transport.path)}$host}"
            }
            is ProvisionedTransport.Grpc ->
                ",\"grpcSettings\":{\"serviceName\":${json(transport.serviceName)},\"multiMode\":false}"
        }
        val securitySettings = when (security) {
            ProvisionedSecurity.None -> ",\"security\":\"none\""
            is ProvisionedSecurity.Tls ->
                ",\"security\":\"tls\",\"tlsSettings\":{\"serverName\":${json(security.serverName)},\"allowInsecure\":${security.allowInsecure},\"fingerprint\":${json(security.fingerprint)}}"
            is ProvisionedSecurity.Reality ->
                ",\"security\":\"reality\",\"realitySettings\":{\"serverName\":${json(security.serverName)},\"publicKey\":${json(security.publicKey)},\"shortId\":${json(security.shortId)},\"fingerprint\":${json(security.fingerprint)}}"
        }
        return "{\"network\":${json(network)}$securitySettings$transportSettings}"
    }

    private fun json(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\b' -> append("\\b")
                '\u000C' -> append("\\f")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }
}

class SensitiveXrayConfig internal constructor(private var value: String?) : AutoCloseable {
    internal fun consume(): String = value?.also { value = null } ?: error("Xray config was already consumed")

    override fun close() {
        value = null
    }

    override fun toString(): String = "SensitiveXrayConfig([REDACTED])"
}
