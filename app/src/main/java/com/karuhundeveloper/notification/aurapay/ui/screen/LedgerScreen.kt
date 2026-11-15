package com.karuhundeveloper.notification.aurapay.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AssignmentTurnedIn
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karuhundeveloper.notification.aurapay.data.local.AuraPayRecord
import com.karuhundeveloper.notification.aurapay.model.PaymentStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LedgerScreen(
    records: List<AuraPayRecord>,
    selectedTab: String,
    onTabSelected: (String) -> Unit,
    onClaimOrphan: (idempotencyKey: String, orderId: String) -> Unit,
    modifier: Modifier = Modifier
) {
    var claimingRecord by remember { mutableStateOf<AuraPayRecord?>(null) }
    var orderIdInput by remember { mutableStateOf("") }

    val filteredRecords = remember(records, selectedTab) {
        when (selectedTab) {
            "CREDITED" -> records.filter { it.status == PaymentStatus.MATCHED_CREDITED }
            "ORPHANS" -> records.filter { it.status == PaymentStatus.UNMATCHED_ORPHAN }
            "QUEUE" -> records.filter { it.status == PaymentStatus.LOCAL_QUEUE }
            else -> records
        }
    }

    Column(modifier = modifier.fillMaxSize()) {
        // ── Categorized Tabs Row ─────────────────────────────────────────────
        PrimaryTabRow(
            selectedTabIndex = when (selectedTab) {
                "CREDITED" -> 1
                "ORPHANS" -> 2
                "QUEUE" -> 3
                else -> 0
            }
        ) {
            Tab(
                selected = selectedTab == "ALL",
                onClick = { onTabSelected("ALL") },
                text = { Text("All (${records.size})", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == "CREDITED",
                onClick = { onTabSelected("CREDITED") },
                icon = { Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp)) },
                text = { Text("Credited", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == "ORPHANS",
                onClick = { onTabSelected("ORPHANS") },
                icon = { Icon(Icons.Default.Warning, contentDescription = null, modifier = Modifier.size(14.dp)) },
                text = { Text("Orphans", fontSize = 12.sp) }
            )
            Tab(
                selected = selectedTab == "QUEUE",
                onClick = { onTabSelected("QUEUE") },
                icon = { Icon(Icons.Default.HourglassEmpty, contentDescription = null, modifier = Modifier.size(14.dp)) },
                text = { Text("Queue", fontSize = 12.sp) }
            )
        }

        if (filteredRecords.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "No records in '$selectedTab' tab",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                contentPadding = PaddingValues(vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                items(filteredRecords, key = { it.idempotencyKey }) { record ->
                    LedgerCard(
                        record = record,
                        onClaimClick = {
                            claimingRecord = record
                            orderIdInput = ""
                        }
                    )
                }
            }
        }
    }

    // ── Manual Assignment Modal Dialog ───────────────────────────────────────
    claimingRecord?.let { record ->
        AlertDialog(
            onDismissRequest = { claimingRecord = null },
            icon = { Icon(Icons.Default.AssignmentTurnedIn, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
            title = { Text("Claim Orphan Payment", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "Assign ₹${"%.2f".format(record.amount)} from ${record.payerName} to an Order ID or Customer ID to reconcile.",
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OutlinedTextField(
                        value = orderIdInput,
                        onValueChange = { orderIdInput = it },
                        label = { Text("Target Order ID / Reference") },
                        placeholder = { Text("e.g. ORD-98213") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (orderIdInput.isNotBlank()) {
                            onClaimOrphan(record.idempotencyKey, orderIdInput.trim())
                            claimingRecord = null
                        }
                    },
                    enabled = orderIdInput.isNotBlank()
                ) {
                    Text("Assign & Credit")
                }
            },
            dismissButton = {
                TextButton(onClick = { claimingRecord = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun LedgerCard(
    record: AuraPayRecord,
    onClaimClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f), RoundedCornerShape(12.dp))
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "₹${"%.2f".format(record.amount)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF10B981)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = record.payerName,
                        fontWeight = FontWeight.SemiBold,
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
                Text(
                    text = "${record.sourceApp.substringAfterLast('.')} • ${SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(Date(record.timestamp))}",
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
                text = "Key: ${record.idempotencyKey}",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
            )

            // Manual Claim button for Orphan payments
            if (record.status == PaymentStatus.UNMATCHED_ORPHAN) {
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onClaimClick,
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF59E0B)),
                    modifier = Modifier.align(Alignment.End),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Text("Claim / Assign to Order", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}
