const express = require('express');
const router = express.Router();
const storage = require('../services/storage');

/**
 * GET /api/relay/orphans
 * Returns all unmatched orphan payments awaiting manual assignment.
 */
router.get('/', async (req, res) => {
  try {
    const orphans = await storage.listOrphans();

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

    const targetOrderId = orderId || customerId || `MANUAL-${Date.now()}`;
    const event = await storage.claimOrphan(idempotencyKey, targetOrderId, claimedBy);

    if (!event) {
      return res.status(404).json({ success: false, error: 'Payment event not found' });
    }

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
