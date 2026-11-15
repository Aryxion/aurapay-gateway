const mongoose = require('mongoose');
const Order = require('../models/Order');
const PaymentEvent = require('../models/PaymentEvent');
const DeviceTelemetry = require('../models/DeviceTelemetry');

// In-memory fallback stores (used when MongoDB is not connected)
const inMemoryOrders = new Map();
const inMemoryEvents = new Map();
const inMemoryDevices = new Map();

function isDbConnected() {
  return mongoose.connection.readyState === 1;
}

// ── Payment Events ───────────────────────────────────────────────────────────

async function findPaymentEvent(idempotencyKey) {
  if (isDbConnected()) {
    try {
      return await PaymentEvent.findOne({ idempotencyKey });
    } catch (_) {}
  }
  return inMemoryEvents.get(idempotencyKey) || null;
}

async function createPaymentEvent(data) {
  if (isDbConnected()) {
    try {
      return await PaymentEvent.create(data);
    } catch (err) {
      if (err.code === 11000) throw err;
      // Fallback if DB disconnected mid-operation
    }
  }

  // Check unique key in memory
  if (inMemoryEvents.has(data.idempotencyKey)) {
    const duplicateErr = new Error('Duplicate key');
    duplicateErr.code = 11000;
    throw duplicateErr;
  }

  const event = {
    ...data,
    _id: `mem_ev_${Date.now()}_${Math.random().toString(36).substr(2, 6)}`,
    captureTimestamp: data.captureTimestamp || new Date(),
    createdAt: new Date()
  };
  inMemoryEvents.set(data.idempotencyKey, event);
  return event;
}

// ── Atomic Order Matching ────────────────────────────────────────────────────

async function matchAndConfirmPendingOrder(amount, idempotencyKey) {
  if (isDbConnected()) {
    try {
      return await Order.findOneAndUpdate(
        { amount, status: 'PENDING' },
        {
          $set: {
            status: 'CONFIRMED',
            matchedPaymentKey: idempotencyKey,
            confirmedAt: new Date()
          }
        },
        { sort: { createdAt: 1 }, new: true }
      );
    } catch (_) {}
  }

  // In-memory atomic matching
  for (const [orderId, order] of inMemoryOrders.entries()) {
    if (order.status === 'PENDING' && Math.abs(order.amount - amount) < 0.001) {
      order.status = 'CONFIRMED';
      order.matchedPaymentKey = idempotencyKey;
      order.confirmedAt = new Date();
      inMemoryOrders.set(orderId, order);
      return order;
    }
  }
  return null;
}

// ── Orders ───────────────────────────────────────────────────────────────────

async function createOrder(data) {
  if (isDbConnected()) {
    try {
      return await Order.create(data);
    } catch (err) {
      if (err.code === 11000) throw err;
    }
  }

  if (inMemoryOrders.has(data.orderId)) {
    const duplicateErr = new Error('Order ID already exists');
    duplicateErr.code = 11000;
    throw duplicateErr;
  }

  const order = {
    ...data,
    _id: `mem_ord_${Date.now()}`,
    status: data.status || 'PENDING',
    customerName: data.customerName || 'Customer',
    createdAt: new Date()
  };
  inMemoryOrders.set(data.orderId, order);
  return order;
}

async function listOrders(statusFilter) {
  if (isDbConnected()) {
    try {
      const filter = statusFilter ? { status: statusFilter } : {};
      return await Order.find(filter).sort({ createdAt: -1 }).limit(50).lean();
    } catch (_) {}
  }

  let list = Array.from(inMemoryOrders.values());
  if (statusFilter) {
    list = list.filter(o => o.status === statusFilter);
  }
  return list.sort((a, b) => b.createdAt - a.createdAt);
}

// ── Orphans & Manual Claim ───────────────────────────────────────────────────

async function listOrphans() {
  if (isDbConnected()) {
    try {
      return await PaymentEvent.find({ status: 'UNMATCHED_ORPHAN' })
        .sort({ captureTimestamp: -1 })
        .lean();
    } catch (_) {}
  }

  return Array.from(inMemoryEvents.values())
    .filter(e => e.status === 'UNMATCHED_ORPHAN')
    .sort((a, b) => b.captureTimestamp - a.captureTimestamp);
}

async function claimOrphan(idempotencyKey, targetOrderId, claimedBy) {
  if (isDbConnected()) {
    try {
      const event = await PaymentEvent.findOne({ idempotencyKey });
      if (event) {
        if (targetOrderId) {
          await Order.findOneAndUpdate(
            { orderId: targetOrderId },
            {
              $set: {
                status: 'CONFIRMED',
                matchedPaymentKey: idempotencyKey,
                confirmedAt: new Date()
              }
            }
          );
        }
        event.status = 'MATCHED_CREDITED';
        event.matchedOrderId = targetOrderId;
        event.claimedBy = claimedBy || 'Operator (Mobile App)';
        event.claimedAt = new Date();
        await event.save();
        return event;
      }
    } catch (_) {}
  }

  // In-memory claim
  const event = inMemoryEvents.get(idempotencyKey);
  if (!event) return null;

  if (targetOrderId && inMemoryOrders.has(targetOrderId)) {
    const ord = inMemoryOrders.get(targetOrderId);
    ord.status = 'CONFIRMED';
    ord.matchedPaymentKey = idempotencyKey;
    ord.confirmedAt = new Date();
    inMemoryOrders.set(targetOrderId, ord);
  }

  event.status = 'MATCHED_CREDITED';
  event.matchedOrderId = targetOrderId;
  event.claimedBy = claimedBy || 'Operator (Mobile App)';
  event.claimedAt = new Date();
  inMemoryEvents.set(idempotencyKey, event);
  return event;
}

// ── Device Telemetry ─────────────────────────────────────────────────────────

async function upsertDeviceTelemetry(data) {
  if (isDbConnected()) {
    try {
      return await DeviceTelemetry.findOneAndUpdate(
        { deviceId: data.deviceId },
        { $set: { ...data, lastSeen: new Date() } },
        { upsert: true, new: true }
      );
    } catch (_) {}
  }

  const record = { ...data, lastSeen: new Date() };
  inMemoryDevices.set(data.deviceId, record);
  return record;
}

// ── Telemetry Aggregates ─────────────────────────────────────────────────────

async function getTelemetrySummary() {
  const startOfToday = new Date();
  startOfToday.setHours(0, 0, 0, 0);

  if (isDbConnected()) {
    try {
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

      const allTimeAgg = await PaymentEvent.aggregate([
        {
          $match: {
            status: { $in: ['MATCHED_CREDITED', 'UNMATCHED_ORPHAN'] }
          }
        },
        { $group: { _id: null, total: { $sum: '$amount' } } }
      ]);
      const allTimeAmount = allTimeAgg.length > 0 ? allTimeAgg[0].total : 0;

      const totalForwarded = await PaymentEvent.countDocuments();
      const matchedOrdersCount = await PaymentEvent.countDocuments({ status: 'MATCHED_CREDITED' });
      const unmatchedOrphansCount = await PaymentEvent.countDocuments({ status: 'UNMATCHED_ORPHAN' });
      const activePendingOrdersCount = await Order.countDocuments({ status: 'PENDING' });

      const recentPayments = await PaymentEvent.find().sort({ captureTimestamp: -1 }).limit(15).lean();
      const devices = await DeviceTelemetry.find().sort({ lastSeen: -1 }).limit(5).lean();

      return {
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
      };
    } catch (_) {}
  }

  // In-memory aggregation
  const allEvents = Array.from(inMemoryEvents.values());
  let todayAmount = 0;
  let allTimeAmount = 0;
  let matchedOrdersCount = 0;
  let unmatchedOrphansCount = 0;

  for (const ev of allEvents) {
    if (ev.status === 'MATCHED_CREDITED' || ev.status === 'UNMATCHED_ORPHAN') {
      allTimeAmount += ev.amount;
      if (new Date(ev.captureTimestamp) >= startOfToday) {
        todayAmount += ev.amount;
      }
    }
    if (ev.status === 'MATCHED_CREDITED') matchedOrdersCount++;
    if (ev.status === 'UNMATCHED_ORPHAN') unmatchedOrphansCount++;
  }

  let activePendingOrdersCount = 0;
  for (const ord of inMemoryOrders.values()) {
    if (ord.status === 'PENDING') activePendingOrdersCount++;
  }

  const recentPayments = allEvents
    .sort((a, b) => new Date(b.captureTimestamp) - new Date(a.captureTimestamp))
    .slice(0, 15);

  const devices = Array.from(inMemoryDevices.values())
    .sort((a, b) => new Date(b.lastSeen) - new Date(a.lastSeen))
    .slice(0, 5);

  return {
    kpis: {
      todayAmount: Math.round(todayAmount * 100) / 100,
      allTimeAmount: Math.round(allTimeAmount * 100) / 100,
      totalForwarded: allEvents.length,
      matchedOrdersCount,
      unmatchedOrphansCount,
      activePendingOrdersCount
    },
    devices,
    recentPayments,
    storageMode: isDbConnected() ? 'MongoDB' : 'In-Memory (MongoDB offline)'
  };
}

module.exports = {
  isDbConnected,
  findPaymentEvent,
  createPaymentEvent,
  matchAndConfirmPendingOrder,
  createOrder,
  listOrders,
  listOrphans,
  claimOrphan,
  upsertDeviceTelemetry,
  getTelemetrySummary
};
