# E-Cart Verification & Reliability Pipelines Runbook

This runbook documents the environment setup and exact commands to execute each of the three core automated verification pipelines in **E-Cart**.

---

## 1. Environment Prerequisites

Before running any pipeline, ensure the Java SDK 21 environment and your Stripe Test API secret key are configured in your PowerShell session:

```powershell
$env:JAVA_HOME = "C:\Program Files\Eclipse Adoptium\jdk-21.0.6.7-hotspot"
$env:STRIPE_SECRET_KEY = "sk_test_51TN8Zc3cJNxh7VViO6WOyPpteoQbbfiW3n3ZD2qwvmxCL30PWxv5KMg874GlQVBAnc6tXab6cO6292CZI363Rc9Q00oGTIpEDQ"
Set-Location "E:\Project\E Cart\backend"
```

> [!NOTE]
> All automated tests execute against the dedicated PostgreSQL test database: `ecommerce_test` (port 5432). This keeps your production data (`ecommerce`) clean and isolated.

---

## 2. Pipeline Execution Commands

### Pipeline 1: Flash-Sale Performance & Overselling Prevention
Proves that 10 units cannot be oversold under concurrent traffic. Demonstrates the contrast between the naive read-modify-write anti-pattern (which oversells) and the atomic conditional SQL update (which guarantees exact stock conservation).

```powershell
.\mvnw.cmd test -Dtest=FlashSaleConcurrencyTest
```

**Key Invariant Proven:**
- Naive Approach: 50 buyers $\rightarrow$ stock goes negative (oversold).
- Atomic Conditional Update: 50 buyers $\rightarrow$ exactly 10 succeed, 40 rejected, ending stock is **0**.

---

### Pipeline 2: Redis + PostgreSQL Coordinated Stock Gate
Validates the in-memory Redis gate (`RedisStockGateService`). Proves that excess requests are rejected in RAM before touching PostgreSQL connection pools, and that failed checkouts execute an in-memory compensating rollback.

```powershell
.\mvnw.cmd test -Dtest=RedisStockGateTest
```

**Key Invariant Proven:**
- Hot product stock primed in Redis (RAM).
- 5 reservations succeed in RAM; the 6th attempt is rejected by Redis in $O(1)$ time with **0 database queries**.
- Rollback compensation restores the counter immediately.

---

### Pipeline 3: Stripe Reliability Pipeline (Idempotency, Webhooks & Saga)
Verifies payment resilience under real-world network edge cases: duplicate submissions, asynchronous webhook delivery, and distributed compensating rollbacks.

```powershell
.\mvnw.cmd test -Dtest=StripeReliabilityPipelineTest
```

**Key Invariants Proven:**
1. **Idempotency Guarantee:** Two requests with the same `IdempotencyKey` return identical `PaymentIntent` IDs without double-charging the customer.
2. **Asynchronous Webhook Compensation:** When Stripe emits `payment_intent.payment_failed` to `POST /api/webhooks/stripe`, the system automatically increments stock back in PostgreSQL and Redis and transitions the order status to `Payment Failed`.

---

## 3. Large-Scale Stress Simulation (Optional)

To run the full 2,000-customer / 1,000-stock concurrent flash sale benchmark with random payment declines and stock exhaustion:

```powershell
.\mvnw.cmd test -Dtest=BbdFlashSaleSimulationTest
```

*See [flash-sale-and-saga-benchmark.md](file:///e:/Project/E%20Cart/docs/flash-sale-and-saga-benchmark.md) for full benchmark metrics and dashboard screenshots.*

