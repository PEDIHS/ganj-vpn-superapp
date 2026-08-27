package com.ganj.vpn.core.xray

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.core.vpn.VpnEngine
import java.io.Closeable
import java.util.concurrent.atomic.AtomicReference

interface TunnelDevice : Closeable {
    val fileDescriptor: Int
}

interface TunnelPlatform {
    fun establish(mtu: Int): Result<TunnelDevice>
    fun protect(fileDescriptor: Int): Boolean
}

class AndroidXrayEngine(
    private val platform: TunnelPlatform,
    private val native: XrayNativeBridge,
    private val clock: () -> Long = System::currentTimeMillis,
    private val compiler: XrayConfigCompiler = XrayConfigCompiler(clock),
    private val mtu: Int = 1500,
) : VpnEngine, Closeable {
    private val state = AtomicReference(ConnectionState())
    private var tunnel: TunnelDevice? = null

    override fun currentState(): ConnectionState = state.get()

    @Synchronized
    override fun connect(request: ConnectionRequest): Result<Unit> {
        val profile = request.profile
        if (state.get().phase != ConnectionPhase.DISCONNECTED && state.get().phase != ConnectionPhase.ERROR) {
            profile.close()
            return Result.failure(VpnRuntimeException("vpn.connection_already_active"))
        }
        if (profile.isExpired(clock())) {
            profile.close()
            state.set(ConnectionState(ConnectionPhase.ERROR, profile.serverId, errorCode = "vpn.profile_expired"))
            return Result.failure(VpnRuntimeException("vpn.profile_expired"))
        }

        state.set(ConnectionState(ConnectionPhase.PREPARING, profile.serverId))
        val established = platform.establish(mtu).getOrElse {
            profile.close()
            state.set(ConnectionState(ConnectionPhase.ERROR, profile.serverId, errorCode = "vpn.tun_establish_failed"))
            return Result.failure(VpnRuntimeException("vpn.tun_establish_failed"))
        }
        tunnel = established
        state.set(ConnectionState(ConnectionPhase.CONNECTING, profile.serverId))

        val protector = native.installSocketProtector(SocketProtector(platform::protect))
        if (!protector.success) return failAndDestroy(profile, protector.errorCode ?: "vpn.socket_protection_failed")

        val config = runCatching { compiler.compile(profile, established.fileDescriptor, mtu) }.getOrElse {
            return failAndDestroy(profile, "vpn.profile_compile_failed")
        }
        val started = try {
            native.start(config)
        } finally {
            config.close()
            profile.close()
        }
        if (!started.success) return fail(started.errorCode ?: "vpn.core_start_failed")

        state.set(
            ConnectionState(
                phase = ConnectionPhase.CONNECTED,
                serverId = profile.serverId,
                connectedAtEpochMillis = clock(),
            ),
        )
        return Result.success(Unit)
    }

    @Synchronized
    override fun disconnect(): Result<Unit> {
        if (state.get().phase == ConnectionPhase.DISCONNECTED) return Result.success(Unit)
        state.set(state.get().copy(phase = ConnectionPhase.DISCONNECTING))
        val stopped = native.stop()
        closeTunnel()
        return if (stopped.success) {
            state.set(ConnectionState())
            Result.success(Unit)
        } else {
            state.set(ConnectionState(ConnectionPhase.ERROR, errorCode = stopped.errorCode ?: "vpn.core_stop_failed"))
            Result.failure(VpnRuntimeException(stopped.errorCode ?: "vpn.core_stop_failed"))
        }
    }

    override fun close() {
        disconnect()
    }

    private fun failAndDestroy(profile: com.ganj.vpn.core.vpn.ProvisionedProfile, code: String): Result<Unit> {
        profile.close()
        return fail(code)
    }

    private fun fail(code: String): Result<Unit> {
        runCatching { native.stop() }
        closeTunnel()
        state.set(ConnectionState(ConnectionPhase.ERROR, state.get().serverId, errorCode = code))
        return Result.failure(VpnRuntimeException(code))
    }

    private fun closeTunnel() {
        runCatching { tunnel?.close() }
        tunnel = null
    }
}

class VpnRuntimeException(val code: String) : IllegalStateException(code)
