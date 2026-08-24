package com.ganj.vpn.composition

import com.ganj.vpn.core.billing.ProviderClientAction
import com.ganj.vpn.presentation.CheckoutActionHandle
import com.ganj.vpn.presentation.CheckoutActionVault
import java.security.SecureRandom

fun interface CheckoutActionHandleGenerator {
    fun next(): CheckoutActionHandle
}

/** Bounded process-local lease vault. A handle is invalid immediately after its first consume. */
class OneTimeCheckoutActionVault(
    private val handles: CheckoutActionHandleGenerator = SecureCheckoutActionHandleGenerator(),
    private val maximumEntries: Int = 8,
) : CheckoutActionVault {
    private val actions = linkedMapOf<CheckoutActionHandle, ProviderClientAction.LaunchGooglePlay>()

    init {
        require(maximumEntries in 1..32)
    }

    @Synchronized
    override fun store(action: ProviderClientAction.LaunchGooglePlay): CheckoutActionHandle {
        while (actions.size >= maximumEntries) {
            actions.remove(actions.entries.first().key)
        }
        repeat(8) {
            val handle = handles.next()
            if (!actions.containsKey(handle)) {
                actions[handle] = action
                return handle
            }
        }
        throw IllegalStateException("Unable to allocate an opaque checkout action handle")
    }

    @Synchronized
    override fun consume(handle: CheckoutActionHandle): ProviderClientAction.LaunchGooglePlay? =
        actions.remove(handle)

    @Synchronized
    override fun clear() {
        actions.clear()
    }
}

private class SecureCheckoutActionHandleGenerator : CheckoutActionHandleGenerator {
    private val random = SecureRandom()

    override fun next(): CheckoutActionHandle {
        val bytes = ByteArray(16).also(random::nextBytes)
        val value = bytes.joinToString(separator = "") { byte ->
            (byte.toInt() and 0xff).toString(16).padStart(2, '0')
        }
        bytes.fill(0)
        return CheckoutActionHandle("gp_action_$value")
    }
}
