package com.ganj.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ganj.vpn.MainActivity
import com.ganj.vpn.R
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.core.vpn.SecureProfileRecoveryStore
import com.ganj.vpn.core.vpn.VpnReconnectCoordinator
import com.ganj.vpn.core.xray.AndroidXrayEngine
import com.ganj.vpn.core.xray.ReflectiveLibXrayBridge
import com.ganj.vpn.core.xray.TunnelDevice
import com.ganj.vpn.core.xray.TunnelPlatform
import com.ganj.vpn.core.xray.VpnRuntimeException
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
    private val recoveryStore by lazy { SecureProfileRecoveryStore(applicationContext) }
    private val connectivityManager by lazy { getSystemService(ConnectivityManager::class.java) }

    @Volatile
    private var reconnectJob: Job? = null

    @Volatile
    private var networkCallbackRegistered = false

    private val engine by lazy {
        AndroidXrayEngine(
            platform = this,
            native = ReflectiveLibXrayBridge(applicationContext.classLoader),
        )
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onLost(network: Network) {
            if (!desiredConnection.get()) return
            networkLossObserved.set(true)
            // Android may deliver new-network onAvailable before old-network onLost. If a new
            // default already exists, reconnect now; otherwise wait for its onAvailable callback.
            if (
                connectivityManager.activeNetwork != null &&
                networkLossObserved.compareAndSet(true, false)
            ) {
                scheduleNetworkReconnect()
            }
        }

        override fun onAvailable(network: Network) {
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
                VpnRuntimeState.publish(engine.currentState())
                delay(250)
            }
        }
        networkCallbackRegistered = runCatching {
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
            true
        }.getOrDefault(false)
    }

    override fun onBind(intent: Intent): IBinder? =
        if (intent.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> startForeground(NOTIFICATION_ID, connectionNotification())
            ACTION_DISCONNECT -> {
                terminateDesiredConnection(clearRecovery = true)
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
                val result = engine.connect(ConnectionRequest(restored))
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

            val result = engine.reconnect(ConnectionRequest(restored))
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
        engine.disconnect()
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
        engine.close()
        VpnRuntimeState.publish(engine.currentState())
        super.onDestroy()
    }

    inner class LocalBinder internal constructor() : Binder() {
        suspend fun connect(request: ConnectionRequest): Result<Unit> = withContext(Dispatchers.IO) {
            reconnectCoordinator.invalidate()
            reconnectJob?.cancel()
            reconnectJob = null
            networkLossObserved.set(false)
            startForeground(NOTIFICATION_ID, connectionNotification())

            val persisted = recoveryStore.save(request.profile)
            if (persisted.isFailure) {
                desiredConnection.set(false)
                request.profile.close()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return@withContext Result.failure(
                    persisted.exceptionOrNull()
                        ?: IllegalStateException("vpn.profile_recovery_write_failed"),
                )
            }

            desiredConnection.set(true)
            engine.connect(request).onFailure {
                desiredConnection.set(false)
                recoveryStore.clear()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
            desiredConnection.set(false)
            networkLossObserved.set(false)
            reconnectCoordinator.invalidate()
            reconnectJob?.cancel()
            reconnectJob = null
            recoveryStore.clear()
            engine.disconnect().also {
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
