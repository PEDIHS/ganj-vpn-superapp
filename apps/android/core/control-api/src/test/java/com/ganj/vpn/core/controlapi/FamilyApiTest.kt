package com.ganj.vpn.core.controlapi

import org.junit.Assert.*
import org.junit.Test

class FamilyApiTest {
    @Test fun `invitation is a signed explicit POST and secret is not put in request URL or diagnostics`() {
        val transport=FakeTransport().apply {enqueue(200,successEnvelope("""{"joined":true,"service_id":"$SERVICE_ID"}"""))}
        val secret="z".repeat(43)
        var signedPath=""
        val api=DefaultFamilyApi(transport,AuthTokenProvider{TOKEN},AuthenticationEventSink.NONE){path,body->
            signedPath=path;assertEquals("{\"token\":\"$secret\"}",String(body));"gdp1.test-signature"
        }
        val result=api.accept(secret).requireSuccess().value
        assertEquals(SERVICE_ID,result.serviceId)
        assertEquals("/v1/family/accept",signedPath)
        assertEquals(HttpMethod.POST,transport.requests.single().method)
        assertFalse(transport.requests.single().pathAndQuery.contains(secret))
        assertFalse(transport.requests.single().toString().contains(secret))
        assertFalse(result.toString().contains(secret))
    }
    @Test fun `missing signing key fails closed before network`() {
        val transport=FakeTransport()
        val api=DefaultFamilyApi(transport,AuthTokenProvider{TOKEN},AuthenticationEventSink.NONE){_,_->null}
        assertTrue(api.createInvite(SERVICE_ID) is ApiResult.Failure)
        assertTrue(transport.requests.isEmpty())
    }
    @Test fun `family maps shared capacity and only actual device labels`() {
        val transport=FakeTransport().apply {enqueue(200,successEnvelope("""{"service_id":"$SERVICE_ID","service_name":"Economy","owner":false,"device_limit":3,"slots_used":2,"members":[{"user_id":"$DEVICE_ID","name":"Member","current":true}],"devices":[{"id":"$DEVICE_ID","name":"Samsung SM-S928B","own":true,"current":true}],"invites":[]}"""))}
        val api=DefaultFamilyApi(transport,AuthTokenProvider{TOKEN},AuthenticationEventSink.NONE){_,_->null}
        val result=api.summary(SERVICE_ID).requireSuccess().value
        assertEquals(3,result.deviceLimit);assertEquals(2,result.slotsUsed);assertFalse(result.owner)
        assertEquals("Samsung SM-S928B",result.devices.single().name)
    }
}
