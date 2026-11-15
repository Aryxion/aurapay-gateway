package com.karuhundeveloper.notification.aurapay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayRecord
import com.karuhundeveloper.notification.aurapay.model.DeviceHealth
import com.karuhundeveloper.notification.aurapay.model.PaymentStatus
import com.karuhundeveloper.notification.aurapay.model.ServerKpis
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun TelemetryScreen(
    health: DeviceHealth,
    kpis: ServerKpis,
    recentPayments: List<AuraPayRecord>,
    onRefreshHealth: () -> Unit,
    modifier: Modifier = Modifier
) {
    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        contentPadding = PaddingValues(vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ── 1. Server KPI Summary Cards ──────────────────────────────────────
        item {
            Text(
                text = "Live Reconciliation KPIs",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiCard(
                    title = "Today's Received",
                    value = "₹${"%.2f".format(kpis.todayAmount)}",
                    icon = Icons.Default.CurrencyRupee,
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                KpiCard(
                    title = "All-Time Total",
                    value = "₹${"%.2f".format(kpis.allTimeAmount)}",
                    icon = Icons.Default.AccountBalanceWallet,
                    color = Color(0xFF3B82F6),
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                KpiSmallCard(
                    title = "Total Forwarded",
                    value = "${kpis.totalForwarded}",
                    modifier = Modifier.weight(1f)
                )
                KpiSmallCard(
                    title = "Matched",
                    value = "${kpis.matchedOrdersCount}",
                    color = Color(0xFF10B981),
                    modifier = Modifier.weight(1f)
                )
                KpiSmallCard(
                    title = "Orphans",
                    value = "${kpis.unmatchedOrphansCount}",
                    color = Color(0xFFF59E0B),
                    modifier = Modifier.weight(1f)
                )
            }
        }

        // ── 2. Device Health Strip ───────────────────────────────────────────
        item {
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Native Unkillable Health",
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                        IconButton(onClick = onRefreshHealth, modifier = Modifier.size(28.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Refresh", modifier = Modifier.size(16.dp))
                        }
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HealthBadge(
                            label = "Battery",
                            value = "${health.batteryLevel}%${if (health.isCharging) " ⚡" else ""}",
                            isGood = health.batteryLevel > 20
                        )
                        HealthBadge(
                            label = "Network",
                            value = health.networkType,
                            isGood = health.networkType != "Disconnected"
                        )
                        HealthBadge(
                            label = "CPU Lock",
                            value = if (health.cpuWakeLock) "Held" else "Off",
                            isGood = health.cpuWakeLock
                        )
                        HealthBadge(
                            label = "Wi-Fi Lock",
                            value = if (health.wifiLock) "HighPerf" else "Off",
                            isGood = health.wifiLock
                        )
                    }
                    Spacer(modifier = Modifier.height(10.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        HealthBadge(
                            label = "Listener Service",
                            value = if (health.listenerActive) "Active" else "Action Req",
                            isGood = health.listenerActive
                        )
                        HealthBadge(
                            label = "Battery Opt",
                            value = if (health.batteryOptBypassed) "Unrestricted" else "Restricted",
                            isGood = health.batteryOptBypassed
                        )
                    }
                }
            }
        }

        // ── 3. Live Notification Feed (Last 15) ──────────────────────────────
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Live Payment Feed",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Last ${recentPayments.size} events",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (recentPayments.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No payments captured yet.\nUse 'Simulate GPay Notification' in Config tab to test.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        } else {
            items(recentPayments, key = { it.idempotencyKey }) { record ->
                PaymentItemCard(record)
            }
        }
    }
}

@Composable
fun KpiCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = color.copy(alpha = 0.12f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(title, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = color)
        }
    }
}

@Composable
fun KpiSmallCard(
    title: String,
    value: String,
    color: Color = MaterialTheme.colorScheme.onSurface,
    modifier: Modifier = Modifier
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(title, fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(modifier = Modifier.height(2.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold, color = color)
        }
    }
}

@Composable
fun HealthBadge(label: String, value: String, isGood: Boolean) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(2.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(6.dp))
                .background(if (isGood) Color(0xFF10B981).copy(alpha = 0.15f) else Color(0xFFEF4444).copy(alpha = 0.15f))
                .padding(horizontal = 6.dp, vertical = 2.dp)
        ) {
            Text(
                value,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = if (isGood) Color(0xFF10B981) else Color(0xFFEF4444)
            )
        }
    }
}

@Composable
fun PaymentItemCard(record: AuraPayRecord) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${"%.2f".format(record.amount)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.payerName,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                }
                StatusBadge(record.status)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                val appLabel = when {
                    record.sourceApp.contains("google.nbu.paisa.merchant") -> "GPay Business"
                    record.sourceApp.contains("phonepe") -> "PhonePe"
                    record.sourceApp.contains("paytm") -> "Paytm"
                    record.sourceApp.contains("bharatpe") -> "BharatPe"
                    else -> record.sourceApp.substringAfterLast('.')
                }
                Text(
                    text = "$appLabel • ${record.notificationTime.ifBlank { SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(record.timestamp)) }}",
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!record.matchedOrderId.isNullOrBlank()) {
                    Text(
                        text = "Order: ${record.matchedOrderId}",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Key: ${record.idempotencyKey.take(16)}...",
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
        }
    }
}

@Composable
fun StatusBadge(status: String) {
    val (bg, fg, label) = when (status) {
        PaymentStatus.MATCHED_CREDITED -> Triple(Color(0xFF10B981).copy(alpha = 0.15f), Color(0xFF10B981), "CREDITED")
        PaymentStatus.UNMATCHED_ORPHAN -> Triple(Color(0xFFF59E0B).copy(alpha = 0.15f), Color(0xFFF59E0B), "ORPHAN")
        PaymentStatus.DUPLICATE_IGNORED -> Triple(Color(0xFF3B82F6).copy(alpha = 0.15f), Color(0xFF3B82F6), "DUPLICATE")
        PaymentStatus.LOCAL_QUEUE -> Triple(Color(0xFF8B5CF6).copy(alpha = 0.15f), Color(0xFF8B5CF6), "QUEUED")
        else -> Triple(Color(0xFFEF4444).copy(alpha = 0.15f), Color(0xFFEF4444), status)
    }

    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(bg)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = label,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            color = fg
        )
    }
}
