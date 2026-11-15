package com.karuhundeveloper.notification.aurapay.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karuhundeveloper.notification.aurapay.model.AuraPayConfig

@Composable
fun ConfigScreen(
    config: AuraPayConfig,
    onSaveConfig: (AuraPayConfig) -> Unit,
    onSimulateNotification: (onResult: (String) -> Unit) -> Unit,
    onManualQueueFlush: (onResult: (Int) -> Unit) -> Unit,
    onTestDbConnection: (onResult: (String) -> Unit) -> Unit,
    onUpdateServerConfig: (mongoUri: String, upiVpa: String, merchantName: String, onResult: (String) -> Unit) -> Unit,
    modifier: Modifier = Modifier
) {
    var serverUrl by remember(config) { mutableStateOf(config.serverUrl) }
    var webhookSecret by remember(config) { mutableStateOf(config.webhookSecret) }
    var deviceId by remember(config) { mutableStateOf(config.deviceId) }
    var mongoUri by remember(config) { mutableStateOf(config.mongoUri) }
    var merchantUpiVpa by remember(config) { mutableStateOf(config.merchantUpiVpa) }
    var merchantName by remember(config) { mutableStateOf(config.merchantName) }

    var resultMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Zero-Rebuild System Config",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )

        // ── Settings Card ────────────────────────────────────────────────────
        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = serverUrl,
                    onValueChange = { serverUrl = it },
                    label = { Text("Server Base URL") },
                    placeholder = { Text("http://10.0.2.2:5000") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Cloud, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = webhookSecret,
                    onValueChange = { webhookSecret = it },
                    label = { Text("Webhook Secret Key (HMAC-SHA256)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Key, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = deviceId,
                    onValueChange = { deviceId = it },
                    label = { Text("Device Identifier (deviceId)") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Smartphone, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Divider()

                Text(
                    text = "Dynamic Database Switcher (No Code/APK Rebuild)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.primary
                )

                OutlinedTextField(
                    value = mongoUri,
                    onValueChange = { mongoUri = it },
                    label = { Text("Dynamic MongoDB Connection String") },
                    placeholder = { Text("mongodb://127.0.0.1:27017/aurapay") },
                    singleLine = true,
                    leadingIcon = { Icon(Icons.Default.Storage, contentDescription = null) },
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = merchantUpiVpa,
                        onValueChange = { merchantUpiVpa = it },
                        label = { Text("Merchant UPI VPA") },
                        placeholder = { Text("merchant@okaxis") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    OutlinedTextField(
                        value = merchantName,
                        onValueChange = { merchantName = it },
                        label = { Text("Store Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            val newConfig = config.copy(
                                serverUrl = serverUrl.trim(),
                                webhookSecret = webhookSecret.trim(),
                                deviceId = deviceId.trim(),
                                mongoUri = mongoUri.trim(),
                                merchantUpiVpa = merchantUpiVpa.trim(),
                                merchantName = merchantName.trim()
                            )
                            onSaveConfig(newConfig)
                            resultMessage = "Local configuration saved successfully!"
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save Local Config")
                    }

                    OutlinedButton(
                        onClick = {
                            isLoading = true
                            onUpdateServerConfig(mongoUri.trim(), merchantUpiVpa.trim(), merchantName.trim()) { res ->
                                isLoading = false
                                resultMessage = "Server DB Switch: $res"
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Switch Server DB")
                    }
                }
            }
        }

        // ── Diagnostic Tools Card ────────────────────────────────────────────
        Text(
            text = "Built-in Diagnostic Tools",
            fontSize = 16.sp,
            fontWeight = FontWeight.Bold
        )

        Card(
            shape = RoundedCornerShape(14.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Tool 1: Simulate GPay Notification
                Button(
                    onClick = {
                        isLoading = true
                        onSimulateNotification { res ->
                            isLoading = false
                            resultMessage = "Simulated GPay: $res"
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF10B981)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.PlayArrow, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Simulate GPay (₹1.01 from AARAV MISHRA)", fontWeight = FontWeight.Bold)
                }

                // Tool 2: Test Database Connection
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        onTestDbConnection { res ->
                            isLoading = false
                            resultMessage = "DB Ping Test:\n$res"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Speed, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Test Database Connection (Ping & Stats)")
                }

                // Tool 3: Manual Queue Flush
                OutlinedButton(
                    onClick = {
                        isLoading = true
                        onManualQueueFlush { count ->
                            isLoading = false
                            resultMessage = "Manual Queue Flush: $count payments sent to server"
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Sync, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Manual Queue Flush (Pending Offline Items)")
                }
            }
        }

        if (isLoading) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        resultMessage?.let { msg ->
            Card(
                shape = RoundedCornerShape(10.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = msg,
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = { resultMessage = null }) {
                        Icon(Icons.Default.Close, contentDescription = "Dismiss")
                    }
                }
            }
        }
    }
}
