package com.ganj.vpn.vpn

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.ganj.vpn.core.xray.ReflectiveLibXrayBridge
import com.ganj.vpn.core.xray.SocketProtector
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeRuntimeContractTest {
    @Test
    fun packagedRuntimeSupportsProtectedSocketsAndDns() {
        // No tunnel or network request is started by this contract check.
        val bridge = ReflectiveLibXrayBridge()
        val result = bridge.installSocketProtector(SocketProtector { false })
        assertTrue(result.errorCode ?: "native socket/DNS contract missing", result.success)
        assertTrue(bridge.stop().success)
    }
}
