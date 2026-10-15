package com.ganj.vpn.vpn

/** Revocation is immediate when observed; transport failures get a bounded five-minute grace. */
internal class ServiceAccessLease(private val startedAt: Long) {
    private var lastVerifiedAt: Long = startedAt
    fun verified(now: Long) {lastVerifiedAt=now}
    fun expired(now: Long): Boolean = now < lastVerifiedAt || now-lastVerifiedAt>=300_000L
}
