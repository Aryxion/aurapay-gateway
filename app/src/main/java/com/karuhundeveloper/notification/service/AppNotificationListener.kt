package com.karuhundeveloper.notification.service

import android.app.Notification
import android.content.ComponentName
import android.content.pm.PackageManager
import android.os.Build
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayDatabase
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayPreferences
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayRecord
import com.karuhundeveloper.notification.aurapay.model.PaymentStatus
import com.karuhundeveloper.notification.aurapay.network.AuraPayRelayClient
import com.karuhundeveloper.notification.aurapay.parser.UpiPaymentParser
import com.karuhundeveloper.notification.aurapay.security.IdempotencyGenerator
import com.karuhundeveloper.notification.aurapay.service.AuraPayForegroundService
import com.karuhundeveloper.notification.data.local.AppDatabase
import com.karuhundeveloper.notification.delivery.DeliveryRepository
import com.karuhundeveloper.notification.delivery.DeliveryScheduler
import com.karuhundeveloper.notification.delivery.EventIdentity
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

class AppNotificationListener : NotificationListenerService() {
    private var scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val database by lazy { AppDatabase.getInstance(applicationContext) }
    private val auraPayDb by lazy { AuraPayDatabase.getInstance(applicationContext) }
    private val auraPayPrefs by lazy { AuraPayPreferences.getInstance(applicationContext) }
    private val relayClient by lazy { AuraPayRelayClient() }
    private val repository by lazy { DeliveryRepository(database) }
    private val scheduler by lazy { DeliveryScheduler(applicationContext) }
    private val installationId by lazy {
        val prefs = getSharedPreferences("listener_identity", MODE_PRIVATE)
        prefs.getString("id", null) ?: UUID.randomUUID().toString().also {
            check(prefs.edit().putString("id", it).commit()) { "Cannot persist listener identity" }
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        if (!scope.isActive) scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        connected.value = true

        // Ensure persistent foreground service is active
        try {
            AuraPayForegroundService.start(applicationContext)
        } catch (e: Exception) {
            Log.w(TAG, "Could not start AuraPayForegroundService from listener", e)
        }

        scope.launch {
            try {
                scheduler.installRecovery()
                scheduler.recover()
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Cannot recover delivery queue", e) }
        }
        try {
            activeNotifications?.forEach { capture(it) }
        } catch (e: SecurityException) {
            Log.w(TAG, "Notification access unavailable during catch-up", e)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        connected.value = false
        requestRebind(ComponentName(this, AppNotificationListener::class.java))
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        sbn?.let { capture(it) }
    }

    private fun capture(sbn: StatusBarNotification) {
        val extras = sbn.notification.extras
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.takeIf { it.isNotBlank() }
            ?: extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)?.joinToString("\n").orEmpty()
        if (title.isBlank() && text.isBlank()) return
        val capturedAt = System.currentTimeMillis()

        scope.launch {
            // ── 1. AuraPay Real-Time Payment Parsing & Relay Ingestion ─────────
            try {
                processAuraPayment(title, text, sbn.packageName, capturedAt)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "AuraPay processing failed for package=" + sbn.packageName, e) }

            // ── 2. Standard Webhook Forwarding Pipeline (Backward Compatible) ───
            try {
                val eventId = EventIdentity.create(installationId, sbn.key, sbn.postTime, title, text)
                val ids = repository.capture(eventId, sbn.packageName, resolveAppLabel(sbn.packageName),
                    title, text, capturedAt, sbn.postTime)
                ids.forEach { scheduler.enqueue(it) }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { Log.e(TAG, "Capture or scheduling failed for package=" + sbn.packageName, e) }
        }
    }

    private suspend fun processAuraPayment(
        title: String,
        text: String,
        packageName: String,
        capturedAt: Long
    ) {
        val config = auraPayPrefs.loadConfig()
        val lowerPkg = packageName.lowercase()
        // Match user custom packages or any major UPI/merchant provider
        val isWhitelisted = config.targetPackages.isEmpty() ||
                config.targetPackages.any { it.equals(packageName, ignoreCase = true) } ||
                lowerPkg.contains("paisa") ||
                lowerPkg.contains("phonepe") ||
                lowerPkg.contains("paytm") ||
                lowerPkg.contains("bharatpe") ||
                lowerPkg.contains("bhim") ||
                packageName in listOf(
                    "com.google.android.apps.nbu.paisa.merchant",
                    "com.google.nbu.paisa.merchant",
                    "com.google.android.apps.nbu.paisa.user",
                    "com.phonepe.app.business",
                    "com.phonepe.app",
                    "com.paytm.business",
                    "net.one97.paytm",
                    "com.bharatpe.app",
                    "com.bharatpe.merchant",
                    "in.org.npci.upiapp"
                )

        if (!isWhitelisted) {
            Log.d(TAG, "Ignoring non-whitelisted package: $packageName")
            return
        }

        // Parse payment using multi-app engine and non-payment filter
        val payment = UpiPaymentParser.parse(title, text, packageName)
        if (payment == null) {
            Log.d(TAG, "Notification from $packageName did not match any payment pattern: title='$title', text='$text'")
            return
        }

        // Compute deterministic SHA-256 idempotency key
        val idempotencyKey = IdempotencyGenerator.create(
            payment.amount,
            payment.payerName,
            payment.notificationTime
        )

        // Save into local Room database as LOCAL_QUEUE
        val record = AuraPayRecord(
            idempotencyKey = idempotencyKey,
            amount = payment.amount,
            payerName = payment.payerName,
            notificationTime = payment.notificationTime,
            sourceApp = payment.sourceApp,
            rawText = payment.rawText,
            status = PaymentStatus.LOCAL_QUEUE,
            timestamp = capturedAt
        )

        val insertedId = auraPayDb.auraPayDao().insert(record)
        if (insertedId == -1L) {
            Log.d(TAG, "Payment with idempotencyKey $idempotencyKey already in local database")
        }

        // Forward immediately to AuraPay relay backend
        val result = relayClient.sendPaymentWebhook(record, config)
        if (result.success && result.status != null) {
            auraPayDb.auraPayDao().updateStatus(
                idempotencyKey,
                result.status,
                result.matchedOrderId
            )
            Log.i(TAG, "AuraPay forwarded successfully: status=${result.status}, order=${result.matchedOrderId}")
        } else {
            Log.w(TAG, "AuraPay webhook forward failed: ${result.error}. Queued for offline retry.")
            auraPayDb.auraPayDao().updateStatus(
                idempotencyKey,
                PaymentStatus.LOCAL_QUEUE,
                error = result.error
            )
        }
    }

    override fun onDestroy() {
        connected.value = false
        scope.cancel()
        super.onDestroy()
    }

    private fun resolveAppLabel(packageName: String): String = try {
        val info = if (Build.VERSION.SDK_INT >= 33) {
            packageManager.getApplicationInfo(packageName, PackageManager.ApplicationInfoFlags.of(0))
        } else {
            @Suppress("DEPRECATION")
            packageManager.getApplicationInfo(packageName, 0)
        }
        packageManager.getApplicationLabel(info).toString()
    } catch (_: PackageManager.NameNotFoundException) { packageName }

    companion object {
        private const val TAG = "AppNotificationListener"
        private val connected = MutableStateFlow(false)
        val isConnected = connected.asStateFlow()
    }
}
