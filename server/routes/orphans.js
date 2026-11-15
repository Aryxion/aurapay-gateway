const express = require('express');
const router = express.Router();
const PaymentEvent = require('../models/PaymentEvent');
const Order = require('../models/Order');

/**
 * GET /api/relay/orphans
 * Returns all unmatched orphan payments awaiting manual assignment.
 */
router.get('/', async (req, res) => {
  try {
    const orphans = await PaymentEvent.find({ status: 'UNMATCHED_ORPHAN' })
      .sort({ captureTimestamp: -1 })
      .lean();

    return res.status(200).json({
      success: true,
      count: orphans.length,
      orphans
    });
  } catch (err) {
    return res.status(500).json({ success: false, error: err.message });
  }
});

/**
 * POST /api/relay/orphans/claim
 * Allows an operator to manually assign an orphan payment to an Order ID or Customer ID.
 */
router.post('/claim', async (req, res) => {
  try {
    const { idempotencyKey, orderId, customerId, claimedBy } = req.body;

    if (!idempotencyKey) {
      return res.status(400).json({ success: false, error: 'Missing idempotencyKey' });
    }

    const event = await PaymentEvent.findOne({ idempotencyKey });
    if (!event) {
      return res.status(404).json({ success: false, error: 'Payment event not found' });
    }

    if (event.status === 'MATCHED_CREDITED') {
      return res.status(400).json({
        success: false,
        error: `Payment already matched to Order ${event.matchedOrderId}`
      });
    }

    const targetOrderId = orderId || customerId || `MANUAL-${Date.now()}`;

    // If an existing order exists, mark it confirmed
    if (orderId) {
      await Order.findOneAndUpdate(
        { orderId },
        {
          $set: {
            status: 'CONFIRMED',
            matchedPaymentKey: idempotencyKey,
            confirmedAt: new Date()
          }
        }
      );
    }

    event.status = 'MATCHED_CREDITED';
    event.matchedOrderId = targetOrderId;
    event.claimedBy = claimedBy || 'Operator (Mobile App)';
    event.claimedAt = new Date();
    await event.save();

    return res.status(200).json({
      success: true,
      message: `Orphan successfully assigned to ${targetOrderId}`,
      event
    });
  } catch (err) {
    console.error('[AuraPay Orphans] Claim error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
