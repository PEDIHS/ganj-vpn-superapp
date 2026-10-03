package com.ganj.vpn.core.xray

import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.io.File
import org.json.JSONObject
import org.json.JSONArray

fun interface SocketProtector {
    fun protect(fileDescriptor: Int): Boolean
}

data class NativeCallResult(val success: Boolean, val errorCode: String? = null)

interface XrayNativeBridge {
    fun installSocketProtector(protector: SocketProtector): NativeCallResult
    fun start(config: SensitiveXrayConfig): NativeCallResult
    fun stop(): NativeCallResult
}

/** Thin adapter for the gomobile class generated from the pinned XTLS/libXray source. */
class ReflectiveLibXrayBridge(
    private val classLoader: ClassLoader = ReflectiveLibXrayBridge::class.java.classLoader!!,
    private val protectedDnsServer: () -> String = { PROTECTED_DNS },
    private val fixtureFailureObserver: (String) -> Unit = {},
) : XrayNativeBridge {
    private val bridgeClass: Class<*> by lazy {
        listOf("libXray.LibXRay", "libXray.LibXray")
            .firstNotNullOfOrNull { runCatching { classLoader.loadClass(it) }.getOrNull() }
            ?: throw IllegalStateException("Pinned libXray runtime is unavailable")
    }

    override fun installSocketProtector(protector: SocketProtector): NativeCallResult = runCatching {
        val register = bridgeClass.methods.firstOrNull {
            it.name.equals("registerDialerController", ignoreCase = true) && it.parameterTypes.size == 1
        } ?: error("libXray socket-protection API is unavailable")
        val callbackType = register.parameterTypes.single()
        require(callbackType.isInterface) { "libXray socket callback is not an interface" }
        // Upstream registration appends: a callback per connection leaves stale rejectors behind.
        // Register once per native class and atomically replace only its current delegate.
        val state = synchronized(protectionStates) {
            protectionStates.getOrPut(bridgeClass) { ProtectionState() }
        }
        val callback = synchronized(state) {
            state.protector = protector
            state.callback ?: Proxy.newProxyInstance(classLoader, arrayOf(callbackType)) { proxy, method, arguments ->
                if (method.name == "equals") proxy === arguments?.firstOrNull()
                else proxyCallback(method, arguments, SocketProtector { fd -> state.protector?.protect(fd) == true })
            }.also {
                register.invoke(null, it)
                state.callback = it
            }
        }
        val setDns = bridgeClass.methods.firstOrNull {
            it.name.equals("setDNS", ignoreCase = true) && it.parameterTypes.size == 2
        } ?: error("libXray protected DNS API is unavailable")
        setDns.invoke(null, callback, protectedDnsServer())
        NativeCallResult(true)
    }.getOrElse { NativeCallResult(false, "xray.socket_protection_unavailable") }

    override fun start(config: SensitiveXrayConfig): NativeCallResult {
        val started = invoke(
            method = "runXrayFromJson",
            payloadName = "configJSON",
            payloadValue = config.consume(),
        )
        if (!started.success) return started
        // A successful invoke is not a packet-level probe, but also verify that
        // the managed Core did not immediately stop after its startup response.
        val running = runCatching {
            val response = invokeMethod().invoke(
                null, "{\"apiVersion\":1,\"method\":\"getXrayState\",\"payload\":{}}",
            ) as? String ?: return@runCatching false
            val parsed = JSONObject(response)
            parsed.optBoolean("success") && parsed.optJSONObject("data")?.optBoolean("running") == true
        }.getOrDefault(false)
        return if (running) started else NativeCallResult(false, "xray.core_not_running")
    }

    override fun stop(): NativeCallResult {
        val stopped = invoke(method = "stopXray")
        runCatching {
            bridgeClass.methods.firstOrNull {
                it.name.equals("resetDNS", ignoreCase = true) && it.parameterTypes.isEmpty()
            }?.invoke(null)
        }
        return stopped
    }

    /** Independent outbound instance; never invokes start/stop on the managed VPN core. */
    fun probe(config: SensitiveXrayConfig, privateDirectory: File, targetUrl: String = ProxyProbeFallback.targets.first()): Long? =
        (probeDetailed(config, privateDirectory, listOf(targetUrl)) as? NativeProbeResult.Measured)?.millis

    fun probeDetailed(
        config: SensitiveXrayConfig,
        privateDirectory: File,
        targetUrls: List<String> = ProxyProbeFallback.targets,
    ): NativeProbeResult {
        var file: File? = null
        return try {
            file = File.createTempFile("probe-", ".json", privateDirectory)
            check(file.setReadable(false, false) && file.setWritable(false, false))
            check(file.setReadable(true, true) && file.setWritable(true, true))
            file.writeText(config.consume())
            val probeFile = file
            ProxyProbeFallback.measure(targetUrls) { target ->
                val item = JSONObject().put("configPath", probeFile.absolutePath).put("outboundTag", "proxy")
                val payload = JSONObject().put("configs", JSONArray().put(item)).put("timeout", 5).put("url", target)
                val request = JSONObject().put("apiVersion", 1).put("method", "pingBatch").put("payload", payload)
                val response = JSONObject(invokeMethod().invoke(null, request.toString()) as String)
                if (!response.optBoolean("success")) {
                    val raw = response.optString("error")
                    val startup = NativeFailureClassifier.startup(raw)
                    return@measure NativeProbeResult.Failed(if (startup == "xray.config_invalid") startup else NativeFailureClassifier.probe(raw))
                }
                val result = response.optJSONObject("data")?.optJSONArray("results")?.optJSONObject(0)
                    ?: return@measure NativeProbeResult.Failed("probe.native_failed")
                // Upstream omits delay when it is exactly zero; success still makes it a valid measurement.
                val delay = if (!result.has("delay")) 0L else (result.opt("delay") as? Number)?.toLong()
                    ?: return@measure NativeProbeResult.Failed("probe.native_failed")
                if (result.optBoolean("success") && delay >= 0) NativeProbeResult.Measured(delay)
                else {
                    val raw = result.optString("error")
                    val startup = NativeFailureClassifier.startup(raw)
                    NativeProbeResult.Failed(if (startup == "xray.config_invalid") startup else NativeFailureClassifier.probe(raw))
                }
            }
        } catch (error: Exception) {
            NativeProbeResult.Failed(NativeFailureClassifier.probe(error))
        } finally {
            file?.delete()
            config.close()
        }
    }

    private fun invoke(
        method: String,
        payloadName: String? = null,
        payloadValue: String? = null,
    ): NativeCallResult = runCatching {
        val request = if (payloadName == null) {
            "{\"apiVersion\":1,\"method\":\"$method\",\"payload\":{}}"
        } else {
            "{\"apiVersion\":1,\"method\":\"$method\",\"payload\":{\"$payloadName\":${json(payloadValue.orEmpty())}}}"
        }
        val response = invokeMethod().invoke(null, request) as? String
            ?: return NativeCallResult(false, "xray.invalid_native_response")
        if (JSONObject(response).optBoolean("success")) NativeCallResult(true)
        else {
            fixtureFailureObserver(response)
            NativeCallResult(false, NativeFailureClassifier.startup(JSONObject(response).optString("error")))
        }
    }.getOrElse { NativeCallResult(false, "xray.native_call_failed") }

    private fun invokeMethod(): Method = bridgeClass.methods.firstOrNull {
        it.name.equals("invoke", ignoreCase = true) &&
            it.parameterTypes.contentEquals(arrayOf(String::class.java))
    } ?: error("libXray Invoke API is unavailable")

    private fun proxyCallback(method: Method, arguments: Array<out Any?>?, protector: SocketProtector): Any? {
        if (method.declaringClass == Any::class.java) {
            return when (method.name) {
                "toString" -> "GanjSocketProtector([REDACTED])"
                "hashCode" -> System.identityHashCode(this)
                "equals" -> false
                else -> null
            }
        }
        val descriptor = arguments?.firstOrNull() as? Number ?: return false
        return protector.protect(descriptor.toInt())
    }

    private fun json(value: String): String = buildString(value.length + 2) {
        append('"')
        value.forEach { character ->
            when (character) {
                '"' -> append("\\\"")
                '\\' -> append("\\\\")
                '\n' -> append("\\n")
                '\r' -> append("\\r")
                '\t' -> append("\\t")
                else -> if (character.code < 0x20) append("\\u%04x".format(character.code)) else append(character)
            }
        }
        append('"')
    }

    private companion object {
        val protectionStates = java.util.WeakHashMap<Class<*>, ProtectionState>()
        const val PROTECTED_DNS = "1.1.1.1:53"
    }

    private class ProtectionState {
        @Volatile var protector: SocketProtector? = null
        var callback: Any? = null
    }
}
