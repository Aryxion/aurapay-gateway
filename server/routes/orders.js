const express = require('express');
const router = express.Router();
const storage = require('../services/storage');

/**
 * POST /api/orders
 * Creates a new pending order for payment reconciliation.
 */
router.post('/', async (req, res) => {
  try {
    const { orderId, amount, customerName } = req.body;

    if (!orderId || amount === undefined) {
      return res.status(400).json({ success: false, error: 'Missing orderId or amount' });
    }

    const order = await storage.createOrder({
      orderId: orderId.trim(),
      amount: parseFloat(amount),
      customerName: customerName ? customerName.trim() : 'Customer',
      status: 'PENDING'
    });

    return res.status(201).json({
      success: true,
      message: 'Pending order created',
      order
    });
  } catch (err) {
    if (err.code === 11000) {
      return res.status(400).json({ success: false, error: 'Order ID already exists' });
    }
    return res.status(500).json({ success: false, error: err.message });
  }
});

/**
 * GET /api/orders
 * Retrieves orders, optionally filtered by status (PENDING, CONFIRMED, etc.).
 */
router.get('/', async (req, res) => {
  try {
    const { status } = req.query;
    const orders = await storage.listOrders(status);

    return res.status(200).json({
      success: true,
      count: orders.length,
      orders
    });
  } catch (err) {
    return res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
