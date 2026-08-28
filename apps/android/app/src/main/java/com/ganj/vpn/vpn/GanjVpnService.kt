package com.ganj.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ganj.vpn.MainActivity
import com.ganj.vpn.R
import com.ganj.vpn.core.vpn.ConnectionPhase
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.core.vpn.SecureProfileRecoveryStore
import com.ganj.vpn.core.vpn.VpnReconnectBackoff
import com.ganj.vpn.core.xray.AndroidXrayEngine
import com.ganj.vpn.core.xray.ReflectiveLibXrayBridge
import com.ganj.vpn.core.xray.TunnelDevice
import com.ganj.vpn.core.xray.TunnelPlatform
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Android's privileged, subscription-only tunnel boundary.
 *
 * Requests stay in-process and contain only short-lived profiles issued for the signed-in user's
 * verified entitlement. No Intent extra, file, clipboard value, QR code, share link, or manually
 * supplied configuration can start or restore a tunnel.
 */
class GanjVpnService : VpnService(), TunnelPlatform {
    private val localBinder = LocalBinder()
    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(serviceJob + Dispatchers.IO)
    private val operationMutex = Mutex()
    private val networkEpoch = AtomicLong(0)
    private val reconnectBackoff = VpnReconnectBackoff()
    private val recoveryStore by lazy { SecureProfileRecoveryStore(applicationContext) }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }
    private val engineDelegate = lazy {
        AndroidXrayEngine(
            platform = this,
            native = ReflectiveLibXrayBridge(applicationContext.classLoader),
        )
    }
    private val engine by engineDelegate

    @Volatile
    private var activeNetwork: Network? = null

    @Volatile
    private var networkCallbackRegistered = false

    private var prepareTimeoutJob: Job? = null
    private var reconnectJob: Job? = null

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            val previous = activeNetwork
            activeNetwork = network
            runCatching { setUnderlyingNetworks(arrayOf(network)) }
            if (previous != null && previous != network) scheduleReconnect()
        }

        override fun onLost(network: Network) {
            if (activeNetwork == network) {
                activeNetwork = null
                runCatching { setUnderlyingNetworks(null) }
                startVpnForeground(NotificationState.RECONNECTING)
                scheduleReconnect()
            }
        }

        override fun onCapabilitiesChanged(network: Network, capabilities: NetworkCapabilities) {
            if (
                activeNetwork == network &&
                !capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            ) {
                scheduleReconnect()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder? =
        if (intent.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> {
                startVpnForeground(NotificationState.PREPARING)
                armPrepareTimeout(startId)
            }
            ACTION_DISCONNECT -> {
                startVpnForeground(NotificationState.DISCONNECTING)
                serviceScope.launch { disconnectAndStop(clearRecovery = true) }
            }
            else -> {
                startVpnForeground(NotificationState.RECONNECTING)
                serviceScope.launch { recoverAfterProcessRestart(startId) }
            }
        }
        return Service.START_STICKY
    }

    override fun establish(mtu: Int): Result<TunnelDevice> = runCatching {
        val builder = Builder()
            .setSession(getString(R.string.vpn_session_name))
            .setMtu(mtu)
            .setConfigureIntent(openApplicationIntent())
            .addAddress(IPV4_CLIENT_ADDRESS, IPV4_PREFIX_LENGTH)
            .addRoute(IPV4_DEFAULT_ROUTE, 0)
            .addDnsServer(IPV4_DNS)
            .addAddress(IPV6_CLIENT_ADDRESS, IPV6_PREFIX_LENGTH)
            .addRoute(IPV6_DEFAULT_ROUTE, 0)
            .addDnsServer(IPV6_DNS)

        currentUsableNetwork()?.let { builder.setUnderlyingNetworks(arrayOf(it)) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            builder.setBlocking(true)
            builder.setMetered(false)
        }
        val descriptor = requireNotNull(builder.establish()) { "vpn.tun_permission_missing" }
        ParcelTunnelDevice(descriptor)
    }

    override fun protect(fileDescriptor: Int): Boolean =
        fileDescriptor >= 0 && super.protect(fileDescriptor)

    override fun onRevoke() {
        networkEpoch.incrementAndGet()
        prepareTimeoutJob?.cancel()
        reconnectJob?.cancel()
        unregisterNetworkCallback()
        recoveryStore.clear()
        if (engineDelegate.isInitialized()) engine.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        networkEpoch.incrementAndGet()
        prepareTimeoutJob?.cancel()
        reconnectJob?.cancel()
        unregisterNetworkCallback()
        serviceJob.cancel()
        if (engineDelegate.isInitialized()) engine.close()
        super.onDestroy()
    }

    inner class LocalBinder internal constructor() : Binder() {
        suspend fun connect(request: ConnectionRequest): Result<Unit> {
            return try {
                connectProvisionedProfile(request)
            } catch (cancelled: CancellationException) {
                withContext(NonCancellable) { disconnectAndStop(clearRecovery = true) }
                throw cancelled
            }
        }

        suspend fun disconnect(): Result<Unit> =
            withContext(NonCancellable) { disconnectAndStop(clearRecovery = true) }

        fun currentState(): ConnectionState =
            if (engineDelegate.isInitialized()) engine.currentState() else ConnectionState()
    }

    private suspend fun connectProvisionedProfile(request: ConnectionRequest): Result<Unit> {
        var engineOwnsProfile = false
        return try {
            operationMutex.withLock {
                networkEpoch.incrementAndGet()
                reconnectJob?.cancel()
                prepareTimeoutJob?.cancel()
                val saved = recoveryStore.save(request.profile)
                if (saved.isFailure) {
                    recoveryStore.clear()
                    stopVpnForegroundAndSelf()
                    return@withLock Result.failure(
                        saved.exceptionOrNull()
                            ?: IllegalStateException("vpn.profile_recovery_write_failed"),
                    )
                }

                activeNetwork = currentUsableNetwork()
                activeNetwork?.let { runCatching { setUnderlyingNetworks(arrayOf(it)) } }
                engineOwnsProfile = true
                val connected = engine.connect(request)
                currentCoroutineContext().ensureActive()
                if (connected.isSuccess) {
                    registerNetworkCallback()
                    startVpnForeground(NotificationState.CONNECTED)
                } else {
                    recoveryStore.clear()
                    unregisterNetworkCallback()
                    stopVpnForegroundAndSelf()
                }
                connected
            }
        } finally {
            if (!engineOwnsProfile) request.profile.close()
        }
    }

    private suspend fun recoverAfterProcessRestart(startId: Int) {
        prepareTimeoutJob?.cancel()
        var attempt = 0
        while (currentCoroutineContext().isActive) {
            val restored = recoveryStore.restore()
            if (restored.isFailure) {
                disconnectAndStop(clearRecovery = true)
                return
            }
            val profile = restored.getOrNull()
            if (profile == null) {
                stopVpnForegroundAndSelf(startId)
                return
            }
            val result = operationMutex.withLock {
                activeNetwork = currentUsableNetwork()
                activeNetwork?.let { runCatching { setUnderlyingNetworks(arrayOf(it)) } }
                engine.connect(ConnectionRequest(profile))
            }
            if (result.isSuccess) {
                registerNetworkCallback()
                startVpnForeground(NotificationState.CONNECTED)
                return
            }
            startVpnForeground(NotificationState.RECONNECTING)
            delay(reconnectBackoff.delayMillis(attempt++))
        }
    }

    private fun scheduleReconnect() {
        if (!networkCallbackRegistered) return
        val epoch = networkEpoch.incrementAndGet()
        reconnectJob?.cancel()
        reconnectJob = serviceScope.launch {
            var attempt = 0
            while (isActive && networkEpoch.get() == epoch) {
                val network = currentUsableNetwork()
                if (network == null) {
                    delay(reconnectBackoff.delayMillis(attempt++))
                    continue
                }
                activeNetwork = network
                runCatching { setUnderlyingNetworks(arrayOf(network)) }
                startVpnForeground(NotificationState.RECONNECTING)
                delay(reconnectBackoff.delayMillis(attempt++))
                if (networkEpoch.get() != epoch) return@launch

                val restored = recoveryStore.restore()
                if (restored.isFailure) {
                    disconnectAndStop(clearRecovery = true)
                    return@launch
                }
                val profile = restored.getOrNull()
                if (profile == null) {
                    disconnectAndStop(clearRecovery = true)
                    return@launch
                }
                val result = operationMutex.withLock {
                    if (networkEpoch.get() != epoch) {
                        profile.close()
                        return@withLock Result.failure<Unit>(
                            IllegalStateException("vpn.network_changed"),
                        )
                    }
                    engine.reconnect(ConnectionRequest(profile))
                }
                if (result.isSuccess) {
                    startVpnForeground(NotificationState.CONNECTED)
                    return@launch
                }
            }
        }
    }

    private suspend fun disconnectAndStop(clearRecovery: Boolean): Result<Unit> =
        operationMutex.withLock {
            networkEpoch.incrementAndGet()
            prepareTimeoutJob?.cancel()
            reconnectJob?.cancel()
            unregisterNetworkCallback()
            activeNetwork = null
            runCatching { setUnderlyingNetworks(null) }
            if (clearRecovery) recoveryStore.clear()
            val result = if (engineDelegate.isInitialized()) {
                engine.disconnect()
            } else {
                Result.success(Unit)
            }
            stopVpnForegroundAndSelf()
            result
        }

    private fun registerNetworkCallback() {
        if (networkCallbackRegistered) return
        activeNetwork = currentUsableNetwork()
        connectivityManager.registerDefaultNetworkCallback(networkCallback)
        networkCallbackRegistered = true
    }

    private fun unregisterNetworkCallback() {
        if (!networkCallbackRegistered) return
        runCatching { connectivityManager.unregisterNetworkCallback(networkCallback) }
        networkCallbackRegistered = false
    }

    private fun currentUsableNetwork(): Network? {
        val network = connectivityManager.activeNetwork ?: return null
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return null
        return network.takeIf {
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }
    }

    private fun armPrepareTimeout(startId: Int) {
        prepareTimeoutJob?.cancel()
        prepareTimeoutJob = serviceScope.launch {
            delay(PREPARE_TIMEOUT_MILLIS)
            val disconnected = !engineDelegate.isInitialized() ||
                engine.currentState().phase == ConnectionPhase.DISCONNECTED
            if (disconnected) stopVpnForegroundAndSelf(startId)
        }
    }

    private fun startVpnForeground(state: NotificationState) {
        val notification = connectionNotification(state)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun stopVpnForegroundAndSelf(startId: Int? = null) {
        stopForeground(STOP_FOREGROUND_REMOVE)
        if (startId == null) stopSelf() else stopSelfResult(startId)
    }

    private fun connectionNotification(state: NotificationState): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, NOTIFICATION_CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }
        builder
            .setSmallIcon(R.drawable.ic_vpn_shield)
            .setContentTitle(getString(state.titleResource))
            .setContentText(getString(state.messageResource))
            .setContentIntent(openApplicationIntent())
            .setCategory(Notification.CATEGORY_SERVICE)
            .setVisibility(Notification.VISIBILITY_SECRET)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .addAction(
                Notification.Action.Builder(
                    R.drawable.ic_vpn_shield,
                    getString(R.string.vpn_disconnect_action),
                    disconnectIntent(),
                ).build(),
            )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setForegroundServiceBehavior(Notification.FOREGROUND_SERVICE_IMMEDIATE)
        }
        return builder.build()
    }

    private fun openApplicationIntent(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun disconnectIntent(): PendingIntent = PendingIntent.getService(
        this,
        1,
        Intent(this, GanjVpnService::class.java).setAction(ACTION_DISCONNECT),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

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
            lockscreenVisibility = Notification.VISIBILITY_SECRET
        }
        manager.createNotificationChannel(channel)
    }

    private class ParcelTunnelDevice(
        private val descriptor: ParcelFileDescriptor,
    ) : TunnelDevice {
        override val fileDescriptor: Int = descriptor.fd
        override fun close() = descriptor.close()
    }

    private enum class NotificationState(
        val titleResource: Int,
        val messageResource: Int,
    ) {
        PREPARING(R.string.vpn_notification_preparing_title, R.string.vpn_notification_preparing_message),
        CONNECTED(R.string.vpn_notification_title, R.string.vpn_notification_message),
        RECONNECTING(R.string.vpn_notification_reconnecting_title, R.string.vpn_notification_reconnecting_message),
        DISCONNECTING(R.string.vpn_notification_disconnecting_title, R.string.vpn_notification_disconnecting_message),
    }

    companion object {
        const val ACTION_LOCAL_BIND = "com.ganj.vpn.action.BIND_LOCAL_TUNNEL"
        const val ACTION_PREPARE = "com.ganj.vpn.action.PREPARE_TUNNEL"
        const val ACTION_DISCONNECT = "com.ganj.vpn.action.DISCONNECT"
        private const val NOTIFICATION_CHANNEL_ID = "ganj_vpn_connection"
        private const val NOTIFICATION_ID = 4201
        private const val PREPARE_TIMEOUT_MILLIS = 30_000L
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
