package com.karuhundeveloper.notification.aurapay.model

data class DeviceHealth(
    val batteryLevel: Int = 100,
    val isCharging: Boolean = false,
    val networkType: String = "Wi-Fi",
    val cpuWakeLock: Boolean = true,
    val wifiLock: Boolean = true,
    val listenerActive: Boolean = true,
    val batteryOptBypassed: Boolean = true,
    val appVersion: String = "1.0.0"
)

data class ServerKpis(
    val todayAmount: Double = 0.0,
    val allTimeAmount: Double = 0.0,
    val totalForwarded: Int = 0,
    val matchedOrdersCount: Int = 0,
    val unmatchedOrphansCount: Int = 0,
    val activePendingOrdersCount: Int = 0
)
