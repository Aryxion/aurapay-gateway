package com.karuhundeveloper.notification.aurapay.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.karuhundeveloper.notification.MainActivity
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayDatabase
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayPreferences
import com.karuhundeveloper.notification.aurapay.model.DeviceHealth
import com.karuhundeveloper.notification.aurapay.network.AuraPayRelayClient
import com.karuhundeveloper.notification.aurapay.wakelock.WakeLockManager
import com.karuhundeveloper.notification.service.AppNotificationListener
import com.karuhundeveloper.notification.ui.screen.isNotificationListenerEnabled
import kotlinx.coroutines.*
import java.util.Calendar

class AuraPayForegroundService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private lateinit var wakeLockManager: WakeLockManager
    private lateinit var preferences: AuraPayPreferences
    private lateinit var database: AuraPayDatabase
    private val relayClient = AuraPayRelayClient()

    private var forwardedCount = 0
    private var todayAmount = 0.0

    override fun onCreate() {
        super.onCreate()
        wakeLockManager = WakeLockManager.getInstance(applicationContext)
        preferences = AuraPayPreferences.getInstance(applicationContext)
        database = AuraPayDatabase.getInstance(applicationContext)

        // Acquire CPU Partial WakeLock and Wi-Fi High Perf Lock
        wakeLockManager.acquireLocks()

        createNotificationChannel()
        startAsForeground()

        // Observe local DB counts to update notification text live
        observeStats()

        // Start background loop for heartbeat and offline queue retry
        startBackgroundLoops()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        wakeLockManager.acquireLocks()
        return START_STICKY
    }

    private fun startAsForeground() {
        val notification = buildOngoingNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun buildOngoingNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val subtext = "Forwarded: $forwardedCount | Today: ₹${"%.2f".format(todayAmount)}"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("AuraPay Relay Engine Active")
            .setContentText(subtext)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }

    private fun updateNotification() {
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
        manager?.notify(NOTIFICATION_ID, buildOngoingNotification())
    }

    private fun observeStats() {
        serviceScope.launch {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis

            launch {
                database.auraPayDao().countTotal().collect { count ->
                    forwardedCount = count
                    updateNotification()
                }
            }
            launch {
                database.auraPayDao().observeTodayAmount(startOfDay).collect { amt ->
                    todayAmount = amt
                    updateNotification()
                }
            }
        }
    }

    private fun startBackgroundLoops() {
        // Loop 1: Offline Queue flusher (every 15s)
        serviceScope.launch {
            while (isActive) {
                delay(15_000L)
                flushOfflineQueue()
            }
        }

        // Loop 2: Device Health Heartbeat (every 60s)
        serviceScope.launch {
            while (isActive) {
                delay(60_000L)
                sendDeviceHeartbeat()
            }
        }
    }

    suspend fun flushOfflineQueue() {
        val pending = database.auraPayDao().getPendingQueue()
        if (pending.isEmpty()) return

        val config = preferences.loadConfig()
        Log.d(TAG, "Flushing ${pending.size} offline queued payments...")
        for (item in pending) {
            val response = relayClient.sendPaymentWebhook(item, config)
            if (response.success && response.status != null) {
                database.auraPayDao().updateStatus(
                    item.idempotencyKey,
                    response.status,
                    response.matchedOrderId
                )
            }
        }
    }

    private suspend fun sendDeviceHeartbeat() {
        val health = getLiveDeviceHealth()
        val config = preferences.loadConfig()
        relayClient.sendHeartbeat(health, config)
    }

    private fun getLiveDeviceHealth(): DeviceHealth {
        val batteryIntent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val connManager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNetwork)
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Disconnected"
        }

        val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isOptBypassed = powerManager?.isIgnoringBatteryOptimizations(packageName) == true

        return DeviceHealth(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            networkType = netType,
            cpuWakeLock = wakeLockManager.isCpuLockHeld(),
            wifiLock = wakeLockManager.isWifiLockHeld(),
            listenerActive = AppNotificationListener.isConnected.value || isNotificationListenerEnabled(this),
            batteryOptBypassed = isOptBypassed,
            appVersion = "1.0.0"
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "AuraPay Relay Engine Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Keeps the payment relay engine alive and shows live metrics"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        wakeLockManager.releaseLocks()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "AuraPayService"
        private const val CHANNEL_ID = "aurapay_relay_foreground_channel"
        private const val NOTIFICATION_ID = 202611

        fun start(context: Context) {
            val intent = Intent(context, AuraPayForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
