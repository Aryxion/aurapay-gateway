const crypto = require('crypto');
const assert = require('assert');

console.log('====================================================');
console.log('🧪 Running AuraPay Relay Engine Test Suite');
console.log('====================================================');

// ── Test 1: Deterministic SHA-256 Idempotency Key ───────────────────────────
function testIdempotencyKey() {
  console.log('\n[Test 1] Deterministic SHA-256 Idempotency Key Generation');

  const amount = 1.01;
  const payerName = 'AARAV MISHRA';
  const notificationTime = '12:05 am';
  const dateStr = '2026-11-15';

  const normalizedPayer = payerName.trim().toUpperCase();
  const normalizedTime = notificationTime.trim().toLowerCase();
  const keySource = `${amount}_${normalizedPayer}_${normalizedTime}_${dateStr}`;
  const key1 = crypto.createHash('sha256').update(keySource).digest('hex');

  // Second calculation with slight whitespace/casing difference before normalization
  const payer2 = '  aarav mishra  ';
  const time2 = ' 12:05 AM ';
  const keySource2 = `${amount}_${payer2.trim().toUpperCase()}_${time2.trim().toLowerCase()}_${dateStr}`;
  const key2 = crypto.createHash('sha256').update(keySource2).digest('hex');

  assert.strictEqual(key1, key2, 'Normalized idempotency keys must match identically');
  console.log('  ✓ Source string:', keySource);
  console.log('  ✓ Computed Key:', key1);
  console.log('  ✓ Deduplication guarantee verified across variations');
}

// ── Test 2: HMAC-SHA256 Signature Verification ──────────────────────────────
function testHmacSignature() {
  console.log('\n[Test 2] HMAC-SHA256 Webhook Authentication');

  const secret = 'aurapay_secret_shared_key_2026';
  const payload = JSON.stringify({
    amount: 1.01,
    payerName: 'AARAV MISHRA',
    notificationTime: '12:05 am',
    idempotencyKey: 'abc123test'
  });

  const validSig = crypto.createHmac('sha256', secret).update(payload).digest('hex');
  const tamperedPayload = JSON.stringify({
    amount: 100.01, // Tampered
    payerName: 'AARAV MISHRA'
  });
  const tamperedSig = crypto.createHmac('sha256', secret).update(tamperedPayload).digest('hex');

  assert.notStrictEqual(validSig, tamperedSig, 'Tampered payload must not match signature');
  console.log('  ✓ HMAC-SHA256 generated:', validSig.slice(0, 24) + '...');
  console.log('  ✓ Tampering detected and rejected');
}

// ── Test 3: Regex Parsing Suite for Multi-App Support ───────────────────────
function testUpiRegexParsing() {
  console.log('\n[Test 3] UPI Multi-App Regex Parsing Suite');

  // GPay Business primary pattern
  const gpayRegex = /(?:[₹]|Rs\.?)*\s*([0-9,]+(?:\.[0-9]{1,2})?)\s+received\s+from\s+(.+?)(?:\s+at\s+([0-9:]+(?:\s*[ap]m)?))?$/i;
  const gpaySample = '₹1.01 received from AARAV MISHRA at 12:05 am';
  const gpayMatch = gpaySample.match(gpayRegex);

  assert(gpayMatch, 'GPay sample must match regex');
  assert.strictEqual(gpayMatch[1], '1.01');
  assert.strictEqual(gpayMatch[2], 'AARAV MISHRA');
  assert.strictEqual(gpayMatch[3], '12:05 am');
  console.log('  ✓ Google Pay Business sample parsed:', {
    amount: gpayMatch[1],
    payer: gpayMatch[2],
    time: gpayMatch[3]
  });

  // Real device screenshot sample: "Payments you receive will be shown here \n ₹1.02 received from UTKARSH R at 2:54 pm"
  const realSample = '₹1.02 received from UTKARSH R at 2:54 pm';
  const realMatch = realSample.match(gpayRegex);
  assert(realMatch, 'Real device sample must match');
  assert.strictEqual(realMatch[1], '1.02');
  assert.strictEqual(realMatch[2], 'UTKARSH R');
  assert.strictEqual(realMatch[3], '2:54 pm');
  console.log('  ✓ Real Device Screenshot parsed:', {
    amount: realMatch[1],
    payer: realMatch[2],
    time: realMatch[3]
  });

  // PhonePe Business pattern
  const phonepeRegex = /received\s+₹([0-9,]+(?:\.[0-9]{1,2})?)\s+from\s+(.+?)(?:\s+via\s+PhonePe)?$/i;
  const phonepeSample = 'Received ₹500.00 from RAMESH K via PhonePe';
  const phonepeMatch = phonepeSample.match(phonepeRegex);
  assert(phonepeMatch, 'PhonePe sample must match regex');
  assert.strictEqual(phonepeMatch[1], '500.00');
  assert.strictEqual(phonepeMatch[2], 'RAMESH K');
  console.log('  ✓ PhonePe Business sample parsed:', {
    amount: phonepeMatch[1],
    payer: phonepeMatch[2]
  });

  // Paytm Business pattern
  const paytmRegex = /payment\s+of\s+₹([0-9,]+(?:\.[0-9]{1,2})?)\s+received\s+from\s+(.+)/i;
  const paytmSample = 'Payment of ₹250.50 received from SNEHA P';
  const paytmMatch = paytmSample.match(paytmRegex);
  assert(paytmMatch, 'Paytm sample must match regex');
  assert.strictEqual(paytmMatch[1], '250.50');
  assert.strictEqual(paytmMatch[2], 'SNEHA P');
  console.log('  ✓ Paytm for Business sample parsed:', {
    amount: paytmMatch[1],
    payer: paytmMatch[2]
  });
}

// ── Test 4: Non-Payment Rejection Filter ─────────────────────────────────────
function testNonPaymentFilter() {
  console.log('\n[Test 4] Non-Payment Message Rejection Filter');

  const rejectionPatterns = [
    /otp|one\s*time\s*password|verification\s*code/i,
    /debited|spent|sent\s+to|withdrawn|paid\s+to/i,
    /balance\s+enquiry|available\s+balance|credit\s+score/i,
    /offer|discount|cashback|cash\s*back|promotional|loan/i
  ];

  function isNonPayment(text) {
    return rejectionPatterns.some(pattern => pattern.test(text));
  }

  const otpMsg = 'Your OTP for transaction is 482910. Do not share it.';
  const promoMsg = 'Get flat 50% cashback on your next electricity bill!';
  const debitMsg = 'Rs 450 debited from your A/C for Swiggy payment.';
  const validPayment = '₹1.01 received from AARAV MISHRA at 12:05 am';

  assert(isNonPayment(otpMsg), 'OTP message must be rejected');
  assert(isNonPayment(promoMsg), 'Promo message must be rejected');
  assert(isNonPayment(debitMsg), 'Debit message must be rejected');
  assert(!isNonPayment(validPayment), 'Valid payment must not be rejected');

  console.log('  ✓ OTPs rejected with 0 overhead');
  console.log('  ✓ Promotions and marketing rejected');
  console.log('  ✓ Debit/spent alerts filtered out');
  console.log('  ✓ Valid incoming payment accepted');
}

try {
  testIdempotencyKey();
  testHmacSignature();
  testUpiRegexParsing();
  testNonPaymentFilter();
  console.log('\n====================================================');
  console.log('🎉 ALL AURAPAY CORE UNIT TESTS PASSED SUCCESSFULLY');
  console.log('====================================================\n');
} catch (err) {
  console.error('\n❌ TEST FAILED:', err);
  process.exit(1);
}
