package com.karuhundeveloper.notification.aurapay.security

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object HmacSigner {

    /**
     * Signs the JSON body payload with HMAC-SHA256 using [secretKey].
     * Returns a lowercase hexadecimal signature string.
     */
    fun sign(payloadJson: String, secretKey: String): String {
        val mac = Mac.getInstance("HmacSHA256")
        val secretKeySpec = SecretKeySpec(secretKey.toByteArray(Charsets.UTF_8), "HmacSHA256")
        mac.init(secretKeySpec)
        val hmacBytes = mac.doFinal(payloadJson.toByteArray(Charsets.UTF_8))
        return hmacBytes.joinToString("") { "%02x".format(it) }
    }
}
