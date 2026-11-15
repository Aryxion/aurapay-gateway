const express = require('express');
const router = express.Router();
const PaymentEvent = require('../models/PaymentEvent');
const Order = require('../models/Order');
const DeviceTelemetry = require('../models/DeviceTelemetry');

/**
 * POST /api/relay/heartbeat
 * Updates live device health telemetry sent periodically from mobile app.
 */
router.post('/heartbeat', async (req, res) => {
  try {
    const {
      deviceId,
      batteryLevel,
      isCharging,
      networkType,
      cpuWakeLock,
      wifiLock,
      listenerActive,
      batteryOptBypassed,
      appVersion
    } = req.body;

    if (!deviceId) {
      return res.status(400).json({ success: false, error: 'Missing deviceId' });
    }

    const telemetry = await DeviceTelemetry.findOneAndUpdate(
      { deviceId },
      {
        $set: {
          batteryLevel: batteryLevel ?? 100,
          isCharging: isCharging ?? false,
          networkType: networkType || 'Wi-Fi',
          cpuWakeLock: cpuWakeLock ?? true,
          wifiLock: wifiLock ?? true,
          listenerActive: listenerActive ?? true,
          batteryOptBypassed: batteryOptBypassed ?? true,
          appVersion: appVersion || '1.0.0',
          lastSeen: new Date()
        }
      },
      { upsert: true, new: true }
    );

    return res.status(200).json({
      success: true,
      deviceId: telemetry.deviceId,
      serverTime: Date.now()
    });
  } catch (err) {
    console.error('[AuraPay Telemetry] Heartbeat error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

/**
 * GET /api/relay/telemetry
 * Aggregates live server KPIs, device health, and recent feed for the mobile dashboard.
 */
router.get('/', async (req, res) => {
  try {
    const startOfToday = new Date();
    startOfToday.setHours(0, 0, 0, 0);

    // Today's received amount (credited + orphans)
    const todayAgg = await PaymentEvent.aggregate([
      {
        $match: {
          captureTimestamp: { $gte: startOfToday },
          status: { $in: ['MATCHED_CREDITED', 'UNMATCHED_ORPHAN'] }
        }
      },
      { $group: { _id: null, total: { $sum: '$amount' } } }
    ]);
    const todayAmount = todayAgg.length > 0 ? todayAgg[0].total : 0;

    // All-time received amount
    const allTimeAgg = await PaymentEvent.aggregate([
      {
        $match: {
          status: { $in: ['MATCHED_CREDITED', 'UNMATCHED_ORPHAN'] }
        }
      },
      { $group: { _id: null, total: { $sum: '$amount' } } }
    ]);
    const allTimeAmount = allTimeAgg.length > 0 ? allTimeAgg[0].total : 0;

    // Counts
    const totalForwarded = await PaymentEvent.countDocuments();
    const matchedOrdersCount = await PaymentEvent.countDocuments({ status: 'MATCHED_CREDITED' });
    const unmatchedOrphansCount = await PaymentEvent.countDocuments({ status: 'UNMATCHED_ORPHAN' });
    const activePendingOrdersCount = await Order.countDocuments({ status: 'PENDING' });

    // Recent 15 payments for live feed
    const recentPayments = await PaymentEvent.find()
      .sort({ captureTimestamp: -1 })
      .limit(15)
      .lean();

    // Active device health records
    const devices = await DeviceTelemetry.find()
      .sort({ lastSeen: -1 })
      .limit(5)
      .lean();

    return res.status(200).json({
      success: true,
      kpis: {
        todayAmount: Math.round(todayAmount * 100) / 100,
        allTimeAmount: Math.round(allTimeAmount * 100) / 100,
        totalForwarded,
        matchedOrdersCount,
        unmatchedOrphansCount,
        activePendingOrdersCount
      },
      devices,
      recentPayments
    });
  } catch (err) {
    // Graceful fallback if MongoDB is currently offline or unreachable
    return res.status(200).json({
      success: true,
      kpis: {
        todayAmount: 0,
        allTimeAmount: 0,
        totalForwarded: 0,
        matchedOrdersCount: 0,
        unmatchedOrphansCount: 0,
        activePendingOrdersCount: 0
      },
      devices: [],
      recentPayments: [],
      dbNotice: 'MongoDB is disconnected or initializing'
    });
  }
});

module.exports = router;
