package com.sumitrack.android.data.local

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Base64

class JwtDecoderTest {

    private fun buildToken(payloadJson: String, segments: Int = 3): String {
        val encodedPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(payloadJson.toByteArray(Charsets.UTF_8))
        return listOf("header", encodedPayload, "signature").take(segments).joinToString(".")
    }

    @Test
    fun `decodeTenantId extracts tenant_id from valid JWT payload`() {
        val token = buildToken("""{"sub":"user-1","tenant_id":"abc-123","jti":"xyz"}""")
        assertEquals("abc-123", JwtDecoder.decodeTenantId(token))
    }

    @Test
    fun `decodeTenantId returns null when payload has no tenant_id claim`() {
        val token = buildToken("""{"sub":"user-1"}""")
        assertNull(JwtDecoder.decodeTenantId(token))
    }

    @Test
    fun `decodeTenantId returns null for malformed token without dots`() {
        assertNull(JwtDecoder.decodeTenantId("not-a-jwt"))
    }

    @Test
    fun `decodeTenantId returns null when token has only two segments`() {
        val token = buildToken("""{"tenant_id":"abc-123"}""", segments = 2)
        assertNull(JwtDecoder.decodeTenantId(token))
    }

    @Test
    fun `decodeTenantId returns null when tenant_id claim is blank`() {
        val token = buildToken("""{"sub":"user-1","tenant_id":""}""")
        assertNull(JwtDecoder.decodeTenantId(token))
    }

    @Test
    fun `decodeTenantId returns null when payload is not valid JSON`() {
        val badPayload = Base64.getUrlEncoder().withoutPadding()
            .encodeToString("not-json".toByteArray(Charsets.UTF_8))
        val token = "header.$badPayload.signature"
        assertNull(JwtDecoder.decodeTenantId(token))
    }
}
