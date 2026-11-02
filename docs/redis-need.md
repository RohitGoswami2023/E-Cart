# Do I need Redis?

## When Redis adds value
| Scenario | What Redis solves | Is it required for CSV import? |
|----------|-------------------|---------------------------------|
| **Product catalog caching** | Serves read‑heavy product listings from memory, reducing DB load and latency. | **No** – the import runs once and the backend can serve directly from PostgreSQL.
| **Flash‑sale stock gate** | Atomic `DECR` permits sub‑millisecond contention handling; you can keep a fast in‑memory counter and fall back to the DB if the key is missing. | **Optional** – you can achieve the same correctness with an atomic `UPDATE … WHERE quantity >= …` SQL statement (Approach 2 in your plan). Use Redis only if you want to showcase a high‑throughput gate.
| **Rate limiting / short‑lived data** | Stores login‑attempt counters, one‑time tokens, etc. | Not needed for the one‑time import.

## Recommendation
1. **Implement the CSV importer first** – it works perfectly without any cache.
2. **Add Redis later** if you plan to demonstrate a flash‑sale where thousands of concurrent buyers contend for a few items. In that case:
   * Add the Spring Data Redis starter dependency.
   * Create a `RedisTemplate<String, Integer>` bean.
   * Write a tiny `StockGateService` that:
     ```java
     Long remaining = redisTemplate.opsForValue().decrement(stockKey);
     if (remaining == null || remaining < 0) {
         // fallback to DB atomic update or reject purchase
     }
     ```
   * Keep PostgreSQL as the source of truth and use a scheduled job to reconcile any drift.
3. If you never need the flash‑sale gate, you can safely omit Redis from the project and keep the architecture simpler.

---
*This note lives in `docs/redis-need.md` for easy reference when you decide whether to add the caching layer.*
