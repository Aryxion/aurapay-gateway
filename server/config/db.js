const mongoose = require('mongoose');

let currentMongoUri = process.env.MONGO_URI || 'mongodb://127.0.0.1:27017/aurapay';
let isConnected = false;

/**
 * Connect to MongoDB or switch to a new URI at runtime.
 * @param {string} [newUri] Optional new MongoDB connection string.
 * @returns {Promise<{success: boolean, uri: string, error?: string}>}
 */
async function connectDB(newUri) {
  const targetUri = newUri || currentMongoUri;
  try {
    if (mongoose.connection.readyState !== 0) {
      await mongoose.disconnect();
    }

    await mongoose.connect(targetUri, {
      serverSelectionTimeoutMS: 5000,
      connectTimeoutMS: 5000,
    });

    currentMongoUri = targetUri;
    isConnected = true;
    console.log(`[AuraPay DB] Connected successfully to: ${targetUri.replace(/:\/\/.*@/, '://<credentials>@')}`);
    return { success: true, uri: currentMongoUri };
  } catch (err) {
    isConnected = false;
    console.error(`[AuraPay DB] Connection error for ${targetUri}:`, err.message);
    return { success: false, uri: targetUri, error: err.message };
  }
}

function getDbStatus() {
  const states = ['Disconnected', 'Connected', 'Connecting', 'Disconnecting'];
  const stateIndex = mongoose.connection.readyState;
  return {
    state: states[stateIndex] || 'Unknown',
    readyState: stateIndex,
    isConnected: stateIndex === 1,
    uri: currentMongoUri.replace(/:\/\/.*@/, '://<credentials>@')
  };
}

module.exports = {
  connectDB,
  getDbStatus,
  getCurrentUri: () => currentMongoUri
};
