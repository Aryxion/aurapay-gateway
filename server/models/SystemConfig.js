const mongoose = require('mongoose');

const SystemConfigSchema = new mongoose.Schema({
  configKey: {
    type: String,
    required: true,
    unique: true,
    default: 'global_config'
  },
  mongoUri: {
    type: String,
    default: 'mongodb://127.0.0.1:27017/aurapay'
  },
  merchantUpiVpa: {
    type: String,
    default: '7891262336@okbizaxis'
  },
  merchantName: {
    type: String,
    default: 'MAHENDRA KIRANA STORE'
  },
  webhookSecret: {
    type: String,
    default: 'aurapay_secret_shared_key_2026'
  },
  targetPackages: {
    type: [String],
    default: [
      'com.google.nbu.paisa.merchant',
      'com.phonepe.app.business',
      'net.one97.paytm',
      'com.bharatpe.app',
      'com.google.android.apps.nbu.paisa.user'
    ]
  },
  updatedAt: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model('SystemConfig', SystemConfigSchema);
