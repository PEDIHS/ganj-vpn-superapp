package com.ganj.vpn.vpn

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.os.ParcelFileDescriptor
import com.ganj.vpn.MainActivity
import com.ganj.vpn.R
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.core.vpn.ConnectionState
import com.ganj.vpn.core.xray.AndroidXrayEngine
import com.ganj.vpn.core.xray.ReflectiveLibXrayBridge
import com.ganj.vpn.core.xray.TunnelDevice
import com.ganj.vpn.core.xray.TunnelPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Android's privileged tunnel boundary.
 *
 * Requests stay in-process and contain only short-lived profiles issued for the signed-in user's
 * verified entitlement. No Intent extra, file, clipboard value, QR code, or share-link can start a
 * tunnel.
 */
class GanjVpnService : VpnService(), TunnelPlatform {
    private val localBinder = LocalBinder()
    private val engine by lazy {
        AndroidXrayEngine(
            platform = this,
            native = ReflectiveLibXrayBridge(applicationContext.classLoader),
        )
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onBind(intent: Intent): IBinder? =
        if (intent.action == ACTION_LOCAL_BIND) localBinder else super.onBind(intent)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PREPARE -> startForeground(NOTIFICATION_ID, connectionNotification())
            ACTION_DISCONNECT -> {
                engine.disconnect()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
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
        engine.disconnect()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
        super.onRevoke()
    }

    override fun onDestroy() {
        engine.close()
        super.onDestroy()
    }

    inner class LocalBinder internal constructor() : Binder() {
        suspend fun connect(request: ConnectionRequest): Result<Unit> = withContext(Dispatchers.IO) {
            startForeground(NOTIFICATION_ID, connectionNotification())
            engine.connect(request).onFailure {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
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
