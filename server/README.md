# AuraPay Relay Engine Backend Server

Production-grade automated ledger reconciliation backend and UPI payment notification relay server built with Node.js, Express, and MongoDB (Mongoose).

---

## Features

- **HMAC-SHA256 Webhook Authentication**: Authenticates incoming payloads from Android relay devices via the `x-aurapay-signature` header.
- **Deterministic SHA-256 Idempotency Key**: Strict deduplication using MongoDB unique indexes on `idempotencyKey`. Prevents duplicate credits during network retries or repeated Android notifications.
- **Atomic Order Reconciliation**: Finds matching `PENDING` orders and updates status to `CONFIRMED` in an atomic CAS operation. Unmatched payments are classified as `UNMATCHED_ORPHAN`.
- **Live Device Telemetry & Health**: Ingests battery level, network type, CPU WakeLock, Wi-Fi Lock, and listener status via `/api/relay/heartbeat`.
- **Dynamic Database & Config Switcher**: Runtime updates to MongoDB connection URI, UPI VPA handle, and merchant name via `/api/relay/system-config` with zero downtime or process restarts.
- **Orphan Ledger & Manual Claim API**: Manage and assign orphan payments to orders/customers via `/api/relay/orphans/claim`.
- **Built-in Diagnostics**: Live MongoDB ping and collection count at `/api/relay/test-db`.

---

## Quick Start

### 1. Install Dependencies
```bash
cd server
npm install
```

### 2. Configure Environment (Optional)
Create `.env` or use defaults:
```env
PORT=5000
MONGO_URI=mongodb://127.0.0.1:27017/aurapay
AURAPAY_WEBHOOK_SECRET=aurapay_secret_shared_key_2026
```

### 3. Run Tests
```bash
npm test
```

### 4. Start Server
```bash
npm start
```

---

## API Endpoints

| Method | Endpoint | Description | Auth |
| :--- | :--- | :--- | :--- |
| `POST` | `/api/relay/webhook` | Ingests payment notification & reconciles order | HMAC-SHA256 |
| `POST` | `/api/relay/heartbeat` | Ingests mobile device health telemetry | Public |
| `GET` | `/api/relay/telemetry` | Aggregates server KPIs and recent payments | Public |
| `GET` | `/api/relay/system-config` | Fetches runtime config and DB status | Public |
| `POST` | `/api/relay/system-config` | Updates DB URI, UPI VPA, and merchant details | Public / Secret |
| `GET` | `/api/relay/orphans` | Lists unmatched orphan payments | Public |
| `POST` | `/api/relay/orphans/claim` | Manually assigns orphan to Order ID | Public |
| `POST` | `/api/orders` | Creates a new PENDING order for testing | Public |
| `GET` | `/api/orders` | Lists all reconciliation orders | Public |
| `GET/POST`| `/api/relay/test-db` | Pings MongoDB and returns latency & stats | Public |
| `POST` | `/api/relay/simulate` | Generates signed simulation payload | Public |
| `GET` | `/health` | Server health and DB state | Public |
