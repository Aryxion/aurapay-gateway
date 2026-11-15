const express = require('express');
const router = express.Router();
const storage = require('../services/storage');

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

    const telemetry = await storage.upsertDeviceTelemetry({
      deviceId,
      batteryLevel: batteryLevel ?? 100,
      isCharging: isCharging ?? false,
      networkType: networkType || 'Wi-Fi',
      cpuWakeLock: cpuWakeLock ?? true,
      wifiLock: wifiLock ?? true,
      listenerActive: listenerActive ?? true,
      batteryOptBypassed: batteryOptBypassed ?? true,
      appVersion: appVersion || '1.0.0'
    });

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
    const summary = await storage.getTelemetrySummary();
    return res.status(200).json({
      success: true,
      ...summary
    });
  } catch (err) {
    console.error('[AuraPay Telemetry] Aggregation error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
