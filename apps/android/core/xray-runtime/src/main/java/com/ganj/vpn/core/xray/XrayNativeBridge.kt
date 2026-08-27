package com.ganj.vpn.core.xray

import java.lang.reflect.Method
import java.lang.reflect.Proxy

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
) : XrayNativeBridge {
    @Volatile
    private var socketCallback: Any? = null

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
        val callback = Proxy.newProxyInstance(classLoader, arrayOf(callbackType)) { _, method, arguments ->
            proxyCallback(method, arguments, protector)
        }
        register.invoke(null, callback)
        val setDns = bridgeClass.methods.firstOrNull {
            it.name.equals("setDNS", ignoreCase = true) && it.parameterTypes.size == 2
        } ?: error("libXray protected DNS API is unavailable")
        setDns.invoke(null, callback, PROTECTED_DNS)
        socketCallback = callback
        NativeCallResult(true)
    }.getOrElse { NativeCallResult(false, "xray.socket_protection_unavailable") }

    override fun start(config: SensitiveXrayConfig): NativeCallResult = invoke(
        method = "runXrayFromJson",
        payloadName = "configJSON",
        payloadValue = config.consume(),
    )

    override fun stop(): NativeCallResult {
        val stopped = invoke(method = "stopXray")
        runCatching {
            bridgeClass.methods.firstOrNull {
                it.name.equals("resetDNS", ignoreCase = true) && it.parameterTypes.isEmpty()
            }?.invoke(null)
        }
        socketCallback = null
        return stopped
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
        if (SUCCESS_PATTERN.containsMatchIn(response)) NativeCallResult(true)
        else NativeCallResult(false, "xray.native_rejected")
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
        const val PROTECTED_DNS = "1.1.1.1:53"
        val SUCCESS_PATTERN = Regex("\\\"success\\\"\\s*:\\s*true")
    }
}
