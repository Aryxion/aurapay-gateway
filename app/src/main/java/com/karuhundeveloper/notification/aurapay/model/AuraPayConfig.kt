package com.karuhundeveloper.notification.aurapay.model

data class AuraPayConfig(
    val serverUrl: String = "http://10.0.2.2:5000",
    val webhookSecret: String = "aurapay_secret_shared_key_2026",
    val deviceId: String = "AuraPay-Relay-01",
    val targetPackages: List<String> = listOf(
        "com.google.android.apps.nbu.paisa.merchant",
        "com.google.nbu.paisa.merchant",
        "com.google.android.apps.nbu.paisa.user",
        "com.phonepe.app.business",
        "com.phonepe.app",
        "com.paytm.business",
        "net.one97.paytm",
        "com.bharatpe.app",
        "com.bharatpe.merchant",
        "in.org.npci.upiapp"
    ),
    val mongoUri: String = "mongodb://127.0.0.1:27017/aurapay",
    val merchantUpiVpa: String = "7891262336@okbizaxis",
    val merchantName: String = "MAHENDRA KIRANA STORE"
)
