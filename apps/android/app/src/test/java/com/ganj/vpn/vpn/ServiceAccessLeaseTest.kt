package com.ganj.vpn.vpn
import org.junit.Assert.*
import org.junit.Test
class ServiceAccessLeaseTest {
 @Test fun `temporary outage does not interrupt immediately but authorization cannot outlive grace`() {
  val lease=ServiceAccessLease(1000);assertFalse(lease.expired(300999));assertTrue(lease.expired(301000))
 }
 @Test fun `verified access renews grace and clock reversal fails closed`() {
  val lease=ServiceAccessLease(1000);lease.verified(31000);assertFalse(lease.expired(301000));assertTrue(lease.expired(331000));assertTrue(lease.expired(20000))
 }
}
