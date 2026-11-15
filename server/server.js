require('dotenv').config();
const express = require('express');
const cors = require('cors');
const { connectDB, getDbStatus } = require('./config/db');

const webhookRoutes = require('./routes/webhook');
const telemetryRoutes = require('./routes/telemetry');
const systemConfigRoutes = require('./routes/systemConfig');
const orphansRoutes = require('./routes/orphans');
const ordersRoutes = require('./routes/orders');
const testDbRoutes = require('./routes/testDb');
const simulateRoutes = require('./routes/simulate');

const app = express();
const PORT = process.env.PORT || 5000;

// Enable CORS for mobile app & dashboards
app.use(cors());

// Parse JSON with raw body preservation for HMAC verification
app.use(
  express.json({
    verify: (req, res, buf) => {
      req.rawBody = buf.toString();
    }
  })
);

// ── Health Check ─────────────────────────────────────────────────────────────
app.get('/health', (req, res) => {
  res.status(200).json({
    status: 'ONLINE',
    service: 'AuraPay Relay Engine Backend',
    version: '1.0.0',
    timestamp: new Date().toISOString(),
    db: getDbStatus()
  });
});

// ── Mount API Routes ─────────────────────────────────────────────────────────
app.use('/api/relay/webhook', webhookRoutes);
app.use('/api/relay/telemetry', telemetryRoutes);
app.use('/api/relay/system-config', systemConfigRoutes);
app.use('/api/relay/orphans', orphansRoutes);
app.use('/api/orders', ordersRoutes);
app.use('/api/relay/test-db', testDbRoutes);
app.use('/api/relay/simulate', simulateRoutes);

// ── Error Handler ────────────────────────────────────────────────────────────
app.use((err, req, res, next) => {
  console.error('[AuraPay Server Error]', err);
  res.status(500).json({
    success: false,
    error: 'Internal Server Error',
    message: err.message
  });
});

// ── Server Bootstrap ─────────────────────────────────────────────────────────
async function startServer() {
  console.log('───────────────────────────────────────────────────');
  console.log('🚀 Starting AuraPay Relay Engine Backend Server...');
  console.log('───────────────────────────────────────────────────');

  // Attempt initial database connection (non-fatal if MongoDB not yet up)
  const initialUri = process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/aurapay';
  await connectDB(initialUri);

  const server = app.listen(PORT, '0.0.0.0', () => {
    console.log(`[AuraPay Server] Listening on http://0.0.0.0:${PORT}`);
    console.log(`[AuraPay Server] Webhook Endpoint: http://localhost:${PORT}/api/relay/webhook`);
    console.log(`[AuraPay Server] Telemetry Endpoint: http://localhost:${PORT}/api/relay/telemetry`);
    console.log(`[AuraPay Server] Diagnostics / DB Test: http://localhost:${PORT}/api/relay/test-db`);
    console.log('───────────────────────────────────────────────────');
  });

  return { app, server };
}

if (require.main === module) {
  startServer();
}

module.exports = { app, startServer };
