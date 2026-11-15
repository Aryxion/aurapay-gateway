package com.karuhundeveloper.notification.aurapay.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karuhundeveloper.notification.aurapay.ui.screen.ConfigScreen
import com.karuhundeveloper.notification.aurapay.ui.screen.LedgerScreen
import com.karuhundeveloper.notification.aurapay.ui.screen.TelemetryScreen
import com.karuhundeveloper.notification.util.ServiceUtils

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuraPayMainScreen(
    viewModel: AuraPayViewModel = viewModel(),
    onOpenLegacyRules: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentScreen by remember { mutableStateOf(0) } // 0: Telemetry, 1: Ledger, 2: Config

    val config by viewModel.config.collectAsStateWithLifecycle()
    val health by viewModel.deviceHealth.collectAsStateWithLifecycle()
    val kpis by viewModel.kpis.collectAsStateWithLifecycle()
    val recentRecords by viewModel.recentRecords.collectAsStateWithLifecycle()
    val allRecords by viewModel.allRecords.collectAsStateWithLifecycle()
    val selectedLedgerTab by viewModel.selectedTab.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(if (health.listenerActive && health.cpuWakeLock) Color(0xFF10B981) else Color(0xFFF59E0B))
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "AuraPay Relay Engine",
                                fontWeight = FontWeight.Bold,
                                fontSize = 17.sp
                            )
                            Text(
                                text = if (health.listenerActive) "Native Engine Online" else "Listener Unbound",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    // Rebind listener button
                    IconButton(onClick = {
                        ServiceUtils.toggleNotificationListenerService(context)
                        viewModel.refreshDeviceHealth()
                    }) {
                        Icon(Icons.Default.Sync, contentDescription = "Rebind Listener")
                    }

                    // Battery optimization prompt
                    if (!health.batteryOptBypassed) {
                        IconButton(onClick = {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                    data = Uri.parse("package:${context.packageName}")
                                }
                                context.startActivity(intent)
                            }
                        }) {
                            Icon(Icons.Default.BatteryAlert, contentDescription = "Bypass Doze", tint = Color(0xFFEF4444))
                        }
                    }

                    // Open notification listener settings
                    IconButton(onClick = {
                        context.startActivity(Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS"))
                    }) {
                        Icon(Icons.Default.Notifications, contentDescription = "Listener Settings")
                    }

                    // Switch to legacy rules button
                    IconButton(onClick = onOpenLegacyRules) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Legacy Forwarder")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    selected = currentScreen == 0,
                    onClick = { currentScreen = 0 },
                    icon = { Icon(Icons.Default.Dashboard, contentDescription = "Telemetry") },
                    label = { Text("Telemetry") }
                )
                NavigationBarItem(
                    selected = currentScreen == 1,
                    onClick = { currentScreen = 1 },
                    icon = { Icon(Icons.Default.ReceiptLong, contentDescription = "Ledger") },
                    label = { Text("Ledger") }
                )
                NavigationBarItem(
                    selected = currentScreen == 2,
                    onClick = { currentScreen = 2 },
                    icon = { Icon(Icons.Default.Tune, contentDescription = "Config") },
                    label = { Text("Config") }
                )
            }
        }
    ) { innerPadding ->
        when (currentScreen) {
            0 -> TelemetryScreen(
                health = health,
                kpis = kpis,
                recentPayments = recentRecords,
                onRefreshHealth = { viewModel.refreshDeviceHealth() },
                modifier = Modifier.padding(innerPadding)
            )
            1 -> LedgerScreen(
                records = allRecords,
                selectedTab = selectedLedgerTab,
                onTabSelected = { viewModel.setLedgerTab(it) },
                onClaimOrphan = { key, orderId ->
                    viewModel.claimOrphan(key, orderId) {}
                },
                modifier = Modifier.padding(innerPadding)
            )
            2 -> ConfigScreen(
                config = config,
                onSaveConfig = { viewModel.saveConfig(it) },
                onSimulateNotification = { onRes -> viewModel.simulateGpayNotification(onRes) },
                onManualQueueFlush = { onRes -> viewModel.manualQueueFlush(onRes) },
                onTestDbConnection = { onRes -> viewModel.testDatabaseConnection(onRes) },
                onUpdateServerConfig = { mongo, vpa, name, onRes ->
                    viewModel.updateServerConfig(mongo, vpa, name, onRes)
                },
                modifier = Modifier.padding(innerPadding)
            )
        }
    }
}
