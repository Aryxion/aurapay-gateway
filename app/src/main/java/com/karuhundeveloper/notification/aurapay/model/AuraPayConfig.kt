package com.karuhundeveloper.notification.aurapay.model

data class AuraPayConfig(
    val serverUrl: String = "http://10.0.2.2:5000",
    val webhookSecret: String = "aurapay_secret_shared_key_2026",
    val deviceId: String = "AuraPay-Relay-01",
    val targetPackages: List<String> = listOf(
        "com.google.nbu.paisa.merchant",
        "com.phonepe.app.business",
        "net.one97.paytm",
        "com.bharatpe.app",
        "com.google.android.apps.nbu.paisa.user"
    ),
    val mongoUri: String = "mongodb://127.0.0.1:27017/aurapay",
    val merchantUpiVpa: String = "aurapay.merchant@okaxis",
    val merchantName: String = "AuraPay Merchant Store"
)
