package com.karuhundeveloper.notification.aurapay.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.BatteryManager
import android.os.PowerManager
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayDatabase
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayPreferences
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayRecord
import com.karuhundeveloper.notification.aurapay.model.AuraPayConfig
import com.karuhundeveloper.notification.aurapay.model.DeviceHealth
import com.karuhundeveloper.notification.aurapay.model.PaymentStatus
import com.karuhundeveloper.notification.aurapay.model.ServerKpis
import com.karuhundeveloper.notification.aurapay.network.AuraPayRelayClient
import com.karuhundeveloper.notification.aurapay.parser.UpiPaymentParser
import com.karuhundeveloper.notification.aurapay.security.IdempotencyGenerator
import com.karuhundeveloper.notification.aurapay.service.AuraPayForegroundService
import com.karuhundeveloper.notification.aurapay.wakelock.WakeLockManager
import com.karuhundeveloper.notification.service.AppNotificationListener
import com.karuhundeveloper.notification.ui.screen.isNotificationListenerEnabled
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Calendar

class AuraPayViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AuraPayDatabase.getInstance(application)
    private val dao = db.auraPayDao()
    private val prefs = AuraPayPreferences.getInstance(application)
    private val relayClient = AuraPayRelayClient()
    private val wakeLockManager = WakeLockManager.getInstance(application)

    val config: StateFlow<AuraPayConfig> = prefs.configFlow

    // Live Feed (Last 15 records)
    val recentRecords: StateFlow<List<AuraPayRecord>> = dao.observeRecent(15)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // All records for ledger
    val allRecords: StateFlow<List<AuraPayRecord>> = dao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // Device Health Flow
    private val _deviceHealth = MutableStateFlow(getLiveDeviceHealth())
    val deviceHealth: StateFlow<DeviceHealth> = _deviceHealth.asStateFlow()

    // Server KPI Metrics Flow
    private val _kpis = MutableStateFlow(ServerKpis())
    val kpis: StateFlow<ServerKpis> = _kpis.asStateFlow()

    private val _selectedTab = MutableStateFlow("ALL")
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    init {
        // Start foreground service if not started
        try {
            AuraPayForegroundService.start(application)
        } catch (_: Exception) {}

        refreshDeviceHealth()
        observeLocalKpis()
    }

    fun setLedgerTab(tab: String) {
        _selectedTab.value = tab
    }

    fun refreshDeviceHealth() {
        _deviceHealth.value = getLiveDeviceHealth()
    }

    private fun observeLocalKpis() {
        viewModelScope.launch {
            val cal = Calendar.getInstance().apply {
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val startOfDay = cal.timeInMillis

            combine(
                dao.observeTodayAmount(startOfDay),
                dao.observeAllTimeAmount(),
                dao.countTotal(),
                dao.countMatched(),
                dao.countOrphans()
            ) { today, allTime, total, matched, orphans ->
                ServerKpis(
                    todayAmount = today,
                    allTimeAmount = allTime,
                    totalForwarded = total,
                    matchedOrdersCount = matched,
                    unmatchedOrphansCount = orphans,
                    activePendingOrdersCount = 0
                )
            }.collect { calculatedKpis ->
                _kpis.value = calculatedKpis
            }
        }
    }

    fun saveConfig(newConfig: AuraPayConfig) {
        prefs.saveConfig(newConfig)
    }

    /**
     * Simulates the exact capture sample:
     * "₹1.01 received from AARAV MISHRA at 12:05 am"
     */
    fun simulateGpayNotification(onComplete: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val sampleText = "₹1.01 received from AARAV MISHRA at 12:05 am"
            val packageName = "com.google.nbu.paisa.merchant"

            val payment = UpiPaymentParser.parse("", sampleText, packageName)
            if (payment == null) {
                onComplete("Failed to parse sample text")
                return@launch
            }

            val idempotencyKey = IdempotencyGenerator.create(
                payment.amount,
                payment.payerName,
                payment.notificationTime
            )

            val record = AuraPayRecord(
                idempotencyKey = idempotencyKey,
                amount = payment.amount,
                payerName = payment.payerName,
                notificationTime = payment.notificationTime,
                sourceApp = payment.sourceApp,
                rawText = payment.rawText,
                status = PaymentStatus.LOCAL_QUEUE,
                timestamp = System.currentTimeMillis()
            )

            dao.insert(record)

            val currentConfig = prefs.loadConfig()
            val result = relayClient.sendPaymentWebhook(record, currentConfig)
            if (result.success && result.status != null) {
                dao.updateStatus(idempotencyKey, result.status, result.matchedOrderId)
                onComplete("Success: ${result.status} (Order: ${result.matchedOrderId ?: "None"})")
            } else {
                dao.updateStatus(idempotencyKey, PaymentStatus.LOCAL_QUEUE, error = result.error)
                onComplete("Queued locally: ${result.error}")
            }
        }
    }

    fun manualQueueFlush(onComplete: (Int) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val pending = dao.getPendingQueue()
            val currentConfig = prefs.loadConfig()
            var flushed = 0
            for (item in pending) {
                val res = relayClient.sendPaymentWebhook(item, currentConfig)
                if (res.success && res.status != null) {
                    dao.updateStatus(item.idempotencyKey, res.status, res.matchedOrderId)
                    flushed++
                }
            }
            onComplete(flushed)
        }
    }

    fun testDatabaseConnection(onResult: (String) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentConfig = prefs.loadConfig()
            val response = relayClient.testDatabase(currentConfig)
            onResult(response)
        }
    }

    fun updateServerConfig(
        mongoUri: String,
        upiVpa: String,
        merchantName: String,
        onResult: (String) -> Unit
    ) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentConfig = prefs.loadConfig()
            val response = relayClient.updateSystemConfig(
                currentConfig,
                mongoUri.ifBlank { null },
                upiVpa.ifBlank { null },
                merchantName.ifBlank { null }
            )
            // Also update local preferences
            val updated = currentConfig.copy(
                mongoUri = mongoUri.ifBlank { currentConfig.mongoUri },
                merchantUpiVpa = upiVpa.ifBlank { currentConfig.merchantUpiVpa },
                merchantName = merchantName.ifBlank { currentConfig.merchantName }
            )
            prefs.saveConfig(updated)
            onResult(response)
        }
    }

    fun claimOrphan(idempotencyKey: String, orderId: String, onResult: (Boolean) -> Unit) {
        viewModelScope.launch(Dispatchers.IO) {
            val currentConfig = prefs.loadConfig()
            val success = relayClient.claimOrphan(currentConfig, idempotencyKey, orderId)
            if (success) {
                dao.updateStatus(idempotencyKey, PaymentStatus.MATCHED_CREDITED, orderId)
            }
            onResult(success)
        }
    }

    private fun getLiveDeviceHealth(): DeviceHealth {
        val app = getApplication<Application>()
        val batteryIntent = app.registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
        val level = batteryIntent?.getIntExtra(BatteryManager.EXTRA_LEVEL, -1) ?: -1
        val scale = batteryIntent?.getIntExtra(BatteryManager.EXTRA_SCALE, -1) ?: -1
        val batteryPct = if (level != -1 && scale != -1) (level * 100 / scale) else 100
        val status = batteryIntent?.getIntExtra(BatteryManager.EXTRA_STATUS, -1) ?: -1
        val isCharging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
                status == BatteryManager.BATTERY_STATUS_FULL

        val connManager = app.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val activeNetwork = connManager?.activeNetwork
        val caps = connManager?.getNetworkCapabilities(activeNetwork)
        val netType = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Cellular"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
            else -> "Disconnected"
        }

        val powerManager = app.getSystemService(Context.POWER_SERVICE) as? PowerManager
        val isOptBypassed = powerManager?.isIgnoringBatteryOptimizations(app.packageName) == true

        return DeviceHealth(
            batteryLevel = batteryPct,
            isCharging = isCharging,
            networkType = netType,
            cpuWakeLock = wakeLockManager.isCpuLockHeld(),
            wifiLock = wakeLockManager.isWifiLockHeld(),
            listenerActive = AppNotificationListener.isConnected.value || isNotificationListenerEnabled(app),
            batteryOptBypassed = isOptBypassed,
            appVersion = "1.0.0"
        )
    }
}
