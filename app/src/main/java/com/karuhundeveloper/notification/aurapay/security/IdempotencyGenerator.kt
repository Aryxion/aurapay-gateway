package com.karuhundeveloper.notification.aurapay.security

import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object IdempotencyGenerator {

    /**
     * Generates a deterministic SHA-256 idempotency key:
     * `SHA256("${amount}_${normalizedPayer}_${normalizedTime}_${YYYY-MM-DD}")`
     */
    fun create(
        amount: Double,
        payerName: String,
        notificationTime: String,
        dateOverride: String? = null
    ): String {
        val dateStr = dateOverride ?: SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date())
        val normalizedPayer = payerName.trim().uppercase(Locale.US)
        val normalizedTime = notificationTime.trim().lowercase(Locale.US)

        val raw = "${amount}_${normalizedPayer}_${normalizedTime}_$dateStr"
        val digest = MessageDigest.getInstance("SHA-256")
        val hashBytes = digest.digest(raw.toByteArray(Charsets.UTF_8))
        return hashBytes.joinToString("") { "%02x".format(it) }
    }
}
