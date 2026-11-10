# E-Cart

**An e-commerce backend built around one hard question:
what happens when 2,000 people try to buy 1,000 units at the same moment, and the payment gateway fails halfway through?**

Repository: <https://github.com/RohitGoswami2023/E-Cart>
Dataset: https://huggingface.co/datasets/goswamiROHIT/e-cart-dataset
---

## Table of Contents

1. [Why this project exists](#1-why-this-project-exists)
2. [What is in the repository](#2-what-is-in-the-repository)
3. [Architecture at a glance](#3-architecture-at-a-glance)
4. [Tech stack and why I chose each piece](#4-tech-stack-and-why-i-chose-each-piece)
5. [The domain model](#5-the-domain-model)
6. [How checkout works, step by step](#6-how-checkout-works-step-by-step)
7. [Preventing oversell](#7-preventing-oversell)
8. [The Redis stock gate](#8-the-redis-stock-gate)
9. [Payments: Stripe, idempotency and the Saga](#9-payments-stripe-idempotency-and-the-saga)
10. [Webhooks](#10-webhooks)
11. [The order lifecycle](#11-the-order-lifecycle)
12. [Security model](#12-security-model)
13. [REST API reference](#13-rest-api-reference)
14. [Database design](#14-database-design)
15. [Testing strategy](#15-testing-strategy)
16. [Benchmarks and what they do and do not prove](#16-benchmarks-and-what-they-do-and-do-not-prove)
17. [The frontend dashboard](#17-the-frontend-dashboard)
18. [Running it locally](#18-running-it-locally)
19. [Configuration reference](#19-configuration-reference)
20. [The synthetic dataset](#20-the-synthetic-dataset)
21. [Documentation index](#21-documentation-index)
22. [Known limitations](#22-known-limitations)
23. [Roadmap](#23-roadmap)
24. [Design decisions I would defend, and ones I would revisit](#24-design-decisions-i-would-defend-and-ones-i-would-revisit)
25. [Project structure](#25-project-structure)
26. [Contributing](#26-contributing)
27. [License and contact](#27-license-and-contact)

---

## 1. Why this project exists

Most e-commerce tutorials stop at the happy path. A user adds an item to the cart, pays, and the order appears. That version works on a laptop with one user. It falls apart the moment two people want the last item.

I built E-Cart to work on the parts that tutorials skip:

- **Concurrency.** Two requests read "stock = 1", both decide the purchase is fine, and both write. The shop has now sold something it does not have.
- **Partial failure.** A card is charged, then the stock reservation fails. Or the stock is reserved, then the payment fails. A database transaction cannot reach into a payment provider and undo a charge, so the system needs an explicit plan for each failure.
- **Retries and duplicates.** Networks time out. Users double-click. Payment providers deliver the same webhook more than once. A safe system treats a repeated request as the same request, not a new one.
- **Evidence.** It is easy to say a design is correct. It is harder to write a test that hammers it with 64 threads and checks the books afterwards. Every claim in this README points at a test, a file or a measured number.

The result is a modular Spring Boot application with a conventional shop feature set (catalog, cart, addresses, orders, roles) wrapped around a checkout pipeline that I tried to make honest about its guarantees and its limits.

This is a portfolio and learning project. It is not a production storefront, and the [Known limitations](#22-known-limitations) section says so in plain terms.

---

## 2. What is in the repository

| Folder | What it contains |
|---|---|
| `backend/` | The Spring Boot application, its tests and its Maven wrapper. This is the heart of the project. |
| `frontend/` | A React and Vite dashboard that presents the backend's design, benchmark results, API map and limitations. It is an evidence viewer, not a customer storefront. |
| `docs/` | Design notes, runbooks, the benchmark write-up, and an HTML observability page. |
| `scripts/` | Database schema, role seed SQL, and a PowerShell data import script with small sample CSV files. |
| `data/` | The generator and validator for a large synthetic dataset. The large CSV files themselves are excluded from Git on purpose. |

If you only have five minutes, read sections 6 to 10 of this document and open `OrderServiceImpl.java`. That is where the interesting decisions live.

---

## 3. Architecture at a glance

E-Cart is a **modular monolith**: one deployable Spring Boot application, organised internally by responsibility.

```
                      +--------------------+
                      |  React + Vite SPA  |
                      |  (Stripe Elements) |
                      +----+----------+----+
                           |          |
              REST + JWT   |          |  card details go directly
              (HTTP-only   |          |  to Stripe, never to us
               cookie)     |          v
                           |      +--------+
                           |      | Stripe |----- webhooks ----+
                           |      +--------+                    |
                           v                                     v
                +------------------------------------------------------+
                |                 Spring Boot application              |
                |  Controllers -> Services -> Repositories             |
                |  Security filter chain (JWT)   Global exception      |
                |  Order / Cart / Product / Auth / Stripe / Analytics  |
                +------------+----------------------------+------------+
                             |                            |
                             v                            v
                  +--------------------+        +-------------------+
                  | PostgreSQL (Neon)  |        | Redis (Upstash)   |
                  | source of truth    |        | stock gate        |
                  +--------------------+        +-------------------+
```

A few principles shaped this layout.

**The database is the source of truth.** Stock, orders, payments and users live in PostgreSQL. Everything else is an optimisation or a safety net.

**Redis is a shield, not a ledger.** It exists to turn away doomed requests cheaply during a rush. If Redis disappears, correctness does not change; only load on the database does.

**Stripe is an authority that we verify, never trust.** The browser tells the server "I paid". The server asks Stripe whether that is true, for how much, in which currency and for whom.

**The service is stateless.** There is no server-side session. Authentication is a signed JWT carried in a cookie, so more instances could be added behind a load balancer without sticky sessions.

### Why a monolith

For a project of this size, the most important invariant is "stock, order and payment record change together". Inside one process and one database transaction, that is straightforward. Splitting into separate inventory, order and payment services would turn it into a distributed transaction and force me to build sagas, outboxes and message brokers before I had a scaling reason to. I would rather get the monolith right and extract services when a module genuinely needs to scale or be owned independently. The first extraction candidate would be inventory, since it is the hot path during a sale.

---

## 4. Tech stack and why I chose each piece

| Layer | Technology | Reason |
|---|---|---|
| Language and runtime | Java 21 | Long-term support release, strong typing, mature concurrency tooling. |
| Framework | Spring Boot 4.0.1 | Auto-configuration, dependency injection, a first-class security module and transaction support. |
| Persistence | Spring Data JPA with Hibernate | Productive CRUD, with native or JPQL queries where I need precise SQL. |
| Database | PostgreSQL hosted on Neon | Real row-level locking semantics, strong constraints, and a free hosted tier. |
| Cache and gate | Redis via the Upstash REST API | Atomic `DECRBY`, no infrastructure to run locally. |
| Payments | Stripe Java SDK 29.3.0 | PaymentIntents, idempotency keys, refunds, signed webhooks. |
| Authentication | Spring Security with JJWT 0.13.0 | Stateless JWT carried in an HTTP-only cookie. |
| Mapping | ModelMapper 3.0.0 | Entity to DTO conversion so the API shape is separate from the table shape. |
| API docs | springdoc OpenAPI 3.0.0 | Swagger UI generated from the controllers. |
| Validation | Jakarta Bean Validation | Declarative request checks with consistent error output. |
| Testing | JUnit 5, Spring Boot Test, Spring Security Test | Unit, integration and concurrency tests. |
| Coverage | JaCoCo 0.8.12 | Coverage reporting through Maven. |
| Frontend | React 19, Vite, Tailwind CSS 4, Recharts, React Router | A small, fast dashboard for presenting results. |

---

## 5. The domain model

The application models a marketplace with three roles: **USER** (a buyer), **SELLER** and **ADMIN**.

### Entities

- **User** and **Role.** A user has one or more roles. Roles are a many-to-many relationship through a join table.
- **Category.** A named grouping of products.
- **Product.** Belongs to a category and to a seller. Carries price, discount, a computed special price, quantity in stock and an image path.
- **Cart** and **CartItem.** Each user has one cart. A cart item records the product, quantity, price at the time of adding, and discount.
- **Address.** A user can store several delivery addresses.
- **Order** and **OrderItem.** An order references an address and has many order items. Each order item stores a **snapshot** of the product price and discount at purchase time.
- **Payment.** One-to-one with an order. Stores the payment method, the provider's payment identifier, status and response details.
- **ProcessedStripeEvent.** A ledger of webhook event identifiers that have already been handled.

### A deliberate choice: snapshots

An order item copies the product's price and discount rather than pointing at the live product price. If a seller changes a price next week, last month's order must still show what the customer actually paid. This is intentional denormalisation, and it is the kind that accounting systems depend on.

### Money handling

At checkout, the server recomputes the total from current database prices and converts it to the smallest currency unit using `BigDecimal` with half-up rounding. I flag in the [limitations](#22-known-limitations) that several persisted price fields still use `double`, which I would change to `NUMERIC` and `BigDecimal` before taking this anywhere near real money.

---

## 6. How checkout works, step by step

The checkout path lives in `OrderServiceImpl.placeOrder`. Here is the sequence in plain language.

1. **Prepare a payment (Stripe only).** The browser calls `POST /api/order/stripe-client-secret`. The server computes the amount from the user's cart, creates a Stripe PaymentIntent with an idempotency key, and returns a client secret. The browser uses that secret with Stripe Elements to confirm the card. Card numbers never reach this server.
2. **Place the order.** Once the card is confirmed, the browser calls `POST /api/order/users/payments/{method}` with the address and payment details.
3. **Validate the cart and address.** The cart must exist and contain items. The address must belong to the authenticated user. This second check closes an insecure-direct-object-reference hole: knowing someone else's address id gets you nothing.
4. **Recompute the total from live prices.** The client's idea of the total is ignored.
5. **Verify the payment with Stripe.** The server rejects reuse of a payment identifier it has already seen, retrieves the PaymentIntent from Stripe, and checks its status, amount, currency and the email stored in its metadata against what it expects.
6. **Persist.** The payment, the order and the order items are saved.
7. **Reserve stock.** For each item, the Redis gate is tried first, then the atomic database update. A zero-row result from the database means someone else took the last unit.
8. **Clear the cart.**
9. **On failure, compensate.** If anything goes wrong after money has moved, the code returns reserved stock to Redis and asks Stripe for a refund, then rethrows so the database transaction rolls back.

Cash on delivery follows the same path without the Stripe verification step.

### The questions I used to test the design

- *The card is charged, then stock runs out.* The refund path fires.
- *The browser sends the same payment id twice.* The second request is rejected before it reaches Stripe.
- *The server crashes after saving but before responding.* The webhook, and ideally a reconciliation job, brings the order to its final state.
- *A user sends a payment id that belongs to someone else.* The metadata email check rejects it.

---

## 7. Preventing oversell

### The bug

The naive implementation reads the product, checks the quantity in Java, subtracts and saves:

```java
Product p = repo.findById(id).orElseThrow();
if (p.getQuantity() >= qty) {
    p.setQuantity(p.getQuantity() - qty);
    repo.save(p);
}
```

With one thread, this is fine. With a hundred threads, every one of them can read `quantity = 1` before any of them writes. They all pass the check and they all write. Stock goes negative. This is the classic **lost update**, and `FlashSaleConcurrencyTest` reproduces it deliberately.

### The fix

Move the check into the SQL statement so the database makes the decision atomically:

```sql
UPDATE products
SET quantity = quantity - :qty
WHERE product_id = :id
  AND :qty > 0
  AND quantity >= :qty;
```

The number of affected rows is the answer. One means the reservation succeeded. Zero means the stock was insufficient, and the request is rejected cleanly. This is implemented as `ProductRepository.decrementStockSafely`.

### Why it is correct

PostgreSQL's default isolation level is `READ COMMITTED`. When two transactions update the same row, the second waits for the first to commit or roll back. It then re-evaluates the `WHERE` clause against the newly committed row. If the first transaction took the last unit, the second sees `quantity < :qty` and updates nothing. There is no window in which two buyers both see the last unit as theirs.

### How it compares with the alternatives

| Approach | How it works | Trade-off under heavy contention |
|---|---|---|
| Optimistic locking (`@Version`) | Updates fail if the row changed, and the caller retries | Many wasted retries on a single hot row |
| Pessimistic locking (`SELECT ... FOR UPDATE`) | Holds a lock for the entire transaction | Correct, but requests queue behind each other and the connection pool can run dry |
| Atomic conditional update | One statement, lock held very briefly | Best fit for a single counter |

The test suite includes a database-only microbenchmark (`DbOnlyMicrobenchmarkTest`) that measures the atomic update against `SELECT ... FOR UPDATE` on one hot row, so the comparison rests on a measurement rather than folklore. See [section 16](#16-benchmarks-and-what-they-do-and-do-not-prove) for how to read the numbers.

### A note on deadlocks

If one cart contains products A then B, and another contains B then A, two transactions can each hold one row and wait for the other. PostgreSQL detects this and aborts one of them. The standard defence is to lock rows in a consistent order, for example by sorting items by product id before updating. That refinement is on the [roadmap](#23-roadmap).

---

## 8. The Redis stock gate

### What problem it solves

During a flash sale, the vast majority of requests will lose. Without a gate, each loser still opens a database transaction, borrows a pooled connection and queues on the contested row, only to be told "sold out". That wasted work is what hurts the database.

`RedisStockGateService` puts a cheap check in front. It keeps a counter per product (for example `stock:product:{id}`) and uses the atomic `DECRBY` command:

1. If the counter does not exist yet, prime it from the database using `SET NX` so that concurrent primers do not overwrite each other.
2. Decrement by the requested quantity.
3. If the result is negative, give the quantity back with `INCRBY` and reject the request. No database query was made.
4. Otherwise, continue to the atomic database update.

### What it is, honestly

- It talks to **Upstash over its REST API**. Every call is an HTTPS round trip, so latency is bounded by the network, not by memory speed. I do not describe it as sub-millisecond.
- It **fails open**. If Redis errors out, the gate allows the request through and PostgreSQL's conditional update remains the real guard. I chose availability over strictness here, because the database is already correct on its own.
- It is **not a Lua script**. `DECRBY` is atomic, but the surrounding read-prime-decrement sequence is several calls. A Lua script would make the check-and-decrement a single atomic step and remove the need for a compensating `INCRBY`.
- The Redis counter and the database quantity are **two numbers that can drift apart** after crashes or restocks. A reconciliation job is needed to keep them aligned.

`RedisStockGateTest` demonstrates that, once a product is primed, the extra request is rejected by Redis alone and that rollback restores the counter. `docs/redis-need.md` has the longer reasoning on why the gate is worth having even though the database is already safe.

---

## 9. Payments: Stripe, idempotency and the Saga

### Why a plain transaction is not enough

A database transaction can undo database writes. It cannot undo a card charge. So the system uses a **Saga**: a sequence of steps where each step that has side effects has a defined compensating action.

| Step | Side effect | Compensation if a later step fails |
|---|---|---|
| Charge the card | Money moves at Stripe | Issue a refund |
| Reserve stock in Redis | Counter decreases | Increase the counter |
| Reserve stock in the database | Row quantity decreases | Roll back the transaction, or increase stock on cancellation |
| Create the order | Rows written | Roll back, or move the order to a failed state |

This is an **orchestrated** saga: one service, `OrderServiceImpl`, decides what happens next and what to undo. A choreographed saga, where services react to each other's events, would be the natural shape if the application were split into microservices.

`SagaCompensatingActionTest` proves two scenarios using a fake Stripe implementation: a refund is issued when stock fails after payment succeeded, and stock is returned when payment fails.

### Idempotency in three places

Idempotency means that repeating a request has the same effect as sending it once. E-Cart enforces it at three boundaries.

1. **Outbound to Stripe.** PaymentIntent creation sends an idempotency key. A retried request returns the original PaymentIntent instead of creating a second charge.
2. **In the database.** `payments.pg_payment_id` has a unique constraint, and the service checks for an existing record before proceeding. One PaymentIntent can pay for exactly one order.
3. **On inbound webhooks.** `processed_stripe_events.event_id` is unique. A duplicate delivery is acknowledged and ignored.

`StripeReliabilityPipelineTest` shows two requests with the same key returning the same PaymentIntent, and a `payment_failed` webhook restoring stock and moving the order to a failed state.

The key-generation fallback currently combines the customer, the amount and a one-minute bucket. Two different carts with the same total inside the same minute could collide. The code already accepts a client-supplied key in the request metadata, and the right long-term fix is for the frontend to generate a fresh identifier for each checkout attempt.

### Server-side verification of the payment

Before an order is accepted, the server retrieves the PaymentIntent from Stripe and compares:

- **Status** is `succeeded`.
- **Amount** matches the total the server computed from live prices.
- **Currency** matches the configured currency.
- **Owner**, via metadata, matches the authenticated user.
- **Uniqueness**: the identifier has never been used for an order.

Each of those checks closes a specific attack: paying a small amount for a large order, replaying an old payment, or borrowing someone else's successful payment.

---

## 10. Webhooks

Webhooks exist because the browser is not a reliable messenger. A user can close the tab after paying. A request can time out after the server did the work. Stripe will tell the server what actually happened, independent of the browser.

`StripeWebhookController` handles `POST /api/webhooks/stripe`:

1. **Verify the signature.** The endpoint is public, because Stripe cannot log in, but the payload is authenticated by an HMAC signature checked with the webhook signing secret. An unsigned or tampered request is rejected.
2. **Check the ledger.** If the event id is already in `processed_stripe_events`, return success without doing anything.
3. **Dispatch by event type.** `payment_intent.succeeded` marks the payment as completed. `payment_intent.payment_failed` returns stock to Postgres and Redis and sets the order to a failed state.
4. **Record the event** in the same transaction as the business changes, so both commit or neither does.

### What I would tighten

If a webhook arrives before the order has been saved, which is possible because Stripe can be faster than the browser's follow-up call, the handler cannot find the payment. The right behaviour is to return a non-success status so that Stripe retries later, or to avoid marking the event as processed, rather than silently consuming it. A periodic reconciliation job that lists recent PaymentIntents and compares them with orders would close the gap completely. Both are in the [roadmap](#23-roadmap).

---

## 11. The order lifecycle

Orders move through a small state machine implemented on the `OrderStatus` enum with a `canTransitionTo` check. Illegal jumps, such as delivered back to pending, are refused.

```
PENDING --> CONFIRMED --> SHIPPED --> DELIVERED
   |            |            |
   |            +--> CANCELLED <--+
   +--> PAYMENT_FAILED
```

`DELIVERED`, `CANCELLED` and `PAYMENT_FAILED` are terminal. The reason this matters is money and stock: cancellation triggers restocking and, for paid orders, a refund. If an order could be cancelled twice, it would restock twice and refund twice. Guarded transitions make that impossible by construction.

Sellers can update the status of orders that contain their products. Admins can update any order. A seller who requests an order they have no products in receives a not-found response rather than a forbidden one, so the API does not confirm that the order exists.

---

## 12. Security model

### Authentication

- Users sign up and sign in through `/api/auth/*`.
- On sign-in, the server issues a **JWT** and sets it as an **HTTP-only cookie**, so page scripts cannot read it.
- `AuthTokenFilter` validates the token on each request and populates the security context.
- The session policy is **stateless**.

### Authorisation

Roles protect route groups:

- `/api/public/**` is open.
- `/api/admin/**` requires `ADMIN`.
- `/api/seller/**` requires `ADMIN` or `SELLER`.
- Authenticated user endpoints cover cart, addresses, checkout and order history.

Role checks alone are not enough, so the service layer also enforces **ownership**. An address must belong to the person ordering. A user's order lookup filters by both order id and the user's email. A seller can only manage orders that include their own products.

### Input handling

- Request bodies are validated with Bean Validation, and the global handler returns structured field errors.
- Paging parameters are capped, and sort fields are checked against an allow-list so a client cannot sort by an arbitrary column.
- Uploaded images are limited to 2 MB.

### Cards and PCI scope

The server never sees card numbers. Stripe Elements collects them in the browser and sends them straight to Stripe. The server only handles PaymentIntent identifiers.

### Things I know are not production-grade

I would rather list these myself than have a reviewer find them.

- **CSRF protection is disabled** because the API is stateless, yet the token travels in a cookie, which browsers send automatically. This is only defensible with `SameSite=Lax` or `Strict`, restricted CORS and no state-changing GET endpoints. `jwt.cookie.secure` defaults to `false` for local development and must be `true` over HTTPS.
- **The JWT lifetime is long** (about three and a half days). Short-lived access tokens with refresh tokens would be better, plus a revocation list for logout.
- **Development conveniences remain** in the security chain (`/h2-console/**`, `/api/test/**`) and a startup seeder creates demo accounts. These should be removed or gated behind a profile.
- **There is no rate limiting** on login or checkout.
- **Secrets have appeared in documentation and default properties.** Anyone cloning this repository should treat those values as compromised, and I have listed rotation as a required cleanup in the limitations section.

---

## 13. REST API reference

Interactive documentation is available through Swagger UI at `/swagger-ui/index.html` when the backend is running. The summary below is for orientation.

### Public

| Method | Path | Purpose |
|---|---|---|
| POST | `/api/auth/signup` | Register |
| POST | `/api/auth/signin` | Sign in, sets the JWT cookie |
| POST | `/api/auth/signout` | Clear the cookie |
| GET | `/api/public/products` | Paginated product list |
| GET | `/api/public/products/{id}` | Product detail |
| GET | `/api/public/products/keyword/{kw}` | Keyword search |
| GET | `/api/public/categories` | List categories |
| GET | `/api/public/categories/{id}/products` | Products in a category |
| GET | `/api/public/health` | Liveness check |
| POST | `/api/webhooks/stripe` | Stripe webhook (signature verified) |

### Authenticated user

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/auth/user` | Current user |
| GET | `/api/carts/users/cart` | Current cart |
| POST | `/api/carts/products/{id}/quantity/{q}` | Add to cart |
| PUT | `/api/cart/products/{id}/quantity/{op}` | Increase or decrease quantity |
| DELETE | `/api/carts/{cartId}/product/{id}` | Remove from cart |
| GET, POST | `/api/addresses` | List and create addresses |
| POST | `/api/order/stripe-client-secret` | Create a PaymentIntent |
| POST | `/api/order/users/payments/{method}` | Place an order |
| GET | `/api/users/orders` and `/{id}` | Order history and detail |

### Seller

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/seller/products` | Own products |
| POST | `/api/seller/categories/{id}/product` | Create a product |
| PUT, DELETE | `/api/seller/products/{id}` | Update or delete |
| PUT | `/api/seller/products/{id}/image` | Upload an image |
| GET | `/api/seller/orders` | Orders containing own products |
| PUT | `/api/seller/orders/{id}/status` | Update order status |

### Admin

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/admin/orders` | All orders |
| PUT | `/api/admin/orders/{id}/status` | Update any order status |
| GET | `/api/admin/app/analytics` | Aggregate analytics |
| POST, PUT, DELETE | `/api/admin/categories` | Manage categories |
| POST, PUT, DELETE | `/api/admin/products` | Manage products |
| GET | `/api/auth/sellers` | List sellers |

### Errors

A global exception handler returns consistent JSON. Missing resources give `404`, business-rule violations give `400`, and validation problems return a map of field names to messages.

---

## 14. Database design

### Relationships

```
users 1---* addresses
users *---* roles          (user_role)
users 1---1 carts 1---* cart_items *---1 products
categories 1---* products *---1 users (seller)
users 1---* orders *---1 addresses
orders 1---* order_items *---1 products
orders 1---1 payments
processed_stripe_events    (standalone idempotency ledger)
```

### Constraints that carry real weight

- `payments.pg_payment_id` is **unique**. This is what stops one Stripe payment from buying two orders, even if two requests race past the application-level check.
- `processed_stripe_events.event_id` is **unique**. Two concurrent deliveries of the same webhook cannot both be recorded.
- The stock update's `WHERE quantity >= :qty` predicate acts as a guard that the schema alone cannot express.

### Migrations

The application runs with `spring.jpa.hibernate.ddl-auto=none`, so Hibernate never alters a live schema. Schema changes are applied as SQL scripts. `V1__baseline_and_hardening.sql` is written to be re-runnable: it uses existence checks when adding the unique payment constraint, the webhook ledger and the Stripe customer column. A full schema and role seed are in `scripts/schema.sql` and `scripts/roles_seed.sql`.

### Indexing

Primary keys and the unique constraints above are in place. If you are evaluating this for real traffic, the indexes I would add next, driven by the existing queries, are:

- `orders (email, order_date DESC)` for order history with paging.
- `order_items (order_id)` and `order_items (product_id)`, since PostgreSQL does not index foreign keys automatically.
- `products (category_id, price)` for category listings sorted by price.
- `products (seller_id)` for the seller dashboard.
- A trigram (`pg_trgm`) GIN index, or a move to a proper search engine, for keyword search, because a B-tree cannot help a `LIKE '%term%'` query.

### Connection handling

The default JDBC URL points at Neon's pooled endpoint and sets `prepareThreshold=0`. Transaction-mode poolers can hand each transaction a different server connection, which breaks server-side prepared statements, so they are disabled. Keep the Hikari pool modest: the database, not the application server, is the bottleneck.

---

## 15. Testing strategy

Tests live under `backend/src/test/java/com/ecommerce/project/` and are grouped by what they prove.

| Area | Class | What it demonstrates |
|---|---|---|
| Concurrency | `FlashSaleConcurrencyTest` | A naive read-modify-write oversells. The atomic conditional update does not. With 50 buyers and 10 units, exactly 10 succeed and final stock is 0. |
| Concurrency | `DbOnlyMicrobenchmarkTest` | Measures atomic `UPDATE` versus `SELECT ... FOR UPDATE` on a single hot row, with no Stripe and no Redis involved. |
| Concurrency | `BbdFlashSaleSimulationTest` | The full simulation: 2,000 buyers, 1,000 units, 64 threads, random payment declines, real PostgreSQL. |
| Redis gate | `RedisStockGateTest` | Once primed, the extra reservation is rejected by Redis and rollback restores the counter. |
| Payments | `StripeReliabilityPipelineTest` | Idempotency-key behaviour and webhook-driven compensation. |
| Payments | `SagaCompensatingActionTest` | Refund on stock failure, restock on payment failure. |
| Validation | `OrderValidationTests` | Ownership, empty cart and amount checks on `placeOrder`. |
| Security | `SecurityTests` | Route protection and role rules. |
| Data | `DataImporterTest` | The bulk import utility. |
| Reporting | `BenchmarkReportWriterTest`, `BenchmarkReportListener` | Writes measured results to JSON so the frontend can display them. |
| Smoke | `SbEcomApplicationTests` | The context starts. |

### Test doubles

`FakeStripeService` and `FakeStripeTestConfig` replace the real Stripe client in tests that should not hit the network. That separation is possible because Stripe access sits behind a `StripeService` interface, which also makes the payment flow easy to reason about in isolation.

### Test database

Automated tests run against a dedicated PostgreSQL database named `ecommerce_test` so the main data is never touched.

### What is missing

- The tests use a regular PostgreSQL instance. Testcontainers would make them self-contained.
- There are no contract tests for Stripe webhook payloads.
- There is no chaos test that kills Redis mid-run to confirm the fail-open path under load.

---

## 16. Benchmarks and what they do and do not prove

### The flash-sale simulation

`BbdFlashSaleSimulationTest` models a "big billion days" style sale on a single hot product.

| Parameter or result | Value |
|---|---|
| Concurrent buyers | 2,000 |
| Stock | 1,000 units |
| Worker threads | 64 |
| Confirmed orders | 1,000 |
| Payment failures, compensated | 286 |
| Out-of-stock rejections | 714 |
| Final stock | 0, never negative |
| Elapsed time | 69.27 seconds |
| End-to-end throughput | 28.87 requests per second |

The outcomes account for every buyer: 1,000 confirmed orders, plus 714 clean out-of-stock rejections, plus 286 payment failures that were compensated, makes 2,000. What the numbers establish is the invariant: **exactly as many orders were confirmed as there were units, and the books balanced at zero afterwards.**

### How to read the throughput

The 28.87 requests per second figure measures the complete path: authentication, cart handling, Stripe interaction and multi-table writes against a remote hosted database. It is not a measure of how fast the stock decrement is. It is also bounded by network latency to Neon and Stripe, and by the connection pool size.

The frontend shows two other figures, roughly 3,000 requests per second for the atomic update and 300 to 800 for `SELECT ... FOR UPDATE`. **Those are estimates carried over from a design note, and they are labelled as such in the UI.** They are meant to be replaced by results from `DbOnlyMicrobenchmarkTest`, which records operations per second, p50 and p95 latency, thread count and pool size.

### What this proves, and what it does not

It proves **correctness under contention**: no oversell, no negative stock, compensation works, duplicates are handled. It does not prove peak capacity. A true capacity test would need load generation from a separate machine, a database in the same region, and a production-like pool configuration.

---

## 17. The frontend dashboard

The `frontend/` folder is a React 19 and Vite application styled with Tailwind CSS 4. It exists to let a reader see the engineering without reading every file first.

It presents:

- **Result.** The flash-sale numbers, with the test that produced them.
- **Race demo.** An interactive illustration of the read-then-write bug versus the atomic update.
- **Saga.** The payment failure and compensation flow.
- **Stripe evidence.** A screenshot of a real sandbox transaction captured during the simulation.
- **Throughput.** Measured and estimated figures, clearly labelled, plus live rows pulled from the benchmark report file when it exists.
- **Redis.** A short, candid description of what the gate is and is not.
- **API map.** The endpoint tables for each role, and a link to Swagger UI.
- **Documentation index.** Pointers into the `docs/` folder.
- **Known limitations.** The same list as in this README.

A small status indicator in the header calls `/api/public/health` and shows whether a backend is reachable. In development, the Vite server proxies `/api`, `/swagger-ui`, `/v3/api-docs` and `/images` to `http://localhost:8080`.

To run it:

```bash
cd frontend
npm install
npm run dev
```

The dashboard opens at `http://localhost:5173`. To point it at a backend elsewhere, set `VITE_API_URL`.

---

## 18. Running it locally

### Prerequisites

- **Java 21**
- **Node.js** (current LTS) for the dashboard
- A **PostgreSQL** database, either local or a free Neon project
- An **Upstash Redis** database, if you want the stock gate active. Without it, the gate fails open.
- A **Stripe account in test mode**, for payment flows

### 1. Clone

```bash
git clone https://github.com/RohitGoswami2023/E-Cart.git
cd E-Cart
```

### 2. Provide configuration through environment variables

Never put real credentials in a file that is committed. On macOS or Linux:

```bash
export DB_URL="jdbc:postgresql://<host>/<database>?sslmode=require&prepareThreshold=0"
export DB_USER="<user>"
export DB_PASSWORD="<password>"
export SPRING_JPA_HIBERNATE_DDL_AUTO="none"
export UPSTASH_REDIS_REST_URL="<your upstash url>"
export UPSTASH_REDIS_REST_TOKEN="<your upstash token>"
export STRIPE_SECRET_KEY="sk_test_..."
export FRONTEND_URL="http://localhost:5173"
```

On Windows PowerShell, use `$env:NAME = "value"` instead of `export`.

### 3. Prepare the database

Apply `scripts/schema.sql` and `scripts/roles_seed.sql` to your database, then the baseline migration under `backend/src/main/resources/db/migration`.

### 4. Start the backend

```bash
cd backend
./mvnw spring-boot:run
```

On Windows, use `mvnw.cmd`. The API listens on `http://localhost:8080`, and Swagger UI is at `http://localhost:8080/swagger-ui/index.html`.

### 5. Start the dashboard

```bash
cd frontend
npm install
npm run dev
```

### 6. Wire up Stripe webhooks (optional)

Install the Stripe CLI and forward events to your local server, then place the signing secret it prints into the `stripe.webhook.secret` property or its environment variable:

```bash
stripe listen --forward-to localhost:8080/api/webhooks/stripe
```

### Running the tests

```bash
cd backend
./mvnw test                                   # everything
./mvnw test -Dtest=FlashSaleConcurrencyTest   # oversell proof
./mvnw test -Dtest=RedisStockGateTest         # Redis gate
./mvnw test -Dtest=StripeReliabilityPipelineTest
./mvnw test -Dtest=SagaCompensatingActionTest
./mvnw test -Dtest=DbOnlyMicrobenchmarkTest   # DB-only measurements
./mvnw test -Dtest=BbdFlashSaleSimulationTest # the 2,000-buyer simulation
```

Tests use the `ecommerce_test` database. Stripe-dependent tests run with the fake service unless you deliberately supply a sandbox key. JaCoCo coverage is produced through the Maven build.

### Packaging

```bash
./mvnw clean package -DskipTests
```

---

## 19. Configuration reference

| Property | Environment variable | Default | Notes |
|---|---|---|---|
| `spring.datasource.url` | `DB_URL` | Neon pooled URL | Include `prepareThreshold=0` when using a transaction-mode pooler. |
| `spring.datasource.username` | `DB_USER` | none you should rely on | |
| `spring.datasource.password` | `DB_PASSWORD` | none you should rely on | |
| `spring.jpa.hibernate.ddl-auto` | `SPRING_JPA_HIBERNATE_DDL_AUTO` | `none` | Keep `none` outside throwaway environments. |
| `jwt.cookie.secure` | `JWT_COOKIE_SECURE` | `false` | Set to `true` behind HTTPS. |
| `frontend.url` | `FRONTEND_URL` | `http://localhost:5173` | Used for CORS. |
| `image.base.url` | `IMAGE_BASE_URL` | `http://localhost:8080/images` | Base for product image links. |
| `stripe.secret` | `STRIPE_SECRET_KEY` | empty | Use a test key outside production. |
| `stripe.webhook.secret` | see `application.properties` | empty | Signing secret from Stripe. |
| `stripe.currency` | `STRIPE_CURRENCY` | `usd` | Lowercase ISO code. |
| `upstash.redis.rest.url` | `UPSTASH_REDIS_REST_URL` | none you should rely on | |
| `upstash.redis.rest.token` | `UPSTASH_REDIS_REST_TOKEN` | none you should rely on | |
| `spring.app.jwtSecret` | see `application.properties` | none you should rely on | Use a long random value. |
| `spring.app.jwtExpirationMs` | n/a | about 3.5 days | Consider shortening it. |
| `spring.servlet.multipart.max-file-size` | n/a | `2MB` | Image upload limit. |

---

## 20. The synthetic dataset

To test realistic query behaviour, the `data/` folder holds a generator (`generate_v2.py`) and a validator (`validate_v2.py`) for a large, entirely synthetic dataset. The generated CSV files are excluded from version control because of their size, but the scripts to reproduce them are included, along with a validation report.

| Table | Rows |
|---|---|
| Users | 1,000,000 |
| Products | 100,000 |
| Orders | 2,000,000 |
| Order items | 3,930,188 |
| Payments | 2,000,000 (exactly one per order) |
| Carts | 392,999 cart lines across 213,000 carts |
| Reviews | 1,000,000 |

The validator checks 45 business rules and all 45 pass. A few examples of what it enforces:

- Signup date precedes order date, which precedes payment date.
- Only active, verified users place orders, and only active products are ordered.
- The ordered unit price equals the product price.
- Line totals, subtotals and order totals reconcile arithmetically.
- Shipping is free from a subtotal of 499 and otherwise 49, in the dataset's local currency.
- Reviews are verified purchases written only by customers who received the product, and each user reviews a given product at most once.

Everything is synthetic. Email addresses use a reserved test domain, and phone numbers are placeholders. Nothing here is real customer data.

Smaller sample files and a PowerShell import script live in `scripts/` for quick experiments.

---

## 21. Documentation index

| File | What you will find |
|---|---|
| `docs/flash-sale-and-saga-benchmark.md` | The benchmark write-up, the invariant being tested and the Stripe trace. |
| `docs/redis-need.md` | The reasoning for the Redis gate and its limits. |
| `docs/pipelines-runbook.md` | Three verification pipelines and the exact commands to run them. |
| `docs/commands.md` | Build, run and individual test commands. |
| `docs/system-design-guide.md` | Design patterns and trade-offs in this codebase. |
| `docs/system-design-interview-playbook.md` | Distributed-systems concepts applied to this project. |
| `docs/interview-technical-defense.md` | Questions and answers about the design decisions. |
| `docs/backend-observability-cockpit.html` | A standalone observability page. |
| `docs/stripe-dashboard-success.jpg` | Screenshot of a sandbox transaction from the simulation. |

> **Housekeeping note.** Some runbooks in `docs/` contain example commands with real-looking credentials. If you are reading an older clone, treat anything resembling a key, token or password in those files as exposed, and replace it with an environment variable in your own copy.

---

## 22. Known limitations

I would rather state these than have them discovered.

**Correctness and design**

- **The Redis gate can drift.** The counter and the database quantity are separate, and there is no reconciliation job yet. One specific risk is that compensation can run on more than one code path after a failure and increase the Redis counter twice.
- **Webhook race.** An event that arrives before its order exists can be recorded as processed and lost.
- **Status vocabulary is not fully unified.** A payment marked as completed by the webhook is not described with the same word the cancellation path checks before issuing a refund. One shared status type would remove the mismatch.
- **Stripe is called inside a database transaction.** That holds a connection and row locks during a network call and cannot be rolled back with the database. Refunds that fail are only logged.
- **Idempotency key fallback** can collide for equal-value carts within one minute.
- **Rows are not locked in a consistent order**, so opposite-ordered carts can deadlock.
- **No expired-reservation sweeper.** Abandoned pending orders rely on webhooks alone.

**Data and money**

- Several price fields are stored as `double`. They should be `NUMERIC` with `BigDecimal`, or integer minor units.
- `Product` loads some associations eagerly, which can cause extra queries on read paths.
- Offset pagination will slow down on very deep pages.

**Security and operations**

- CSRF protection is disabled with a cookie-carried JWT. See [section 12](#12-security-model).
- Long-lived JWT, no refresh tokens, no revocation.
- Development routes and seeded demo accounts need to be removed from any real deployment.
- No rate limiting on authentication or checkout.
- **Credentials have been committed in documentation and default configuration.** They must be rotated, and secrets must come from the environment or a secrets manager.
- Product images are stored on the local disk, which does not work with more than one instance.
- Field injection is used in many classes. Constructor injection would be more testable.

**Measurement**

- The throughput estimates for the DB-only approaches are unmeasured until `DbOnlyMicrobenchmarkTest` has been run.
- The 28.87 requests per second figure comes from one machine against a remote database and should not be read as capacity.

---

## 23. Roadmap

In rough order of value:

1. **Single compensation path.** Remove duplicated stock rollback so each failure returns stock exactly once.
2. **Lua-based Redis reservation.** Atomic check-and-decrement, plus a scheduled reconciliation job against PostgreSQL.
3. **Fix the webhook race.** Do not consume an event whose payment is not yet visible, and add a PaymentIntent reconciliation worker.
4. **Unify payment status** into one enum, including a refunded state, so double refunds are impossible.
5. **Stock holds with a time limit.** Reserve before charging, convert to a sale on success, release on failure or expiry, with an idempotent sweeper.
6. **Outbox for refunds.** Write compensations to a table in the same transaction and let a worker retry until they succeed.
7. **Consistent lock ordering** when decrementing multiple products.
8. **Money as `NUMERIC` and `BigDecimal`** throughout.
9. **Testcontainers** for self-contained PostgreSQL tests, plus webhook contract tests.
10. **Security hardening:** shorter tokens with refresh, `SameSite` cookies, rate limiting, removal of dev routes and seeded accounts, constructor injection.
11. **Observability:** Micrometer metrics, structured logs with correlation ids, tracing, and a stock-drift gauge.
12. **Object storage** for product images and **keyset pagination** for order history.
13. **Search** through a trigram index first, then a dedicated engine if needed.

---

## 24. Design decisions I would defend, and ones I would revisit

### Defend

**Moving the stock check into SQL.** It is the smallest change that eliminates the entire class of lost-update bugs, and it has a test that shows the failure it prevents.

**Treating Redis as optional.** Because the database is correct on its own, a Redis outage degrades performance rather than correctness. That is the right way round for a cache-like component.

**Verifying payments server-side.** The checks on status, amount, currency, owner and uniqueness are cheap and each one blocks a realistic attack.

**Recomputing prices on the server.** The client's total is never an input to money decisions.

**Recording order-item snapshots.** Historical orders stay truthful when prices change.

**A modular monolith.** It keeps the core invariant inside one transaction and keeps the project understandable to a single reader.

### Revisit

**Calling Stripe inside the transaction.** I would restructure to hold stock first with an expiry, charge outside any database transaction, then convert or release the hold. Compensations would go through an outbox so they cannot be lost.

**The Redis implementation.** A native client with a circuit breaker and a Lua script would be both faster and simpler to reason about than HTTP calls with a manual compensating increment.

**Cookie-based JWT without CSRF defences.** Either add proper CSRF handling and strict cookie attributes, or switch to short-lived bearer tokens with a refresh flow.

**Entity design shortcuts.** `double` prices, eager associations and an order that stores an email string instead of a user reference all make sense for a quick build and not for a long-lived system.

---

## 25. Project structure

```
E-Cart/
├── backend/
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/com/ecommerce/project/
│       │   │   ├── config/          application constants, Swagger, web config
│       │   │   ├── controller/      REST endpoints, including the Stripe webhook
│       │   │   ├── exceptions/      API exceptions and the global handler
│       │   │   ├── model/           JPA entities and enums (OrderStatus, PaymentMethod)
│       │   │   ├── payload/         request and response DTOs
│       │   │   ├── repositories/    Spring Data repositories
│       │   │   ├── security/        filter chain, JWT utilities, user details
│       │   │   └── service/         business logic: orders, cart, products, Stripe, Redis gate
│       │   └── resources/
│       │       ├── application.properties
│       │       └── db/migration/    baseline SQL migration
│       └── test/java/com/ecommerce/project/
│           ├── benchmark/           report writer and listener
│           ├── concurrency/         flash-sale and microbenchmark tests
│           ├── security/            route and role tests
│           ├── service/             Stripe fakes, saga, Redis gate, validation tests
│           └── util/                data importer test
├── frontend/                        React + Vite dashboard
├── docs/                            design notes and runbooks
├── scripts/                         schema, seeds, import script, sample CSVs
├── data/                            generator and validator for the synthetic dataset
└── LICENSE
```

---

## 26. Contributing

This is primarily a personal learning project, but thoughtful feedback is welcome. If you spot a bug, a flawed argument in the documentation or a better way to handle one of the problems above, please open an issue with:

- What you expected, and what actually happened.
- Steps or a failing test that reproduces it.
- If it concerns concurrency, the number of threads and the database you used, since results depend heavily on both.

For code changes, please open an issue first so we can agree on the approach. Keep pull requests small and focused, include a test that fails before the change and passes after it, and avoid committing credentials, local configuration or generated data.

---

## 27. License and contact

Released under the **MIT License**. See the `LICENSE` file for the full text.

Built by **Rohit Goswami**. The repository is at <https://github.com/RohitGoswami2023/E-Cart>. Questions and suggestions are welcome through GitHub issues.

If this project is useful to you as a study aid, the most valuable thing you can do is read `OrderServiceImpl.java` alongside section 6, then try to break it with the concurrency tests. That is how I found most of the problems listed above.
