package com.ganj.vpn.vpn

import android.net.VpnService

/**
 * Platform boundary for the future audited Xray wrapper.
 *
 * No tunnel is opened until a device-bound encrypted profile has been verified.
 */
class GanjVpnService : VpnService()
