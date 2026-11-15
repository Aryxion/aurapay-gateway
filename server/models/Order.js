const mongoose = require('mongoose');

const OrderSchema = new mongoose.Schema({
  orderId: {
    type: String,
    required: true,
    unique: true,
    index: true,
    trim: true
  },
  amount: {
    type: Number,
    required: true,
    index: true
  },
  customerName: {
    type: String,
    trim: true,
    default: 'Customer'
  },
  status: {
    type: String,
    enum: ['PENDING', 'CONFIRMED', 'EXPIRED', 'CANCELLED'],
    default: 'PENDING',
    index: true
  },
  matchedPaymentKey: {
    type: String,
    default: null
  },
  confirmedAt: {
    type: Date,
    default: null
  },
  createdAt: {
    type: Date,
    default: Date.now,
    index: true
  }
});

module.exports = mongoose.model('Order', OrderSchema);
