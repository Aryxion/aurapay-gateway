const express = require('express');
const router = express.Router();
const storage = require('../services/storage');
const { verifyHmac } = require('../middleware/auth');

/**
 * POST /api/relay/webhook
 * Ingests a captured payment notification from the Android relay engine.
 * Authenticated via HMAC-SHA256 signature in `x-aurapay-signature`.
 */
router.post('/', verifyHmac, async (req, res) => {
  try {
    const {
      idempotencyKey,
      amount,
      payerName,
      notificationTime,
      captureTimestamp,
      sourceApp,
      rawText,
      deviceId
    } = req.body;

    if (!idempotencyKey || amount === undefined || !payerName) {
      return res.status(400).json({
        success: false,
        error: 'Missing required payment fields (idempotencyKey, amount, payerName)'
      });
    }

    const numericAmount = parseFloat(amount);
    if (isNaN(numericAmount) || numericAmount <= 0) {
      return res.status(400).json({
        success: false,
        error: 'Invalid payment amount'
      });
    }

    // ── Deduplication Check ──────────────────────────────────────────────────
    const existing = await storage.findPaymentEvent(idempotencyKey);
    if (existing) {
      return res.status(200).json({
        success: true,
        status: 'DUPLICATE_IGNORED',
        message: 'Duplicate payment event ignored (atomic idempotency)',
        idempotencyKey: existing.idempotencyKey,
        originalStatus: existing.status,
        matchedOrderId: existing.matchedOrderId
      });
    }

    // ── Atomic Order Matching ────────────────────────────────────────────────
    const pendingOrder = await storage.matchAndConfirmPendingOrder(numericAmount, idempotencyKey);

    let paymentStatus = 'UNMATCHED_ORPHAN';
    let matchedOrderId = null;

    if (pendingOrder) {
      paymentStatus = 'MATCHED_CREDITED';
      matchedOrderId = pendingOrder.orderId;
    }

    // ── Record Payment Event ─────────────────────────────────────────────────
    let event;
    try {
      event = await storage.createPaymentEvent({
        idempotencyKey,
        amount: numericAmount,
        payerName: (payerName || '').trim(),
        notificationTime: notificationTime || '',
        captureTimestamp: captureTimestamp ? new Date(captureTimestamp) : new Date(),
        sourceApp: sourceApp || 'com.google.nbu.paisa.merchant',
        rawText: rawText || '',
        deviceId: deviceId || 'AuraPay-Relay-Default',
        status: paymentStatus,
        matchedOrderId,
        signatureVerified: true
      });
    } catch (dbErr) {
      // Catch duplicate key race condition
      if (dbErr.code === 11000) {
        return res.status(200).json({
          success: true,
          status: 'DUPLICATE_IGNORED',
          message: 'Duplicate payment event ignored via race condition guard',
          idempotencyKey
        });
      }
      throw dbErr;
    }

    return res.status(200).json({
      success: true,
      status: paymentStatus,
      matchedOrderId,
      amount: numericAmount,
      payerName: event.payerName,
      idempotencyKey: event.idempotencyKey,
      message: paymentStatus === 'MATCHED_CREDITED'
        ? `Matched and credited to Order ${matchedOrderId}`
        : 'Payment recorded as Unmatched Orphan'
    });
  } catch (err) {
    console.error('[AuraPay Webhook] Processing error:', err);
    return res.status(500).json({
      success: false,
      error: 'Internal server error processing payment webhook',
      details: err.message
    });
  }
});

module.exports = router;
