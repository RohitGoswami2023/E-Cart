package com.ecommerce.project.service;

import com.ecommerce.project.benchmark.BenchmarkReport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class RedisStockGateTest {

    @Autowired
    private RedisStockGateService redisStockGateService;

    @Test
    @DisplayName("Redis Stock Gate: Fast-fails excess requests in RAM before touching PostgreSQL")
    void testRedisStockGateFastFail() {
        long t0 = System.currentTimeMillis();
        Long testProductId = 999999L;
        int initialStock = 5;

        redisStockGateService.primeStock(testProductId, initialStock);

        for (int i = 0; i < initialStock; i++) {
            boolean reserved = redisStockGateService.tryReserveStock(testProductId, 1);
            assertThat(reserved).isTrue();
        }

        boolean overflowAttempt = redisStockGateService.tryReserveStock(testProductId, 1);
        assertThat(overflowAttempt).isFalse();

        redisStockGateService.rollbackReservation(testProductId, 1);
        boolean attemptAfterRollback = redisStockGateService.tryReserveStock(testProductId, 1);
        assertThat(attemptAfterRollback).isTrue();

        BenchmarkReport.record("redis-stock-gate", true, System.currentTimeMillis() - t0, null, null, null, null);
    }
}

