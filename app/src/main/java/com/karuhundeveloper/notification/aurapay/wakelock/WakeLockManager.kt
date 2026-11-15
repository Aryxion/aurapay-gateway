package com.karuhundeveloper.notification.aurapay.wakelock

import android.content.Context
import android.net.wifi.WifiManager
import android.os.PowerManager
import android.util.Log

class WakeLockManager(private val context: Context) {

    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
    private val wifiManager = context.applicationContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager

    private var cpuWakeLock: PowerManager.WakeLock? = null
    private var wifiLock: WifiManager.WifiLock? = null

    @Synchronized
    fun acquireLocks() {
        try {
            if (cpuWakeLock == null) {
                cpuWakeLock = powerManager?.newWakeLock(
                    PowerManager.PARTIAL_WAKE_LOCK,
                    "AuraPay:CpuPartialWakeLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "CPU PARTIAL_WAKE_LOCK acquired")
            }

            if (wifiLock == null) {
                @Suppress("DEPRECATION")
                wifiLock = wifiManager?.createWifiLock(
                    WifiManager.WIFI_MODE_FULL_HIGH_PERF,
                    "AuraPay:WifiHighPerfLock"
                )?.apply {
                    setReferenceCounted(false)
                    acquire()
                }
                Log.d(TAG, "High-performance Wi-Fi Lock acquired")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire hardware locks", e)
        }
    }

    @Synchronized
    fun releaseLocks() {
        try {
            cpuWakeLock?.let {
                if (it.isHeld) it.release()
            }
            cpuWakeLock = null

            wifiLock?.let {
                if (it.isHeld) it.release()
            }
            wifiLock = null
            Log.d(TAG, "Hardware locks released")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release hardware locks", e)
        }
    }

    fun isCpuLockHeld(): Boolean = cpuWakeLock?.isHeld == true

    fun isWifiLockHeld(): Boolean = wifiLock?.isHeld == true

    companion object {
        private const val TAG = "WakeLockManager"

        @Volatile
        private var INSTANCE: WakeLockManager? = null

        fun getInstance(context: Context): WakeLockManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: WakeLockManager(context.applicationContext).also { INSTANCE = it }
            }
        }
    }
}
