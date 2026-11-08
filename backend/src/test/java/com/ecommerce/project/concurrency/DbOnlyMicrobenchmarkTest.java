package com.ecommerce.project.concurrency;

import com.ecommerce.project.benchmark.BenchmarkReport;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.repositories.ProductRepository;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.Arrays;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DB-only microbenchmark: PostgreSQL + the JPA/JDBC stack, nothing else.
 * No Stripe, no Redis, no HTTP, no cart/order writes.
 *
 * Compares the two correct strategies on ONE hot row:
 *   1. atomic conditional UPDATE  (ProductRepository.decrementStockSafely)
 *   2. SELECT ... FOR UPDATE then UPDATE (pessimistic lock)
 *
 * Tunable: -Dbench.ops=5000 -Dbench.threads=32
 * Results go to BenchmarkReport as suites "DbOnlyAtomicDecrement" and "DbOnlyForUpdate".
 * Numbers depend on the machine, pool size and network latency to Postgres; the report records them.
 */
@SpringBootTest
@ActiveProfiles("test")
public class DbOnlyMicrobenchmarkTest {

    private static final int OPS = Integer.getInteger("bench.ops", 5000);
    private static final int THREADS = Integer.getInteger("bench.threads", 32);
    private static final int WARMUP_OPS = 500;

    @Autowired private ProductRepository productRepository;
    @Autowired private PlatformTransactionManager transactionManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private DataSource dataSource;

    private record Result(int success, int failed, long wallMs, long[] nanos) { }

    @Test
    @DisplayName("DB-only: atomic conditional UPDATE on a hot row")
    void atomicUpdate() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        measure("DbOnlyAtomicDecrement",
                id -> tx.execute(s -> productRepository.decrementStockSafely(id, 1) > 0));
    }

    @Test
    @DisplayName("DB-only: SELECT ... FOR UPDATE then UPDATE on a hot row")
    void selectForUpdate() throws Exception {
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        measure("DbOnlyForUpdate", id -> tx.execute(s -> {
            Integer qty = jdbc.queryForObject(
                    "SELECT quantity FROM products WHERE product_id = ? FOR UPDATE", Integer.class, id);
            if (qty == null || qty < 1) return false;
            jdbc.update("UPDATE products SET quantity = quantity - 1 WHERE product_id = ?", id);
            return true;
        }));
    }

    private void measure(String suite, Function<Long, Boolean> purchase) throws Exception {
        // Warm up JIT, connection pool and plan cache on a throwaway product.
        Long warm = newProduct(WARMUP_OPS);
        try { run(purchase, warm, WARMUP_OPS); } finally { productRepository.deleteById(warm); }

        Long id = newProduct(OPS);
        try {
            Result r = run(purchase, id, OPS);
            int finalStock = productRepository.findById(id).orElseThrow().getQuantity();
            boolean passed = r.success() == OPS && r.failed() == 0 && finalStock == 0;

            long[] sorted = r.nanos().clone();
            Arrays.sort(sorted);
            double opsPerSec = OPS * 1000.0 / Math.max(1, r.wallMs());

            BenchmarkReport.record(suite, passed, r.wallMs(), OPS, finalStock, r.success(), r.failed());
            BenchmarkReport.putExtra(suite, "ops", OPS);
            BenchmarkReport.putExtra(suite, "threads", THREADS);
            BenchmarkReport.putExtra(suite, "opsPerSec", Math.round(opsPerSec * 10) / 10.0);
            BenchmarkReport.putExtra(suite, "p50Ms", round2(sorted[sorted.length / 2] / 1e6));
            BenchmarkReport.putExtra(suite, "p95Ms", round2(sorted[(int) (sorted.length * 0.95)] / 1e6));
            BenchmarkReport.putExtra(suite, "poolSize",
                    dataSource instanceof HikariDataSource h ? h.getMaximumPoolSize() : null);
            BenchmarkReport.putExtra(suite, "postgres", shortVersion());
            BenchmarkReport.putExtra(suite, "java", System.getProperty("java.version"));

            // Correctness, not speed: never assert on throughput (machine dependent).
            assertThat(r.success()).isEqualTo(OPS);
            assertThat(finalStock).isZero();
        } finally {
            productRepository.deleteById(id);
        }
    }

    private Result run(Function<Long, Boolean> purchase, Long id, int ops) throws InterruptedException {
        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        CountDownLatch done = new CountDownLatch(ops);
        AtomicInteger ok = new AtomicInteger();
        AtomicInteger fail = new AtomicInteger();
        AtomicInteger idx = new AtomicInteger();
        long[] nanos = new long[ops];
        long t0 = System.nanoTime();
        for (int i = 0; i < ops; i++) {
            pool.submit(() -> {
                long s = System.nanoTime();
                try {
                    if (Boolean.TRUE.equals(purchase.apply(id))) ok.incrementAndGet(); else fail.incrementAndGet();
                } catch (Exception e) {
                    fail.incrementAndGet();
                } finally {
                    nanos[idx.getAndIncrement()] = System.nanoTime() - s;
                    done.countDown();
                }
            });
        }
        done.await();
        long wallMs = (System.nanoTime() - t0) / 1_000_000;
        pool.shutdown();
        return new Result(ok.get(), fail.get(), wallMs, nanos);
    }

    private Long newProduct(int stock) {
        Product p = new Product();
        p.setProductName("Microbenchmark hot row");
        p.setDescription("DB-only microbenchmark product");
        p.setQuantity(stock);
        p.setPrice(1000.0);
        p.setDiscount(0.0);
        p.setSpecialPrice(1000.0);
        return productRepository.save(p).getProductId();
    }

    private String shortVersion() {
        String v = jdbc.queryForObject("select version()", String.class);
        String[] parts = v == null ? new String[0] : v.split(" ");
        return parts.length >= 2 ? parts[0] + " " + parts[1] : v;
    }

    private static double round2(double v) { return Math.round(v * 100) / 100.0; }
}
