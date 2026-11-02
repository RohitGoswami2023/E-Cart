package com.ecommerce.project.service;

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
        Long testProductId = 999999L;
        int initialStock = 5;

        // 1. Prime stock in Redis
        redisStockGateService.primeStock(testProductId, initialStock);

        // 2. Consume all 5 units
        for (int i = 0; i < initialStock; i++) {
            boolean reserved = redisStockGateService.tryReserveStock(testProductId, 1);
            assertThat(reserved).isTrue();
        }

        // 3. 6th attempt MUST be rejected by Redis Stock Gate immediately!
        boolean overflowAttempt = redisStockGateService.tryReserveStock(testProductId, 1);
        assertThat(overflowAttempt).isFalse();

        // 4. Test Compensation / Rollback
        redisStockGateService.rollbackReservation(testProductId, 1);
        boolean attemptAfterRollback = redisStockGateService.tryReserveStock(testProductId, 1);
        assertThat(attemptAfterRollback).isTrue();
    }
}

