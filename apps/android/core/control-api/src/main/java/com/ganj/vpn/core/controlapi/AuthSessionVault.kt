package com.ganj.vpn.core.controlapi

interface AuthSessionVault {
    fun restore(): AuthSessionCredentials?
    fun save(session: AuthSessionCredentials): Result<Unit>
    fun clear(): Result<Unit>
}
