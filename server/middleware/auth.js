const crypto = require('crypto');
const mongoose = require('mongoose');
const SystemConfig = require('../models/SystemConfig');

let cachedSecret = process.env.AURAPAY_WEBHOOK_SECRET || 'aurapay_secret_shared_key_2026';

async function getSecret() {
  if (mongoose.connection.readyState === 1) {
    try {
      const config = await SystemConfig.findOne({ configKey: 'global_config' });
      if (config && config.webhookSecret) {
        cachedSecret = config.webhookSecret;
      }
    } catch (_) {}
  }
  return cachedSecret;
}

/**
 * Express middleware to verify HMAC-SHA256 signature in header `x-aurapay-signature`.
 */
async function verifyHmac(req, res, next) {
  const signature = req.headers['x-aurapay-signature'];
  if (!signature) {
    return res.status(401).json({
      success: false,
      error: 'Missing x-aurapay-signature header'
    });
  }

  const secret = await getSecret();
  const rawBody = req.rawBody || JSON.stringify(req.body);
  const expectedSignature = crypto
    .createHmac('sha256', secret)
    .update(rawBody)
    .digest('hex');

  // Constant-time comparison to prevent timing attacks
  const sigBuffer = Buffer.from(signature, 'hex');
  const expectedBuffer = Buffer.from(expectedSignature, 'hex');

  if (sigBuffer.length !== expectedBuffer.length || !crypto.timingSafeEqual(sigBuffer, expectedBuffer)) {
    return res.status(401).json({
      success: false,
      error: 'Invalid HMAC-SHA256 signature',
      hint: 'Ensure mobile client and server share the exact same webhookSecret'
    });
  }

  req.signatureVerified = true;
  next();
}

module.exports = {
  verifyHmac,
  getSecret,
  setCachedSecret: (sec) => { cachedSecret = sec; }
};
