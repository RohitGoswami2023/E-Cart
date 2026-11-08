// Every number here is traceable to a file in the repo. Edit here, nowhere else.
export const flashSale = {
  source: 'docs/flash-sale-and-saga-benchmark.md · BbdFlashSaleSimulationTest',
  rows: [
    ['Concurrent buyers', '2,000', 'real users from the 1M-row table'],
    ['Stock on hot product', '1,000', ''],
    ['Worker threads', '64', 'against PostgreSQL'],
    ['Confirmed orders', '1,000', 'equals stock'],
    ['Payment failures, compensated', '286', 'stock returned by saga'],
    ['Out-of-stock rejections', '714', 'clean 4xx, no side effects'],
    ['Final stock', '0', 'never negative'],
    ['Elapsed', '69.27 s', ''],
    ['Throughput', '28.87 req/s', 'end-to-end incl. Stripe + multi-table writes'],
  ],
};

export const throughput = [
  { claim: 'Atomic conditional UPDATE', value: '~3,000+ req/s', status: 'estimate', note: 'From interview doc. Unmeasured. Compare with the measured DB-only rows below.' },
  { claim: 'SELECT … FOR UPDATE', value: '~300–800 req/s', status: 'estimate', note: 'From interview doc. Unmeasured. Compare with the measured DB-only rows below.' },
  { claim: 'Full checkout incl. Stripe', value: '28.87 req/s', status: 'measured', note: 'BbdFlashSaleSimulationTest, 64 threads.' },
];

export const sagaSteps = [
  ['Validate cart, re-read prices', 'Never trust client totals.'],
  ['Atomic stock decrement (rows updated = 0 → reject)', 'UPDATE … WHERE quantity >= :qty'],
  ['Create order + items, clear cart (one @Transactional)', 'Local ACID: all or nothing.'],
  ['Stripe PaymentIntent with idempotency key', 'External call: cannot be rolled back by the DB.'],
  ['Success → order Accepted', ''],
  ['Failure / webhook payment_failed → incrementStock, order "Payment Failed"', 'Compensating action, in Postgres and the Redis counter.'],
];

export const endpoints = {
  Public: [['POST', '/api/auth/signin'], ['POST', '/api/auth/signup'], ['POST', '/api/auth/signout'], ['GET', '/api/public/products'], ['GET', '/api/public/products/{id}'], ['GET', '/api/public/products/keyword/{kw}'], ['GET', '/api/public/categories'], ['GET', '/api/public/categories/{id}/products'], ['GET', '/api/public/health'], ['POST', '/api/webhooks/stripe  (Stripe signature verified)']],
  User: [['GET', '/api/auth/user'], ['GET', '/api/carts/users/cart'], ['POST', '/api/carts/products/{id}/quantity/{q}'], ['PUT', '/api/cart/products/{id}/quantity/{op}'], ['DELETE', '/api/carts/{cartId}/product/{id}'], ['GET/POST', '/api/addresses'], ['POST', '/api/order/stripe-client-secret'], ['POST', '/api/order/users/payments/{method}'], ['GET', '/api/users/orders[/{id}]']],
  Seller: [['GET', '/api/seller/products'], ['POST', '/api/seller/categories/{id}/product'], ['PUT/DELETE', '/api/seller/products/{id}'], ['PUT', '/api/seller/products/{id}/image'], ['GET', '/api/seller/orders'], ['PUT', '/api/seller/orders/{id}/status']],
  Admin: [['GET', '/api/admin/orders'], ['PUT', '/api/admin/orders/{id}/status'], ['GET', '/api/admin/app/analytics'], ['POST/PUT/DELETE', '/api/admin/categories[/{id}]'], ['POST/PUT/DELETE', '/api/admin/products…'], ['GET', '/api/auth/sellers']],
};

export const dataset = [
  ['Users', '1,000,000'], ['Products', '100,000'], ['Orders', '2,000,000'], ['Order items', '3,930,188'],
  ['Payments', '2,000,000 (1 per order)'], ['Reviews', '1,000,000'], ['Validation checks', '45 / 45 pass'],
];

export const limitations = [
  { t: 'Throughput numbers are partly unmeasured', s: 'warn', d: 'The 3,000+ and 300–800 req/s figures are estimates. A DB-only microbenchmark (no Stripe, no Redis) is the planned replacement; see the slot above.' },
  { t: 'Redis gate: HTTP round trip, fails open', s: 'warn', d: 'Implemented with Upstash REST DECRBY, not a Lua script. DECRBY is atomic, but each call is a network request, so "sub-millisecond" does not apply. If Redis errors, tryReserveStock returns true and PostgreSQL’s atomic UPDATE remains the real guard.' },
  { t: 'CSRF disabled with a cookie-carried JWT', s: 'bad', d: 'Stateless API, csrf().disable(). Cookie-based auth normally needs CSRF defence. Mitigation to state: SameSite=Lax/Strict on the JWT cookie, CORS restricted to frontend.url, no state change on GET. jwt.cookie.secure defaults to false for local dev and must be true in production.' },
  { t: '/h2-console/** and /api/test/** are permitAll', s: 'bad', d: 'Dev conveniences left in the production security chain. Should be removed or gated by a profile before deployment.' },
  { t: 'Stripe test key committed in docs', s: 'bad', d: 'Present in the pipelines runbook. Rotate it and move to environment variables.' },
  { t: 'No expired-reservation sweeper yet', s: 'warn', d: 'Abandoned PENDING orders are covered by webhooks only; a TTL reconciliation worker is a documented next step.' },
];
