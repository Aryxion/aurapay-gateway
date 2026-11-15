package com.karuhundeveloper.notification.aurapay.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.karuhundeveloper.notification.util.ServiceUtils

class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.i(TAG, "Device boot event received: $action. Initiating AuraPay Relay Engine...")

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            "android.intent.action.QUICKBOOT_POWERON",
            "com.htc.intent.action.QUICKBOOT_POWERON",
            Intent.ACTION_REBOOT -> {
                // 1. Auto-start persistent foreground service
                AuraPayForegroundService.start(context)

                // 2. Request NotificationListener re-bind to handle OEM restart quirks
                ServiceUtils.toggleNotificationListenerService(context)
            }
        }
    }

    companion object {
        private const val TAG = "BootReceiver"
    }
}
