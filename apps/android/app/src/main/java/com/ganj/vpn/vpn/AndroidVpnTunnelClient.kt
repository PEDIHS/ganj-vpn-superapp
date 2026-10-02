package com.ganj.vpn.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.presentation.ActiveTunnelProbe
import com.ganj.vpn.presentation.TunnelConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidVpnTunnelClient(context: Context) : TunnelConnector {
    private val applicationContext = context.applicationContext

    override suspend fun connect(request: ConnectionRequest): Result<Unit> = try {
        val intent = Intent(applicationContext, GanjVpnService::class.java)
            .setAction(GanjVpnService.ACTION_PREPARE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) applicationContext.startForegroundService(intent)
        else applicationContext.startService(intent)
        withBoundService { it.connect(request) }
    } catch (cancelled: CancellationException) {
        applicationContext.stopService(Intent(applicationContext, GanjVpnService::class.java))
        throw cancelled
    } catch (_: Exception) {
        applicationContext.stopService(Intent(applicationContext, GanjVpnService::class.java))
        Result.failure(IllegalStateException("vpn.service_start_failed"))
    }

    override suspend fun disconnect(): Result<Unit> = try {
        // Wait for core stop and TUN close, not merely delivery of a service Intent.
        withBoundService { it.disconnect() }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.failure(IllegalStateException("vpn.service_disconnect_failed"))
    }

    override suspend fun probe(profile: com.ganj.vpn.core.vpn.ProvisionedProfile): Long? = try {
        withBoundService { it.probe(profile) }
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        null
    } finally {
        profile.close()
    }

    override suspend fun probeActive(serviceId: String, serverId: String): ActiveTunnelProbe =
        withBoundService { it.probeActive(serviceId, serverId) }

    private suspend fun <T> withBoundService(block: suspend (GanjVpnService.LocalBinder) -> T): T {
        val bound = withTimeoutOrNull(10_000) { withContext(Dispatchers.Main.immediate) { bind() } }
            ?: error("vpn.service_bind_timeout")
        return try {
            block(bound.binder)
        } finally {
            // Cancellation must not skip unbinding and leak the service/activity.
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                runCatching { applicationContext.unbindService(bound.connection) }
            }
        }
    }

    private suspend fun bind(): BoundService = suspendCancellableCoroutine { continuation ->
        var registered = false
        lateinit var connection: ServiceConnection
        fun fail(code: String) {
            if (registered) {
                runCatching { applicationContext.unbindService(connection) }
                registered = false
            }
            if (continuation.isActive) continuation.resumeWithException(IllegalStateException(code))
        }
        connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? GanjVpnService.LocalBinder
                if (binder == null) fail("vpn.invalid_service_binder")
                else if (continuation.isActive) continuation.resume(BoundService(binder, this))
            }
            override fun onServiceDisconnected(name: ComponentName?) = fail("vpn.service_disconnected")
            override fun onNullBinding(name: ComponentName?) = fail("vpn.service_binding_rejected")
            override fun onBindingDied(name: ComponentName?) = fail("vpn.service_binding_died")
        }
        registered = applicationContext.bindService(
            Intent(applicationContext, GanjVpnService::class.java).setAction(GanjVpnService.ACTION_LOCAL_BIND),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (!registered) fail("vpn.service_bind_failed")
        continuation.invokeOnCancellation {
            if (registered) runCatching { applicationContext.unbindService(connection) }
        }
    }

    private data class BoundService(val binder: GanjVpnService.LocalBinder, val connection: ServiceConnection)
}
