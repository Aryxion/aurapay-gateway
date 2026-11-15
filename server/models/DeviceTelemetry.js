const mongoose = require('mongoose');

const DeviceTelemetrySchema = new mongoose.Schema({
  deviceId: {
    type: String,
    required: true,
    unique: true,
    index: true
  },
  batteryLevel: {
    type: Number,
    default: 100
  },
  isCharging: {
    type: Boolean,
    default: false
  },
  networkType: {
    type: String,
    default: 'Wi-Fi'
  },
  cpuWakeLock: {
    type: Boolean,
    default: true
  },
  wifiLock: {
    type: Boolean,
    default: true
  },
  listenerActive: {
    type: Boolean,
    default: true
  },
  batteryOptBypassed: {
    type: Boolean,
    default: true
  },
  appVersion: {
    type: String,
    default: '1.0.0'
  },
  lastSeen: {
    type: Date,
    default: Date.now,
    index: true
  }
});

module.exports = mongoose.model('DeviceTelemetry', DeviceTelemetrySchema);
