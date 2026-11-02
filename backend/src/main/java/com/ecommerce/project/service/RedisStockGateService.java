package com.ecommerce.project.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

/**
 * Redis Stock Gate Service (Coordinates Inventory between Redis and PostgreSQL).
 *
 * System Design Purpose:
 * In high-scale flash sales (e.g. Flipkart Big Billion Days), 100,000 requests/sec
 * hitting PostgreSQL directly causes connection pool exhaustion and database locks.
 *
 * This Redis Stock Gate acts as an in-memory protective shield:
 * 1. Synchronous atomic DECR in Redis: Decr happens in RAM in O(1) time.
 * 2. If Redis stock goes < 0: Fast-fail immediately in Redis! 0 PostgreSQL load!
 * 3. If Redis stock >= 0: Pass through to PostgreSQL to execute durable ACID commit.
 * 4. Compensation: If downstream PG or Stripe fails, atomic INCR in Redis restores the reservation.
 */
@Service
public class RedisStockGateService {

    private static final Logger log = LoggerFactory.getLogger(RedisStockGateService.class);

    @Value("${upstash.redis.rest.url}")
    private String redisRestUrl;

    @Value("${upstash.redis.rest.token}")
    private String redisRestToken;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * Initializes hot product stock in Redis before the flash sale goes live.
     */
    public void primeStock(Long productId, int initialStock) {
        try {
            String url = redisRestUrl + "/set/stock:product:" + productId + "/" + initialStock;
            executeRedisCommand(url, HttpMethod.POST);
            log.info("Redis Gate: Primed product {} with stock = {}", productId, initialStock);
        } catch (Exception e) {
            log.warn("Redis Gate: Priming failed for product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Attempts atomic stock reservation in Redis.
     *
     * @return true if reserved successfully in RAM, false if completely sold out
     */
    public boolean tryReserveStock(Long productId, int qty) {
        try {
            // Atomic DECRBY in Redis
            String url = redisRestUrl + "/decrby/stock:product:" + productId + "/" + qty;
            Long remaining = executeRedisCommand(url, HttpMethod.POST);

            if (remaining == null) {
                // If Redis key doesn't exist, allow pass-through to let PostgreSQL validate
                return true;
            }

            if (remaining < 0) {
                // Out of stock in RAM! Compensate back and reject immediately without touching PostgreSQL!
                rollbackReservation(productId, qty);
                return false;
            }

            return true; // Successfully gated in Redis!
        } catch (Exception e) {
            log.warn("Redis Gate: Failed to query Redis, failing open to PostgreSQL: {}", e.getMessage());
            return true; // Graceful degradation: If Redis is unavailable, let DB handle via row locks
        }
    }

    /**
     * Compensating action: Restores stock in Redis if DB commit or Stripe payment fails downstream.
     */
    public void rollbackReservation(Long productId, int qty) {
        try {
            String url = redisRestUrl + "/incrby/stock:product:" + productId + "/" + qty;
            executeRedisCommand(url, HttpMethod.POST);
            log.info("Redis Gate: Compensated/restored {} units for product {}", qty, productId);
        } catch (Exception e) {
            log.warn("Redis Gate: Rollback failed for product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Checks current stock in Redis without mutating.
     */
    public Integer getStock(Long productId) {
        try {
            String url = redisRestUrl + "/get/stock:product:" + productId;
            Long val = executeRedisCommand(url, HttpMethod.GET);
            return val != null ? val.intValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private Long executeRedisCommand(String url, HttpMethod method) {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "Bearer " + redisRestToken);
        HttpEntity<String> entity = new HttpEntity<>(headers);

        ResponseEntity<String> response = restTemplate.exchange(url, method, entity, String.class);
        if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
            try {
                JsonNode root = objectMapper.readTree(response.getBody());
                JsonNode resultNode = root.get("result");
                if (resultNode != null && !resultNode.isNull()) {
                    if (resultNode.isNumber()) {
                        return resultNode.asLong();
                    } else if (resultNode.isTextual() && !"OK".equalsIgnoreCase(resultNode.asText())) {
                        return Long.parseLong(resultNode.asText());
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return null;
    }
}

