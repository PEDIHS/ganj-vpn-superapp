package com.ganj.vpn.vpn

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.IBinder
import com.ganj.vpn.core.vpn.ConnectionRequest
import com.ganj.vpn.presentation.TunnelConnector
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class AndroidVpnTunnelClient(
    context: Context,
) : TunnelConnector {
    private val applicationContext = context.applicationContext

    override suspend fun connect(request: ConnectionRequest): Result<Unit> {
        val started = runCatching {
            val intent = Intent(applicationContext, GanjVpnService::class.java)
                .setAction(GanjVpnService.ACTION_PREPARE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                applicationContext.startForegroundService(intent)
            } else {
                applicationContext.startService(intent)
            }
        }
        if (started.isFailure) return Result.failure(
            started.exceptionOrNull() ?: IllegalStateException("vpn.service_start_failed"),
        )

        val bound = try {
            withContext(Dispatchers.Main.immediate) { bind() }
        } catch (cancelled: CancellationException) {
            runCatching { applicationContext.stopService(Intent(applicationContext, GanjVpnService::class.java)) }
            throw cancelled
        } catch (error: RuntimeException) {
            runCatching { applicationContext.stopService(Intent(applicationContext, GanjVpnService::class.java)) }
            return Result.failure(error)
        }
        return try {
            withContext(Dispatchers.IO) { bound.binder.connect(request) }
        } finally {
            withContext(Dispatchers.Main.immediate) {
                runCatching { applicationContext.unbindService(bound.connection) }
            }
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.Main.immediate) {
        runCatching {
            applicationContext.startService(
                Intent(applicationContext, GanjVpnService::class.java)
                    .setAction(GanjVpnService.ACTION_DISCONNECT),
            )
        }.map { Unit }
    }

    private suspend fun bind(): BoundService = suspendCancellableCoroutine { continuation ->
        var registered = false
        val connection = object : ServiceConnection {
            override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
                val binder = service as? GanjVpnService.LocalBinder
                if (binder == null) {
                    if (continuation.isActive) {
                        continuation.resumeWithException(IllegalStateException("vpn.invalid_service_binder"))
                    }
                    return
                }
                if (continuation.isActive) continuation.resume(BoundService(binder, this))
            }

            override fun onServiceDisconnected(name: ComponentName?) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("vpn.service_disconnected"))
                }
            }

            override fun onNullBinding(name: ComponentName?) {
                if (continuation.isActive) {
                    continuation.resumeWithException(IllegalStateException("vpn.service_binding_rejected"))
                }
            }
        }
        registered = applicationContext.bindService(
            Intent(applicationContext, GanjVpnService::class.java)
                .setAction(GanjVpnService.ACTION_LOCAL_BIND),
            connection,
            Context.BIND_AUTO_CREATE,
        )
        if (!registered) {
            continuation.resumeWithException(IllegalStateException("vpn.service_bind_failed"))
            return@suspendCancellableCoroutine
        }
        continuation.invokeOnCancellation {
            if (registered) runCatching { applicationContext.unbindService(connection) }
        }
    }

    private data class BoundService(
        val binder: GanjVpnService.LocalBinder,
        val connection: ServiceConnection,
    )
}
