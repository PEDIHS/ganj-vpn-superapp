package com.ganj.vpn.presentation

/** Idle network RTT based on fresh TCP handshakes, not HTTP/TLS/backend waiting time.
 * Two or more successful attempts are required; unreachable is never reported as 0ms.
 */
internal class TcpLatencySampler(private val handshakeMillis: () -> Long?) {
    fun measure(): Long? {
        val samples = mutableListOf<Long>()
        repeat(3) { index ->
            val elapsed = handshakeMillis()?.takeIf { it >= 0L }
            if (elapsed != null) samples += elapsed
            // An address that cannot answer the first SYN should not cost three timeouts.
            if (index == 0 && elapsed == null) return null
        }
        if (samples.size < 2) return null
        val ordered = samples.sorted()
        val middle = ordered.size / 2
        return if (ordered.size % 2 == 1) ordered[middle]
        else ordered[middle - 1] + (ordered[middle] - ordered[middle - 1]) / 2
    }
}
