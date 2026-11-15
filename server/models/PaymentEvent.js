const mongoose = require('mongoose');

const PaymentEventSchema = new mongoose.Schema({
  idempotencyKey: {
    type: String,
    required: true,
    unique: true, // Atomic deduplication guarantee
    index: true,
    trim: true
  },
  amount: {
    type: Number,
    required: true,
    index: true
  },
  payerName: {
    type: String,
    required: true,
    trim: true,
    index: true
  },
  notificationTime: {
    type: String,
    default: ''
  },
  captureTimestamp: {
    type: Date,
    default: Date.now,
    index: true
  },
  sourceApp: {
    type: String,
    default: 'com.google.nbu.paisa.merchant'
  },
  rawText: {
    type: String,
    default: ''
  },
  deviceId: {
    type: String,
    default: 'AuraPay-Relay-Default',
    index: true
  },
  status: {
    type: String,
    enum: ['MATCHED_CREDITED', 'UNMATCHED_ORPHAN', 'DUPLICATE_IGNORED', 'LOCAL_QUEUE'],
    default: 'UNMATCHED_ORPHAN',
    index: true
  },
  matchedOrderId: {
    type: String,
    default: null,
    index: true
  },
  claimedBy: {
    type: String,
    default: null
  },
  claimedAt: {
    type: Date,
    default: null
  },
  signatureVerified: {
    type: Boolean,
    default: true
  },
  createdAt: {
    type: Date,
    default: Date.now
  }
});

module.exports = mongoose.model('PaymentEvent', PaymentEventSchema);
