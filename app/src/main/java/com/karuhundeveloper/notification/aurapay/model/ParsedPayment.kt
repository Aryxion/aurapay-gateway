package com.karuhundeveloper.notification.aurapay.model

data class ParsedPayment(
    val amount: Double,
    val payerName: String,
    val notificationTime: String,
    val sourceApp: String,
    val rawText: String,
    val timestampMs: Long = System.currentTimeMillis()
)
