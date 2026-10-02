package com.ganj.vpn.ui

import com.ganj.vpn.core.controlapi.ConnectionServer

internal val ServiceServersUiState.message: String
    get() = (this as? ServiceServersUiState.Error)?.message.orEmpty()

internal val ServiceServersUiState.items: List<ConnectionServer>
    get() = (this as? ServiceServersUiState.Ready)?.items.orEmpty()
