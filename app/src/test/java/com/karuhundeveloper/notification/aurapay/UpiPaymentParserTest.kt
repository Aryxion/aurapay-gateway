package com.karuhundeveloper.notification.aurapay

import com.karuhundeveloper.notification.aurapay.parser.UpiPaymentParser
import org.junit.Assert.*
import org.junit.Test

class UpiPaymentParserTest {

    @Test
    fun parseGooglePayBusinessExactSample() {
        val sample = "₹1.01 received from AARAV MISHRA at 12:05 am"
        val parsed = UpiPaymentParser.parse("", sample, "com.google.nbu.paisa.merchant")

        assertNotNull("Payment should be parsed successfully", parsed)
        assertEquals(1.01, parsed!!.amount, 0.001)
        assertEquals("AARAV MISHRA", parsed.payerName)
        assertEquals("12:05 am", parsed.notificationTime)
        assertEquals("com.google.nbu.paisa.merchant", parsed.sourceApp)
    }

    @Test
    fun parsePhonePeBusiness() {
        val sample = "Received ₹500.00 from RAMESH K via PhonePe"
        val parsed = UpiPaymentParser.parse("", sample, "com.phonepe.app.business")

        assertNotNull(parsed)
        assertEquals(500.00, parsed!!.amount, 0.001)
        assertEquals("RAMESH K", parsed.payerName)
    }

    @Test
    fun parsePaytmBusiness() {
        val sample = "Payment of ₹250.50 received from SNEHA P"
        val parsed = UpiPaymentParser.parse("", sample, "net.one97.paytm")

        assertNotNull(parsed)
        assertEquals(250.50, parsed!!.amount, 0.001)
        assertEquals("SNEHA P", parsed.payerName)
    }

    @Test
    fun parseBharatPe() {
        val sample = "Payment of ₹1,200.00 from VIKRAM S"
        val parsed = UpiPaymentParser.parse("", sample, "com.bharatpe.app")

        assertNotNull(parsed)
        assertEquals(1200.00, parsed!!.amount, 0.001)
        assertEquals("VIKRAM S", parsed.payerName)
    }

    @Test
    fun rejectNonPaymentOtpAndPromotions() {
        val otpSample = "Your OTP for login is 948210. Valid for 10 mins."
        assertNull(UpiPaymentParser.parse("Google Pay", otpSample, "com.google.nbu.paisa.merchant"))

        val promoSample = "Get 20% cashback on your next DTH recharge!"
        assertNull(UpiPaymentParser.parse("", promoSample, "com.phonepe.app.business"))

        val debitSample = "Rs 350.00 debited from A/C XX1234 for Uber trip."
        assertNull(UpiPaymentParser.parse("", debitSample, "com.google.android.apps.nbu.paisa.user"))

        val balanceSample = "Your available account balance is Rs 12,450.00"
        assertNull(UpiPaymentParser.parse("", balanceSample, "net.one97.paytm"))
    }
}
