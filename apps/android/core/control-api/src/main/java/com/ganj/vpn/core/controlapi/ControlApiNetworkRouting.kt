package com.ganj.vpn.core.controlapi

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import java.io.IOException
import java.net.URL
import java.net.URLConnection

/** Route only origin-validated control-plane HTTPS outside our own TUN while it is held.
 * A dead VPN node must not prevent obtaining an authorized replacement profile.
 * This never changes process-wide routing or routes other applications around the VPN.
 */
object ControlApiNetworkRouting {
    @Volatile private var open: (URL) -> URLConnection = { it.openConnection() }

    fun install(context: Context, ownsTunnel: () -> Boolean) {
        val connectivity = context.applicationContext.getSystemService(ConnectivityManager::class.java)
        open = { url ->
            if (!ownsTunnel()) url.openConnection()
            else {
                val underlying = connectivity.allNetworks.mapNotNull { network ->
                    connectivity.getNetworkCapabilities(network)?.takeIf { capabilities ->
                        capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
                            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_VPN)
                    }?.let { network to it.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) }
                }.maxByOrNull { if (it.second) 1 else 0 }?.first
                    ?: throw IOException("control_api.underlying_network_unavailable")
                underlying.openConnection(url)
            }
        }
    }

    internal fun openConnection(url: URL): URLConnection = open(url)
}
