package com.ecommerce.project.concurrency;

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

/**
 * Concurrency Test Suite: Demonstrates the Overselling Problem vs the Atomic Fix.
 *
 * Scenario:
 * - A single hot product has 10 units in stock.
 * - 50 concurrent buyers attempt to purchase 1 unit at the exact same millisecond.
 *
 * Expected Outcome:
 * - Exactly 10 purchases must succeed.
 * - Exactly 40 purchases must fail.
 * - Stock must end at exactly 0 (NEVER negative).
 */
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
        product.setQuantity(10); // Exactly 10 units available
        product.setPrice(120000.0);
        product.setDiscount(0.0);
        product.setSpecialPrice(110000.0);
        Product saved = productRepository.save(product);
        testProductId = saved.getProductId();
    }

    /**
     * Approach 1: Naive Read-Modify-Write
     * Simulates what OrderServiceImpl lines 98-106 currently do.
     * FAILS: Under concurrency, multiple threads read the same quantity before writing back.
     */
    @Test
    @DisplayName("Naive Read-Modify-Write FAILS: Demonstrates Overselling Under Concurrency")
    void naiveCheckout_causesOverselling() throws InterruptedException {
        int numberOfBuyers = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfBuyers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfBuyers);

        AtomicInteger successfulPurchases = new AtomicInteger(0);
        AtomicInteger failedPurchases = new AtomicInteger(0);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        for (int i = 0; i < numberOfBuyers; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Wait for all threads to be ready (fire simultaneously)

                    // Execute naive read-modify-write in its own transaction
                    Boolean purchaseSucceeded = tx.execute(status -> {
                        Product p = productRepository.findById(testProductId).orElseThrow();
                        if (p.getQuantity() > 0) {
                            try {
                                // Small sleep to simulate typical checkout processing time (e.g. order item creation)
                                Thread.sleep(10);
                            } catch (InterruptedException ignored) {}

                            p.setQuantity(p.getQuantity() - 1);
                            productRepository.save(p);
                            return true;
                        }
                        return false;
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

        startGate.countDown(); // FIRE ALL THREADS SIMULTaneously
        endGate.await();
        executor.shutdown();

        Product finalProduct = productRepository.findById(testProductId).orElseThrow();
        System.out.println("=== NAIVE APPROACH RESULTS ===");
        System.out.println("Successful Purchases: " + successfulPurchases.get());
        System.out.println("Failed Purchases: " + failedPurchases.get());
        System.out.println("Ending Stock: " + finalProduct.getQuantity());

        // This assertion PROVES the bug: more than 10 purchases succeeded, or stock is negative!
        // In the naive approach, successfulPurchases is typically 20-50 instead of 10.
        assertThat(successfulPurchases.get())
                .as("Naive approach should suffer from race condition overselling")
                .isGreaterThan(10);
    }

    /**
     * Approach 2: Atomic Conditional SQL Update
     * Demonstrates the solution: UPDATE products SET quantity = quantity - 1 WHERE id = :id AND quantity >= 1
     * PASSES: Exactly 10 succeed, exactly 40 fail, ending stock is exactly 0.
     */
    @Test
    @DisplayName("Atomic Conditional Update PASSES: Zero Overselling Under 50 Concurrent Buyers")
    void atomicConditionalUpdate_preventsOverselling() throws InterruptedException {
        int numberOfBuyers = 50;
        ExecutorService executor = Executors.newFixedThreadPool(numberOfBuyers);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(numberOfBuyers);

        AtomicInteger successfulPurchases = new AtomicInteger(0);
        AtomicInteger failedPurchases = new AtomicInteger(0);

        TransactionTemplate tx = new TransactionTemplate(transactionManager);

        for (int i = 0; i < numberOfBuyers; i++) {
            executor.submit(() -> {
                try {
                    startGate.await(); // Simultaneous burst

                    // Execute atomic decrement inside transaction
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

        startGate.countDown(); // FIRE
        endGate.await();
        executor.shutdown();

        Product finalProduct = productRepository.findById(testProductId).orElseThrow();
        System.out.println("=== ATOMIC CONDITIONAL UPDATE RESULTS ===");
        System.out.println("Successful Purchases: " + successfulPurchases.get());
        System.out.println("Failed Purchases: " + failedPurchases.get());
        System.out.println("Ending Stock: " + finalProduct.getQuantity());

        // STRICT ACCURACY ASSERTIONS:
        assertThat(successfulPurchases.get())
                .as("Exactly 10 purchases must succeed for 10 units in stock")
                .isEqualTo(10);

        assertThat(failedPurchases.get())
                .as("Exactly 40 buyers must be rejected cleanly")
                .isEqualTo(40);

        assertThat(finalProduct.getQuantity())
                .as("Ending stock must be exactly 0, never negative")
                .isEqualTo(0);
    }
}

