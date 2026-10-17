package com.ganj.vpn.core.xray

/**
 * One native Xray instance per bounded group/URL. The native library itself
 * performs v2rayNG-compatible two GET requests with a reusable HTTP transport
 * and returns the fastest successful response. Do NOT measure multiple
 * separate native cold handshakes or reaggregate them in Kotlin.
 */
internal object ProxyDelaySampler {
    fun measure(
        count: Int,
        urls: List<String>,
        request: (targetUrl: String, configIndexes: List<Int>) -> List<NativeProbeResult>,
    ): List<NativeProbeResult> {
        if (count == 0) return emptyList()
        val finalResults = MutableList<NativeProbeResult?>(count) { null }
        var unresolved = (0 until count).toList()
        for (target in urls.take(2)) {
            if (unresolved.isEmpty()) break
            val responses = try {
                request(target, unresolved)
            } catch (error: Exception) {
                List(unresolved.size) { NativeProbeResult.Failed(NativeFailureClassifier.probe(error)) }
            }
            val next = mutableListOf<Int>()
            unresolved.forEachIndexed { offset, index ->
                when (val reply = responses.getOrNull(offset) ?: NativeProbeResult.Failed("probe.native_failed")) {
                    is NativeProbeResult.Measured -> finalResults[index] = reply
                    is NativeProbeResult.Failed -> {
                        finalResults[index] = reply
                        if (reply.code.startsWith("probe.")) next += index
                    }
                }
            }
            unresolved = next
        }
        return finalResults.map { it ?: NativeProbeResult.Failed("probe.unavailable") }
    }
}
