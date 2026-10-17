package com.ganj.vpn.vpn

import com.ganj.vpn.core.xray.NativeFailureClassifier
import com.ganj.vpn.core.xray.NativeProbeResult

/** v2rayNG semantics for the already established Android VPN tunnel. */
internal object ActiveTunnelDelaySampler {
    fun measure(targets: List<String>, request: (String) -> NativeProbeResult): NativeProbeResult {
        var last: NativeProbeResult = NativeProbeResult.Failed("probe.unavailable")
        for (target in targets.take(2)) {
            var best: Long? = null
            repeat(2) {
                val result = try { request(target) }
                    catch (error: Exception) { NativeProbeResult.Failed(NativeFailureClassifier.probe(error)) }
                when (result) {
                    is NativeProbeResult.Measured ->
                        if (best == null || result.millis < best!!) best = result.millis
                    is NativeProbeResult.Failed -> last = result
                }
            }
            if (best != null) return NativeProbeResult.Measured(best!!)
        }
        return last
    }
}
