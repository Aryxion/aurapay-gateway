const http = require('http');
const crypto = require('crypto');
const { app } = require('../server');

const PORT = 5555;
let server;

function request(options, postData) {
  return new Promise((resolve, reject) => {
    const req = http.request(options, (res) => {
      let body = '';
      res.on('data', chunk => { body += chunk; });
      res.on('end', () => {
        try {
          resolve({ status: res.statusCode, headers: res.headers, body: JSON.parse(body) });
        } catch (_) {
          resolve({ status: res.statusCode, headers: res.headers, body });
        }
      });
    });
    req.on('error', reject);
    if (postData) {
      req.write(typeof postData === 'string' ? postData : JSON.stringify(postData));
    }
    req.end();
  });
}

async function runE2eTests() {
  console.log('====================================================');
  console.log('🚀 Running AuraPay End-to-End API Integration Tests');
  console.log('====================================================');

  server = app.listen(PORT, '127.0.0.1');

  try {
    // 1. Health Check
    console.log('\n[E2E 1] GET /health');
    const healthRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/health',
      method: 'GET'
    });
    console.log('  Response:', healthRes.status, healthRes.body.service);
    if (healthRes.status !== 200) throw new Error('Health check failed');

    // 2. Simulation Endpoint: "₹1.01 received from AARAV MISHRA at 12:05 am"
    console.log('\n[E2E 2] POST /api/relay/simulate');
    const simRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/relay/simulate',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, {
      rawText: '₹1.01 received from AARAV MISHRA at 12:05 am',
      amount: 1.01,
      payerName: 'AARAV MISHRA',
      notificationTime: '12:05 am'
    });
    console.log('  Simulation Status:', simRes.status);
    console.log('  Formula:', simRes.body.keyFormula);
    console.log('  Idempotency Key:', simRes.body.payload.idempotencyKey);
    console.log('  HMAC Signature:', simRes.body.headers['x-aurapay-signature'].slice(0, 24) + '...');
    if (simRes.status !== 200 || !simRes.body.headers['x-aurapay-signature']) {
      throw new Error('Simulation failed');
    }

    // 3. Webhook Authentication Test - Missing HMAC
    console.log('\n[E2E 3] POST /api/relay/webhook without HMAC header');
    const unauthRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/relay/webhook',
      method: 'POST',
      headers: { 'Content-Type': 'application/json' }
    }, simRes.body.payload);
    console.log('  Status:', unauthRes.status, unauthRes.body.error);
    if (unauthRes.status !== 401) throw new Error('Expected 401 for missing HMAC');

    // 4. Webhook Authentication Test - Invalid HMAC
    console.log('\n[E2E 4] POST /api/relay/webhook with tampered HMAC');
    const tamperedRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/relay/webhook',
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'x-aurapay-signature': '0000000000000000000000000000000000000000000000000000000000000000'
      }
    }, simRes.body.payload);
    console.log('  Status:', tamperedRes.status, tamperedRes.body.error);
    if (tamperedRes.status !== 401) throw new Error('Expected 401 for invalid HMAC');

    // 5. Test Database Ping & Diagnostics
    console.log('\n[E2E 5] GET /api/relay/test-db');
    const dbTestRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/relay/test-db',
      method: 'GET'
    });
    console.log('  DB Diagnostics status:', dbTestRes.status);
    console.log('  DB State:', dbTestRes.body.status || (dbTestRes.body.isConnected ? 'Connected' : 'Disconnected'));

    // 6. Dynamic System Configuration Test
    console.log('\n[E2E 6] GET /api/relay/system-config');
    const configRes = await request({
      hostname: '127.0.0.1',
      port: PORT,
      path: '/api/relay/system-config',
      method: 'GET'
    });
    console.log('  Status:', configRes.status);

    console.log('\n====================================================');
    console.log('🎉 ALL AURAPAY E2E API TESTS PASSED SUCCESSFULLY');
    console.log('====================================================\n');
  } finally {
    server.close();
  }
}

runE2eTests().catch(err => {
  console.error('\n❌ E2E TEST FAILED:', err);
  if (server) server.close();
  process.exit(1);
});
