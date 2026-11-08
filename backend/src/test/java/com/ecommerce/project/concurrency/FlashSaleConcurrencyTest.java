package com.ecommerce.project.concurrency;

/**
 * Flash Sale Concurrency Integration Test
 *
 * Why & Where it is used:
 *   Stress-tests the atomic conditional UPDATE logic (`quantity = quantity - :qty WHERE quantity >= :qty`)
 *   under high-concurrency multi-threaded execution (e.g. 50 parallel buyers competing for 10 units).
 *
 * Imports & Key Dependencies:
 *   - Repositories: ProductRepository (executes decrementStockSafely).
 *   - Concurrency Utilities: CountDownLatch (simultaneous thread release), ExecutorService, AtomicInteger.
 *   - Spring Transaction: PlatformTransactionManager, TransactionTemplate.
 *
 * Exports & Consumers:
 *   - Executed via `mvnw test "-Dtest=FlashSaleConcurrencyTest"`.
 *   - Proves zero overselling guarantee for the portfolio presentation.
 *
 * Project Impact:
 *   Provides empirical verification that under intense contention, exactly 10 requests succeed, 40 fail,
 *   and the final inventory balance settles at exactly 0 (never negative).
 */

import com.ecommerce.project.benchmark.BenchmarkReport;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.repositories.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class FlashSaleConcurrencyTest {

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private PlatformTransactionManager transactionManager;

    private Long testProductId;

    @BeforeEach
    void setupProduct() {
        Product product = new Product();
        product.setProductName("Flash Sale iPhone 16 Pro");
        product.setDescription("Brand new smartphone for flash sale testing");
        product.setQuantity(10);
        product.setPrice(120000.0);
        product.setDiscount(0.0);
        product.setSpecialPrice(110000.0);
        Product saved = productRepository.save(product);
        testProductId = saved.getProductId();
    }

    @Test
    @DisplayName("Atomic Conditional Update")
    void atomicConditionalUpdate_preventsOverselling() throws InterruptedException {
        int numberOfBuyers = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfBuyers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfBuyers);
        AtomicInteger successfulPurchases = new AtomicInteger(0);
        AtomicInteger failedPurchases = new AtomicInteger(0);
        TransactionTemplate tx = new TransactionTemplate(transactionManager);
        long t0 = System.currentTimeMillis();

        for (int i = 0; i < numberOfBuyers; i++) {
            executor.submit(() -> {
                try {
                    startGate.await();
                    Boolean purchaseSucceeded = tx.execute(status -> {
                        int updatedRows = productRepository.decrementStockSafely(testProductId, 1);
                        return updatedRows > 0;
                    });
                    if (Boolean.TRUE.equals(purchaseSucceeded)) {
                        successfulPurchases.incrementAndGet();
                    } else {
                        failedPurchases.incrementAndGet();
                    }
                } catch (Exception e) {
                    failedPurchases.incrementAndGet();
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        endGate.await();
        executor.shutdown();
        Product finalProduct = productRepository.findById(testProductId).orElseThrow();
        boolean passed = successfulPurchases.get() == 10 && failedPurchases.get() == 40 && finalProduct.getQuantity() == 0;
        BenchmarkReport.record("atomic-decrement", passed, System.currentTimeMillis() - t0, 10, finalProduct.getQuantity(), successfulPurchases.get(), failedPurchases.get());
        assertThat(successfulPurchases.get()).isEqualTo(10);
        assertThat(failedPurchases.get()).isEqualTo(40);
        assertThat(finalProduct.getQuantity()).isEqualTo(0);
    }
}
