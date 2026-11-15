const express = require('express');
const router = express.Router();
const crypto = require('crypto');
const { getSecret } = require('../middleware/auth');

/**
 * POST /api/relay/simulate
 * Simulates a payment notification (defaults to: "₹1.01 received from AARAV MISHRA at 12:05 am")
 * and executes a self-dispatch through the webhook handler.
 */
router.post('/', async (req, res) => {
  try {
    const rawText = req.body.rawText || '₹1.01 received from AARAV MISHRA at 12:05 am';
    const amount = req.body.amount || 1.01;
    const payerName = req.body.payerName || 'AARAV MISHRA';
    const notificationTime = req.body.notificationTime || '12:05 am';
    const sourceApp = req.body.sourceApp || 'com.google.nbu.paisa.merchant';
    const deviceId = req.body.deviceId || 'AuraPay-Simulator';

    const todayDate = new Date().toISOString().slice(0, 10);
    const normalizedPayer = payerName.trim().toUpperCase();
    const normalizedTime = notificationTime.trim().toLowerCase();

    // Deterministic SHA-256 Idempotency Key
    const keySource = `${amount}_${normalizedPayer}_${normalizedTime}_${todayDate}`;
    const idempotencyKey = crypto.createHash('sha256').update(keySource).digest('hex');

    const payload = {
      idempotencyKey,
      amount,
      payerName,
      notificationTime,
      captureTimestamp: Date.now(),
      sourceApp,
      rawText,
      deviceId
    };

    const secret = await getSecret();
    const signature = crypto
      .createHmac('sha256', secret)
      .update(JSON.stringify(payload))
      .digest('hex');

    return res.status(200).json({
      success: true,
      message: 'Simulation payload prepared',
      payload,
      headers: {
        'x-aurapay-signature': signature,
        'Content-Type': 'application/json'
      },
      keyFormula: `SHA256(${keySource})`,
      testSample: '₹1.01 received from AARAV MISHRA at 12:05 am'
    });
  } catch (err) {
    return res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
