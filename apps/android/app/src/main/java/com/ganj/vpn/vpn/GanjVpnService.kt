package com.ganj.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import com.ganj.vpn.MainActivity
import com.ganj.vpn.presentation.ActiveTunnelProbe
import com.ganj.vpn.presentation.LatencyProbeResult
import com.ganj.vpn.presentation.latencyMillis
import com.ganj.vpn.presentation.ConnectionFailures
import com.ganj.vpn.core.xray.NativeProbeResult
import com.ganj.vpn.core.xray.ProxyProbeFallback
import com.ganj.vpn.core.xray.NativeFailureClassifier
import com.ganj.vpn.R
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.core.vpn.ProvisionedProfile
import com.ganj.vpn.core.xray.XrayConfigCompiler
import com.ganj.vpn.core.xray.SocketProtector
import com.ganj.vpn.core.vpn.SecureProfileRecoveryStore
import com.ganj.vpn.core.vpn.VpnReconnectCoordinator
import com.ganj.vpn.core.xray.AndroidXrayEngine
import com.ganj.vpn.core.xray.ReflectiveLibXrayBridge
import com.ganj.vpn.core.xray.TunnelDevice
import com.ganj.vpn.core.xray.TunnelPlatform
import com.ganj.vpn.core.xray.VpnRuntimeException
import java.net.HttpURLConnection
import java.net.InetSocketAddress
import java.net.Socket
import java.net.URL
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Android's privileged tunnel boundary.
 *
 * Requests stay in-process and contain only short-lived profiles issued for the signed-in user's
 * verified entitlement. No Intent extra, file, clipboard value, QR code, or share-link can start a
 * tunnel. Process recovery can only restore a Keystore-sealed profile previously accepted here.
 */
class GanjVpnService : VpnService(), TunnelPlatform {
    private val localBinder = LocalBinder()
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val recoveryInFlight = AtomicBoolean(false)
    private val desiredConnection = AtomicBoolean(false)
    private val networkLossObserved = AtomicBoolean(false)
    private val reconnectCoordinator = VpnReconnectCoordinator()
    private val nativeLifecycleLock = Any()
    private val recoveryStore by lazy { SecureProfileRecoveryStore(applicationContext) }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }

    @Volatile
    private var reconnectJob: Job? = null

    @Volatile
    private var networkCallbackRegistered = false

    private val underlyingNetworks = java.util.concurrent.ConcurrentHashMap.newKeySet<Network>()

    private val engine by lazy {
        AndroidXrayEngine(
            platform = this,
            native = ReflectiveLibXrayBridge(applicationContext.classLoader, protectedDnsServer = ::protectedDnsServer) { response ->
                if (com.ganj.vpn.BuildConfig.NATIVE_FIXTURE_DIAGNOSTICS) {
                    android.util.Log.e("GanjNativeFixture", response)
                }
            },
        )
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (!underlyingNetworks.remove(network)) return
            if (!desiredConnection.get()) return
            networkLossObserved.set(true)
            // Android may deliver new-network onAvailable before old-network onLost. If a new
            // default already exists, reconnect now; otherwise wait for its onAvailable callback.
            if (
                underlyingNetworks.isNotEmpty() &&
                networkLossObserved.compareAndSet(true, false)
            ) {
                scheduleNetworkReconnect()
            }
        }

        override fun onAvailable(network: Network) {
            underlyingNetworks.add(network)
            if (!desiredConnection.get()) return
            if (networkLossObserved.compareAndSet(true, false)) {
                scheduleNetworkReconnect()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        serviceScope.launch {
            while (isActive) {
                VpnRuntimeState.publish(engine.currentState(), engine.hasTunnel())
                delay(250)
            }
        }
        networkCallbackRegistered = runCatching {
            connectivityManager.registerNetworkCallback(
                NetworkRequest.Builder()
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                    .addCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    .build(),
                networkCallback,
            )
            true
        }.getOrDefault(false)
    }

    override fun onBind(intent: Intent): IBinder? =
        if (intent.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> startForeground(NOTIFICATION_ID, connectionNotification())
            ACTION_DISCONNECT -> {
                // Notification/service commands are delivered on the main thread. Native Xray
                // shutdown and recovery-store cleanup may block, so keep teardown on the service
                // IO scope just like binder-driven disconnects.
                serviceScope.launch {
                    terminateDesiredConnection(clearRecovery = true, startId = startId)
                }
                return START_NOT_STICKY
            }
            null -> {
                // START_STICKY process/service recreation. Foreground first, then validate and
                // decrypt the last server-provisioned profile off the main thread.
                startForeground(NOTIFICATION_ID, connectionNotification())
                scheduleRecovery(startId)
            }
        }
        return START_STICKY
    }

    private fun scheduleRecovery(startId: Int) {
        if (!recoveryInFlight.compareAndSet(false, true)) return
        serviceScope.launch {
            try {
                val restored = recoveryStore.restore().getOrElse {
                    terminateDesiredConnection(clearRecovery = true, startId = startId)
                    return@launch
                }
                if (restored == null) {
                    terminateDesiredConnection(clearRecovery = false, startId = startId)
                    return@launch
                }

                desiredConnection.set(true)
                val result = synchronized(nativeLifecycleLock) { engine.connect(ConnectionRequest(restored)) }
                if (result.isFailure) {
                    terminateDesiredConnection(clearRecovery = true, startId = startId)
                }
            } finally {
                recoveryInFlight.set(false)
            }
        }
    }

    private fun scheduleNetworkReconnect() {
        if (!desiredConnection.get() || recoveryInFlight.get()) return
        reconnectJob?.cancel()
        val epoch = reconnectCoordinator.beginEpoch()
        reconnectJob = serviceScope.launch { reconnectLoop(epoch) }
    }

    private suspend fun reconnectLoop(epoch: Long) {
        while (
            currentCoroutineContext().isActive &&
            desiredConnection.get() &&
            reconnectCoordinator.isCurrent(epoch)
        ) {
            val plan = reconnectCoordinator.nextPlan(epoch) ?: return
            delay(plan.delayMillis)
            if (
                !currentCoroutineContext().isActive ||
                !desiredConnection.get() ||
                !reconnectCoordinator.isCurrent(epoch)
            ) return

            val restored = recoveryStore.restore().getOrElse {
                terminateDesiredConnection(clearRecovery = true)
                return
            } ?: run {
                terminateDesiredConnection(clearRecovery = false)
                return
            }

            val result = synchronized(nativeLifecycleLock) { engine.reconnect(ConnectionRequest(restored)) }
            if (result.isSuccess) {
                reconnectCoordinator.markConnected(epoch)
                return
            }

            when ((result.exceptionOrNull() as? VpnRuntimeException)?.code) {
                "vpn.profile_expired",
                "vpn.reconnect_without_tunnel",
                -> {
                    terminateDesiredConnection(clearRecovery = true)
                    return
                }
            }
            // Other transient/native failures retain the existing TUN. Traffic therefore remains
            // routed into the VPN interface while bounded retries continue instead of bypassing it.
        }
    }

    private fun terminateDesiredConnection(
        clearRecovery: Boolean,
        startId: Int? = null,
    ) {
        desiredConnection.set(false)
        networkLossObserved.set(false)
        reconnectCoordinator.invalidate()
        reconnectJob?.cancel()
        reconnectJob = null
        if (clearRecovery) recoveryStore.clear()
        synchronized(nativeLifecycleLock) { engine.disconnect() }
        VpnRuntimeState.publish(engine.currentState())
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (startId == null) stopSelf() else stopSelf(startId)
    }

    override fun establish(mtu: Int): Result<TunnelDevice> = runCatching {
        val builder = Builder()
            .setSession(getString(R.string.vpn_session_name))
            .setMtu(mtu)
            .addAddress(IPV4_CLIENT_ADDRESS, IPV4_PREFIX_LENGTH)
            .addRoute(IPV4_DEFAULT_ROUTE, 0)
            .addDnsServer(IPV4_DNS)
            .addAddress(IPV6_CLIENT_ADDRESS, IPV6_PREFIX_LENGTH)
            .addRoute(IPV6_DEFAULT_ROUTE, 0)
            .addDnsServer(IPV6_DNS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) builder.setBlocking(true)
        val descriptor = requireNotNull(builder.establish()) { "vpn.tun_permission_missing" }
        VpnRuntimeState.publish(engine.currentState(), hasTunnel = true)
        ParcelTunnelDevice(descriptor)
    }

    override fun protect(fileDescriptor: Int): Boolean = super.protect(fileDescriptor)

    override fun onRevoke() {
        terminateDesiredConnection(clearRecovery = true)
        super.onRevoke()
    }

    override fun onDestroy() {
        reconnectCoordinator.invalidate()
        reconnectJob?.cancel()
        if (networkCallbackRegistered) {
            runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
            networkCallbackRegistered = false
        }
        serviceScope.cancel()
        synchronized(nativeLifecycleLock) { engine.close() }
        VpnRuntimeState.publish(engine.currentState())
        super.onDestroy()
    }

    private fun protectedDnsServer(): String {
        // Resolve the proxy endpoint using DNS reachable on the physical network.
        // A hard-coded public UDP resolver can be blocked even when the VPN server is reachable.
        val active = connectivityManager.activeNetwork?.takeIf {
            connectivityManager.getNetworkCapabilities(it)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN) == true
        }
        val networks = listOfNotNull(active) + underlyingNetworks.toList()
        val servers = networks.flatMap { connectivityManager.getLinkProperties(it)?.dnsServers.orEmpty() }
        val server = servers.firstOrNull { it is java.net.Inet4Address } ?: servers.firstOrNull()
            ?: return "1.1.1.1:53"
        val host = server.hostAddress ?: return "1.1.1.1:53"
        return if (server is java.net.Inet6Address) "[$host]:53" else "$host:53"
    }

    private fun probeProfile(profile: ProvisionedProfile): NativeProbeResult {
        return try {
            if (engine.hasTunnel() && engine.currentState().phase != ConnectionPhase.CONNECTED) {
                return NativeProbeResult.Failed("probe.active_tunnel_busy")
            }
            if (profile.isExpired(System.currentTimeMillis())) return NativeProbeResult.Failed("vpn.profile_expired")
            val native = ReflectiveLibXrayBridge(applicationContext.classLoader, protectedDnsServer = ::protectedDnsServer)
            // Keep the active core's global DNS/protector intact. pingBatch uses a local
            // core instance and inherits the already installed, protected socket dialer.
            val installed = if (engine.hasTunnel()) com.ganj.vpn.core.xray.NativeCallResult(true)
            else native.installSocketProtector(SocketProtector { fd -> fd >= 0 && (!engine.hasTunnel() || protect(fd)) })
            if (!installed.success) NativeProbeResult.Failed(installed.errorCode ?: "xray.socket_protection_unavailable")
            else XrayConfigCompiler().compileProbe(profile).use { config ->
                if (com.ganj.vpn.BuildConfig.NATIVE_FIXTURE_DIAGNOSTICS) {
                    native.probeDetailed(config, noBackupFilesDir, listOf(NATIVE_FIXTURE_PROBE_URL))
                } else native.probeDetailed(config, noBackupFilesDir)
            }
        } finally { profile.close() }
    }

    private fun NativeProbeResult.asLatencyResult(): LatencyProbeResult = when (this) {
        is NativeProbeResult.Measured -> LatencyProbeResult.Measured(millis)
        is NativeProbeResult.Failed -> LatencyProbeResult.Failed(
            if (code.startsWith("probe.")) ConnectionFailures.probe(code) else ConnectionFailures.runtime(code),
        )
    }

    private fun probeEstablishedTunnel(): NativeProbeResult {
        if (com.ganj.vpn.BuildConfig.NATIVE_FIXTURE_DIAGNOSTICS) {
            // The isolated benchmark endpoint uses plaintext only inside the test VLESS fixture.
            // Keep Android's production cleartext policy intact and verify its exact marker.
            return try {
                val started = SystemClock.elapsedRealtime()
                Socket().use { socket ->
                    socket.soTimeout = ACTIVE_TUNNEL_PROBE_TIMEOUT_MS
                    socket.connect(InetSocketAddress("198.18.0.1", 18080), ACTIVE_TUNNEL_PROBE_TIMEOUT_MS)
                    socket.getOutputStream().write(
                        "GET /ganj-tun-check HTTP/1.1\r\nHost: test.invalid\r\nConnection: close\r\n\r\n".toByteArray(),
                    )
                    val response = socket.getInputStream().bufferedReader().readText()
                    if (response.contains("ganj-tun-vless-roundtrip-ok")) {
                        NativeProbeResult.Measured((SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
                    } else NativeProbeResult.Failed("probe.http_failed")
                }
            } catch (error: Exception) {
                NativeProbeResult.Failed(NativeFailureClassifier.probe(error))
            }
        }
        // Bind every attempt to this VPN network. A disconnect between attempts must
        // fail instead of quietly timing a direct connection on the physical network.
        val vpnNetwork = connectivityManager.allNetworks.firstOrNull {
            connectivityManager.getNetworkCapabilities(it)?.hasTransport(NetworkCapabilities.TRANSPORT_VPN) == true
        } ?: return NativeProbeResult.Failed("probe.network_unreachable")
        return ProxyProbeFallback.measure { target ->
            var connection: HttpURLConnection? = null
            try {
                val started = SystemClock.elapsedRealtime()
                connection = vpnNetwork.openConnection(URL(target)) as HttpURLConnection
                connection.connectTimeout = ACTIVE_TUNNEL_PROBE_TIMEOUT_MS
                connection.readTimeout = ACTIVE_TUNNEL_PROBE_TIMEOUT_MS
                connection.instanceFollowRedirects = false
                connection.requestMethod = "HEAD"
                connection.useCaches = false
                connection.setRequestProperty("Cache-Control", "no-store")
                connection.setRequestProperty("Connection", "close")
                if (connection.responseCode in 200..399) {
                    NativeProbeResult.Measured((SystemClock.elapsedRealtime() - started).coerceAtLeast(0L))
                } else NativeProbeResult.Failed("probe.http_failed")
            } finally { connection?.disconnect() }
        }
    }


    inner class LocalBinder internal constructor() : Binder() {
        suspend fun probe(profile: ProvisionedProfile): Long? = probeDetailed(profile).latencyMillis

        suspend fun probeDetailed(profile: ProvisionedProfile): LatencyProbeResult = withContext(Dispatchers.IO) {
            synchronized(nativeLifecycleLock) {
                if (engine.hasTunnel() && engine.currentState().phase != ConnectionPhase.CONNECTED) {
                    profile.close()
                    LatencyProbeResult.Failed(ConnectionFailures.probe("probe.active_tunnel_busy"))
                } else probeProfile(profile).asLatencyResult()
            }
        }

        suspend fun probeActive(serviceId: String, serverId: String): ActiveTunnelProbe =
            withContext(Dispatchers.IO) {
                val state = engine.currentState()
                if (!engine.hasTunnel()) return@withContext ActiveTunnelProbe.NotActive
                if (state.phase != ConnectionPhase.CONNECTED) return@withContext ActiveTunnelProbe.OtherTunnelActive
                if (state.serviceId != serviceId || state.serverId != serverId) {
                    return@withContext ActiveTunnelProbe.OtherTunnelActive
                }
                when (val measured = probeEstablishedTunnel()) {
                    is NativeProbeResult.Measured -> ActiveTunnelProbe.Measured(measured.millis)
                    is NativeProbeResult.Failed -> ActiveTunnelProbe.Measured(null, measured.code)
                }
            }

        suspend fun connect(request: ConnectionRequest): Result<Unit> = withContext(Dispatchers.IO) {
            reconnectCoordinator.invalidate()
            reconnectJob?.cancel()
            reconnectJob = null
            networkLossObserved.set(false)
            startForeground(NOTIFICATION_ID, connectionNotification())

            val persisted = recoveryStore.save(request.profile)
            if (persisted.isFailure) {
                request.profile.close()
                if (!engine.hasTunnel()) {
                    desiredConnection.set(false)
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
                return@withContext Result.failure(
                    VpnRuntimeException("vpn.profile_recovery_write_failed"),
                )
            }

            desiredConnection.set(true)
            val result = synchronized(nativeLifecycleLock) {
                if (engine.hasTunnel()) engine.reconnect(request) else engine.connect(request)
            }
            VpnRuntimeState.publish(engine.currentState(), engine.hasTunnel())
            result.onFailure {
                if (engine.hasTunnel()) {
                    scheduleNetworkReconnect()
                } else {
                    desiredConnection.set(false)
                    recoveryStore.clear()
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
        }

        suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
            desiredConnection.set(false)
            networkLossObserved.set(false)
            reconnectCoordinator.invalidate()
            reconnectJob?.cancel()
            reconnectJob = null
            recoveryStore.clear()
            synchronized(nativeLifecycleLock) { engine.disconnect() }.also {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        fun currentState(): ConnectionState = engine.currentState()
    }

    private fun connectionNotification(): Notification {
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val disconnect = PendingIntent.getService(
            this,
            1,
            Intent(this, GanjVpnService::class.java).setAction(ACTION_DISCONNECT),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        return builder
            .setSmallIcon(R.drawable.ic_vpn_shield)
            .setContentTitle(getString(R.string.vpn_notification_title))
            .setContentText(getString(R.string.vpn_notification_message))
            .setContentIntent(openApp)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_vpn_shield,
                    getString(R.string.vpn_disconnect_action),
                    disconnect,
                ).build(),
            )
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            NOTIFICATION_CHANNEL_ID,
            getString(R.string.vpn_notification_channel),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.vpn_notification_channel_description)
            setShowBadge(false)
        }
        manager.createNotificationChannel(channel)
    }

    private class ParcelTunnelDevice(
        private val descriptor: ParcelFileDescriptor,
    ) : TunnelDevice {
        override val fileDescriptor: Int = descriptor.fd
        override fun close() = descriptor.close()
    }

    companion object {
        const val ACTION_LOCAL_BIND = "com.ganj.vpn.action.BIND_LOCAL_TUNNEL"
        const val ACTION_PREPARE = "com.ganj.vpn.action.PREPARE_TUNNEL"
        const val ACTION_DISCONNECT = "com.ganj.vpn.action.DISCONNECT"
        private const val NOTIFICATION_CHANNEL_ID = "ganj_vpn_connection"
        private const val NOTIFICATION_ID = 4201
        private const val NATIVE_FIXTURE_PROBE_URL = "http://198.18.0.1:18080/ganj-tun-check"
        private const val ACTIVE_TUNNEL_PROBE_TIMEOUT_MS = 5_000
        private const val IPV4_CLIENT_ADDRESS = "10.111.222.2"
        private const val IPV4_PREFIX_LENGTH = 32
        private const val IPV4_DEFAULT_ROUTE = "0.0.0.0"
        private const val IPV4_DNS = "1.1.1.1"
        private const val IPV6_CLIENT_ADDRESS = "fd00:111:222::2"
        private const val IPV6_PREFIX_LENGTH = 128
        private const val IPV6_DEFAULT_ROUTE = "::"
        private const val IPV6_DNS = "2606:4700:4700::1111"
    }
}
