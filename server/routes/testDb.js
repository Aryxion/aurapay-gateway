const express = require('express');
const router = express.Router();
const mongoose = require('mongoose');
const { getDbStatus, getCurrentUri } = require('../config/db');

/**
 * POST /api/relay/test-db and GET /api/relay/test-db
 * Tests MongoDB connectivity, ping latency, and collection stats.
 */
async function handleTestDb(req, res) {
  const startTime = Date.now();
  const dbStatus = getDbStatus();

  if (!dbStatus.isConnected || mongoose.connection.readyState !== 1) {
    return res.status(503).json({
      success: false,
      isConnected: false,
      status: dbStatus.state,
      uri: dbStatus.uri,
      error: 'Database not connected. Please ensure MongoDB is running or update mongoUri in configuration.'
    });
  }

  try {
    const admin = mongoose.connection.db.admin();
    const pingResult = await admin.ping();
    const latencyMs = Date.now() - startTime;

    const collections = await mongoose.connection.db.listCollections().toArray();
    const collectionNames = collections.map(c => c.name);

    return res.status(200).json({
      success: true,
      isConnected: true,
      latencyMs,
      ping: pingResult,
      uri: dbStatus.uri,
      collections: collectionNames,
      databaseName: mongoose.connection.db.databaseName,
      message: `MongoDB is healthy (${latencyMs}ms ping)`
    });
  } catch (err) {
    return res.status(500).json({
      success: false,
      isConnected: false,
      error: err.message,
      uri: dbStatus.uri
    });
  }
}

router.get('/', handleTestDb);
router.post('/', handleTestDb);

module.exports = router;
