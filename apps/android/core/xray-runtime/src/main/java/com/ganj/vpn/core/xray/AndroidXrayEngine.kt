package com.ganj.vpn.core.xray

import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.vpn.VpnEngine
import java.io.Closeable
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
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
    private val operationGeneration = AtomicLong(0)
    private val operationLock = Any()

    @Volatile
    private var tunnel: TunnelDevice? = null

    override fun currentState(): ConnectionState = state.get()

    override fun connect(request: ConnectionRequest): Result<Unit> =
        start(request, reconnect = false)

    override fun reconnect(request: ConnectionRequest): Result<Unit> =
        start(request, reconnect = true)

    override fun disconnect(): Result<Unit> {
        operationGeneration.incrementAndGet()
        return synchronized(operationLock) {
            if (state.get().phase == ConnectionPhase.DISCONNECTED && tunnel == null) {
                return@synchronized Result.success(Unit)
            }
            state.set(state.get().copy(phase = ConnectionPhase.DISCONNECTING))
            val stopped = runCatching { native.stop() }
                .getOrElse { NativeCallResult(false, "vpn.core_stop_failed") }
            closeTunnel()
            if (stopped.success) {
                state.set(ConnectionState())
                Result.success(Unit)
            } else {
                val code = stopped.errorCode ?: "vpn.core_stop_failed"
                state.set(ConnectionState(ConnectionPhase.ERROR, errorCode = code))
                Result.failure(VpnRuntimeException(code))
            }
        }
    }

    override fun close() {
        disconnect()
    }

    private fun start(request: ConnectionRequest, reconnect: Boolean): Result<Unit> {
        val operation = operationGeneration.incrementAndGet()
        val profile = request.profile
        return try {
            synchronized(operationLock) {
                if (!isCurrent(operation)) return@synchronized cancelled()
                val previous = state.get()
                if (reconnect) {
                    if (tunnel == null || previous.phase == ConnectionPhase.DISCONNECTED) {
                        return@synchronized failure(
                            profile = profile,
                            code = "vpn.reconnect_without_tunnel",
                            closeTunnel = false,
                        )
                    }
                } else if (
                    previous.phase != ConnectionPhase.DISCONNECTED &&
                    previous.phase != ConnectionPhase.ERROR
                ) {
                    return@synchronized failure(
                        profile = profile,
                        code = "vpn.connection_already_active",
                        closeTunnel = true,
                    )
                }
                if (profile.isExpired(clock())) {
                    return@synchronized failure(
                        profile = profile,
                        code = "vpn.profile_expired",
                        closeTunnel = !reconnect,
                        stopNative = false,
                    )
                }

                state.set(
                    ConnectionState(
                        phase = if (reconnect) ConnectionPhase.RECONNECTING else ConnectionPhase.PREPARING,
                        serverId = profile.serverId,
                        connectedAtEpochMillis = previous.connectedAtEpochMillis,
                    ),
                )

                if (reconnect) {
                    val stopped = runCatching { native.stop() }
                        .getOrElse { NativeCallResult(false, "vpn.core_stop_failed") }
                    if (!stopped.success) {
                        return@synchronized failure(
                            profile = profile,
                            code = stopped.errorCode ?: "vpn.core_stop_failed",
                            closeTunnel = false,
                            stopNative = false,
                        )
                    }
                } else {
                    val established = platform.establish(mtu).getOrElse {
                        return@synchronized failure(
                            profile = profile,
                            code = "vpn.tun_establish_failed",
                            closeTunnel = true,
                            stopNative = false,
                        )
                    }
                    tunnel = established
                }

                if (!isCurrent(operation)) return@synchronized cancelled()
                state.set(state.get().copy(phase = ConnectionPhase.CONNECTING))
                val device = tunnel ?: return@synchronized failure(
                    profile = profile,
                    code = "vpn.tun_missing",
                    closeTunnel = !reconnect,
                )
                val socketProtectionFailed = AtomicBoolean(false)
                val protector = runCatching {
                    native.installSocketProtector(
                        SocketProtector { fileDescriptor ->
                            val protected = isCurrent(operation) &&
                                fileDescriptor >= 0 &&
                                platform.protect(fileDescriptor)
                            if (!protected) socketProtectionFailed.set(true)
                            protected
                        },
                    )
                }.getOrElse { NativeCallResult(false, "vpn.socket_protection_failed") }
                if (!protector.success || socketProtectionFailed.get()) {
                    return@synchronized failure(
                        profile = profile,
                        code = protector.errorCode ?: "vpn.socket_protection_failed",
                        closeTunnel = !reconnect,
                    )
                }

                val config = runCatching { compiler.compile(profile, device.fileDescriptor, mtu) }
                    .getOrElse {
                        return@synchronized failure(
                            profile = profile,
                            code = "vpn.profile_compile_failed",
                            closeTunnel = !reconnect,
                        )
                    }
                val started = try {
                    if (!isCurrent(operation)) return@synchronized cancelled()
                    runCatching { native.start(config) }
                        .getOrElse { NativeCallResult(false, "vpn.core_start_failed") }
                } finally {
                    config.close()
                }
                if (!isCurrent(operation)) {
                    runCatching { native.stop() }
                    return@synchronized cancelled()
                }
                if (!started.success) {
                    return@synchronized failure(
                        profile = profile,
                        code = started.errorCode ?: "vpn.core_start_failed",
                        closeTunnel = !reconnect,
                    )
                }

                state.set(
                    ConnectionState(
                        phase = ConnectionPhase.CONNECTED,
                        serverId = profile.serverId,
                        connectedAtEpochMillis = previous.connectedAtEpochMillis ?: clock(),
                    ),
                )
                Result.success(Unit)
            }
        } finally {
            profile.close()
        }
    }

    private fun isCurrent(operation: Long): Boolean = operationGeneration.get() == operation

    private fun cancelled(): Result<Unit> =
        Result.failure(VpnRuntimeException("vpn.operation_cancelled"))

    private fun failure(
        profile: ProvisionedProfile,
        code: String,
        closeTunnel: Boolean,
        stopNative: Boolean = true,
    ): Result<Unit> {
        if (stopNative) runCatching { native.stop() }
        if (closeTunnel) closeTunnel()
        state.set(
            ConnectionState(
                phase = ConnectionPhase.ERROR,
                serverId = profile.serverId,
                errorCode = code,
            ),
        )
        return Result.failure(VpnRuntimeException(code))
    }

    private fun closeTunnel() {
        runCatching { tunnel?.close() }
        tunnel = null
    }
}

class VpnRuntimeException(val code: String) : IllegalStateException(code)
