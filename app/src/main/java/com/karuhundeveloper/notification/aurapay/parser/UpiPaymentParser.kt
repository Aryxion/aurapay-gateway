package com.karuhundeveloper.notification.aurapay.parser

import com.karuhundeveloper.notification.aurapay.model.ParsedPayment
import java.util.regex.Pattern

object UpiPaymentParser {

    // ── Non-Payment Rejection Patterns (OTPs, promos, debits, inquiries) ──────
    private val REJECTION_PATTERNS = listOf(
        Pattern.compile("(?i)\\b(otp|one\\s*time\\s*password|verification\\s*code|security\\s*code)\\b"),
        Pattern.compile("(?i)\\b(debited|spent|sent\\s+to|withdrawn|paid\\s+to|charged)\\b"),
        Pattern.compile("(?i)\\b(balance\\s+enquiry|available\\s+balance|ledger\\s+balance|credit\\s+score)\\b"),
        Pattern.compile("(?i)\\b(cashback|cash\\s*back|discount|promo|promotional|loan|voucher|scratch\\s*card)\\b"),
        Pattern.compile("(?i)\\b(failed|declined|cancelled|reversed|refunded)\\b")
    )

    // ── Primary Pattern: Google Pay for Business ─────────────────────────────
    // Regex: ₹([0-9,]+(?:\.[0-9]{1,2})?)\s+received\s+from\s+(.+?)\s+at\s+([0-9:]+(?:\s*[ap]m)?)
    private val GPAY_BUSINESS_PATTERN = Pattern.compile(
        "(?i)[₹Rs.]*\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+received\\s+from\\s+(.+?)\\s+at\\s+([0-9:]+(?:\\s*[ap]m)?)"
    )

    // ── PhonePe Business: "Received ₹500.00 from RAMESH K via PhonePe" ────────
    private val PHONEPE_PATTERN = Pattern.compile(
        "(?i)received\\s+[₹Rs.]*\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+from\\s+(.+?)(?:\\s+via\\s+PhonePe|\\s+at\\s+.*)?$"
    )

    // ── Paytm for Business: "Payment of ₹250.50 received from SNEHA P" ────────
    private val PAYTM_PATTERN = Pattern.compile(
        "(?i)payment\\s+of\\s+[₹Rs.]*\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+received\\s+from\\s+(.+)"
    )

    // ── BharatPe: "Received ₹100 from ..." or "Payment of ₹100 from ..." ──────
    private val BHARATPE_PATTERN = Pattern.compile(
        "(?i)(?:received|payment\\s+of)\\s+[₹Rs.]*\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+(?:from\\s+)?(.+)"
    )

    // ── Google Pay Personal: "John Doe paid you ₹150" or "Received ₹150 from John" ─
    private val GPAY_PERSONAL_PATTERN = Pattern.compile(
        "(?i)(?:(.+?)\\s+paid\\s+you\\s+[₹Rs.]*\\s*([0-9,]+(?:\\.[0-9]{1,2})?)|received\\s+[₹Rs.]*\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+from\\s+(.+))"
    )

    // ── General UPI Fallback Pattern ─────────────────────────────────────────
    private val GENERIC_UPI_PATTERN = Pattern.compile(
        "(?i)[₹Rs.]*\\s*([0-9,]+(?:\\.[0-9]{1,2})?)\\s+(?:received|credited|deposited)\\s+(?:from\\s+)?(.+)"
    )

    /**
     * Checks if notification text is a non-payment message that should be rejected immediately.
     */
    fun isNonPayment(text: String): Boolean {
        if (text.isBlank()) return true
        for (pattern in REJECTION_PATTERNS) {
            if (pattern.matcher(text).find()) return true
        }
        return false
    }

    /**
     * Parses notification text into a [ParsedPayment] using multi-app patterns.
     * Returns `null` if the text is rejected or does not represent a valid incoming payment.
     */
    fun parse(title: String, text: String, packageName: String): ParsedPayment? {
        val combinedText = if (title.isNotBlank()) "$title $text" else text
        if (isNonPayment(combinedText)) return null

        // 1. Try Google Pay Business
        val gpayMatcher = GPAY_BUSINESS_PATTERN.matcher(combinedText)
        if (gpayMatcher.find()) {
            val amountStr = gpayMatcher.group(1)?.replace(",", "") ?: return null
            val payer = gpayMatcher.group(2)?.trim() ?: "Unknown Payer"
            val time = gpayMatcher.group(3)?.trim() ?: ""
            val amount = amountStr.toDoubleOrNull() ?: return null
            return ParsedPayment(amount, payer, time, packageName, combinedText)
        }

        // 2. Try PhonePe Business
        val phonepeMatcher = PHONEPE_PATTERN.matcher(combinedText)
        if (phonepeMatcher.find()) {
            val amountStr = phonepeMatcher.group(1)?.replace(",", "") ?: return null
            val payer = phonepeMatcher.group(2)?.trim() ?: "Unknown Payer"
            val amount = amountStr.toDoubleOrNull() ?: return null
            return ParsedPayment(amount, payer, "", packageName, combinedText)
        }

        // 3. Try Paytm Business
        val paytmMatcher = PAYTM_PATTERN.matcher(combinedText)
        if (paytmMatcher.find()) {
            val amountStr = paytmMatcher.group(1)?.replace(",", "") ?: return null
            val payer = paytmMatcher.group(2)?.trim() ?: "Unknown Payer"
            val amount = amountStr.toDoubleOrNull() ?: return null
            return ParsedPayment(amount, payer, "", packageName, combinedText)
        }

        // 4. Try BharatPe
        val bharatpeMatcher = BHARATPE_PATTERN.matcher(combinedText)
        if (bharatpeMatcher.find()) {
            val amountStr = bharatpeMatcher.group(1)?.replace(",", "") ?: return null
            val payer = bharatpeMatcher.group(2)?.trim() ?: "Unknown Payer"
            val amount = amountStr.toDoubleOrNull() ?: return null
            return ParsedPayment(amount, payer, "", packageName, combinedText)
        }

        // 5. Try Google Pay Personal
        val gpayPersonalMatcher = GPAY_PERSONAL_PATTERN.matcher(combinedText)
        if (gpayPersonalMatcher.find()) {
            val amountStr = (gpayPersonalMatcher.group(2) ?: gpayPersonalMatcher.group(3))?.replace(",", "")
            val payer = (gpayPersonalMatcher.group(1) ?: gpayPersonalMatcher.group(4))?.trim() ?: "Unknown Payer"
            val amount = amountStr?.toDoubleOrNull() ?: return null
            return ParsedPayment(amount, payer, "", packageName, combinedText)
        }

        // 6. Generic Fallback
        val genericMatcher = GENERIC_UPI_PATTERN.matcher(combinedText)
        if (genericMatcher.find()) {
            val amountStr = genericMatcher.group(1)?.replace(",", "") ?: return null
            val payer = genericMatcher.group(2)?.trim() ?: "Unknown Payer"
            val amount = amountStr.toDoubleOrNull() ?: return null
            return ParsedPayment(amount, payer, "", packageName, combinedText)
        }

        return null
    }
}
