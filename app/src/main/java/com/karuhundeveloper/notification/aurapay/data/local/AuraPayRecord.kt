package com.karuhundeveloper.notification.aurapay.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.karuhundeveloper.notification.aurapay.model.PaymentStatus

@Entity(
    tableName = "aurapay_records",
    indices = [
        Index(value = ["status"]),
        Index(value = ["timestamp"])
    ]
)
data class AuraPayRecord(
    @PrimaryKey
    val idempotencyKey: String,
    val amount: Double,
    val payerName: String,
    val notificationTime: String,
    val sourceApp: String,
    val rawText: String,
    val status: String = PaymentStatus.LOCAL_QUEUE,
    val matchedOrderId: String? = null,
    val timestamp: Long = System.currentTimeMillis(),
    val retryCount: Int = 0,
    val lastError: String? = null
)
