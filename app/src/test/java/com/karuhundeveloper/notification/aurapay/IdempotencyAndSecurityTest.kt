package com.karuhundeveloper.notification.aurapay

import com.karuhundeveloper.notification.aurapay.security.HmacSigner
import com.karuhundeveloper.notification.aurapay.security.IdempotencyGenerator
import org.junit.Assert.*
import org.junit.Test

class IdempotencyAndSecurityTest {

    @Test
    fun testDeterministicIdempotencyKey() {
        val key1 = IdempotencyGenerator.create(
            amount = 1.01,
            payerName = "AARAV MISHRA",
            notificationTime = "12:05 am",
            dateOverride = "2026-11-15"
        )

        // Same with mixed case and leading/trailing spaces
        val key2 = IdempotencyGenerator.create(
            amount = 1.01,
            payerName = "  aarav mishra  ",
            notificationTime = " 12:05 AM ",
            dateOverride = "2026-11-15"
        )

        assertEquals("Deterministic idempotency keys must be identical", key1, key2)
        assertEquals(64, key1.length) // SHA-256 hex length
    }

    @Test
    fun testHmacSignerIntegrity() {
        val secret = "aurapay_secret_shared_key_2026"
        val payload = """{"amount":1.01,"payerName":"AARAV MISHRA"}"""

        val signature = HmacSigner.sign(payload, secret)
        assertEquals(64, signature.length) // HMAC-SHA256 hex length

        val tamperedPayload = """{"amount":100.01,"payerName":"AARAV MISHRA"}"""
        val tamperedSig = HmacSigner.sign(tamperedPayload, secret)

        assertNotEquals("Signatures must differ when payload is tampered", signature, tamperedSig)
    }
}
