package com.ganj.vpn.core.xray

/**
 * Per-config real-delay sampling. Each sample must be a successful HTTPS request
 * through the actual Xray outbound, NEVER an endpoint TCP port check.
 *
 * The fallback URL is attempted only when the primary test endpoint cannot
 * yield two successful measurements. Round trips are not adjusted or clamped.
 */
internal object ProxyDelaySampler {
    private const val REPEAT_COUNT = 3
    private const val MIN_SAMPLES = 2

    fun measure(
        count: Int,
        urls: List<String>,
        request: (targetUrl: String, configIndexes: List<Int>) -> List<NativeProbeResult>,
    ): List<NativeProbeResult> {
        if (count == 0) return emptyList()
        val finalResults = MutableList<NativeProbeResult?>(count) { null }
        val lastFailure = MutableList<NativeProbeResult.Failed>(count) {
            NativeProbeResult.Failed("probe.unavailable")
        }
        var unresolved = (0 until count).toList()

        for (target in urls.take(2)) {
            if (unresolved.isEmpty()) break
            val successes = Array(count) { mutableListOf<Long>() }
            var active = unresolved
            repeat(REPEAT_COUNT) {
                if (active.isEmpty()) return@repeat
                val replies = try {
                    request(target, active)
                } catch (error: Exception) {
                    List(active.size) { NativeProbeResult.Failed(NativeFailureClassifier.probe(error)) }
                }
                active.forEachIndexed { offset, index ->
                    when (val reply = replies.getOrNull(offset)
                        ?: NativeProbeResult.Failed("probe.native_failed")) {
                        is NativeProbeResult.Measured -> successes[index] += reply.millis
                        is NativeProbeResult.Failed -> {
                            lastFailure[index] = reply
                            if (!reply.code.startsWith("probe.")) finalResults[index] = reply
                        }
                    }
                }
                // No point retrying a config that failed before even completing one
                // proxy request; try the alternative target instead.
                active = active.filter { finalResults[it] == null && successes[it].isNotEmpty() }
            }
            unresolved.forEach { index ->
                if (finalResults[index] == null && successes[index].size >= MIN_SAMPLES) {
                    val values = successes[index].sorted()
                    val center = values.size / 2
                    val median = if (values.size % 2 == 1) values[center]
                        else values[center - 1] + (values[center] - values[center - 1]) / 2
                    finalResults[index] = NativeProbeResult.Measured(median)
                }
            }
            unresolved = unresolved.filter { finalResults[it] == null }
        }
        return finalResults.mapIndexed { index, result -> result ?: lastFailure[index] }
    }
}
