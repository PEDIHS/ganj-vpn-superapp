package com.ganj.vpn.core.xray

/** Both attempts must use the same proxy/TUN; never fall back to a direct internet request. */
object ProxyProbeFallback {
    val targets = listOf("https://cp.cloudflare.com/", "https://www.gstatic.com/generate_204")

    fun measure(targets: List<String> = this.targets, request: (String) -> NativeProbeResult): NativeProbeResult {
        var last: NativeProbeResult = NativeProbeResult.Failed("probe.unavailable")
        for (target in targets.take(2)) {
            last = try { request(target) } catch (error: Exception) {
                NativeProbeResult.Failed(NativeFailureClassifier.probe(error))
            }
            if (last is NativeProbeResult.Measured) return last
            if (last is NativeProbeResult.Failed && !last.code.startsWith("probe.")) return last
        }
        return last
    }
}
