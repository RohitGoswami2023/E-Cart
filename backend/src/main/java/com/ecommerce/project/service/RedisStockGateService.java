package com.ecommerce.project.service;

/**
 * Redis Stock Gate Service
 *
 * Why & Where it is used:
 *   Acts as an ultra-fast in-memory pre-filter during high-concurrency flash sales (e.g. Big Billion Days),
 *   rejecting oversell attempts before they ever hit the relational database.
 *
 * Imports & Key Dependencies:
 *   - Redis REST Client: Upstash Redis HTTP API with atomic DECRBY and INCRBY commands.
 *   - Repositories: ProductRepository (used to lazily prime initial inventory if missing from Redis).
 *   - Spring Web: RestTemplate, HttpHeaders, HttpEntity.
 *
 * Exports & Consumers:
 *   - OrderServiceImpl: Invoked during checkout to atomically claim stock.
 *   - StripeWebhookController: Increments back the Redis counter upon payment cancellation.
 *   - FlashSaleConcurrencyTest: Validated under simulated multi-threaded buyer traffic.
 *
 * Project Impact:
 *   Shields PostgreSQL from database connection exhaustion and lock queuing under traffic surges.
 *   Designed with fail-open behavior so Redis network failures degrade gracefully to database row-level locking.
 */

import com.ecommerce.project.repositories.ProductRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class RedisStockGateService {

    private static final Logger log = LoggerFactory.getLogger(RedisStockGateService.class);

    @Value("${upstash.redis.rest.url:}")
    private String redisRestUrl;

    @Value("${upstash.redis.rest.token:}")
    private String redisRestToken;

    @Autowired
    private ProductRepository productRepository;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper objectMapper = new ObjectMapper();

    public void primeStock(Long productId, int initialStock) {
        if (!configured()) {
            return;
        }
        try {
            String url = redisRestUrl + "/set/stock:product:" + productId + "/" + initialStock + "/nx";
            Long result = executeRedisCommand(url, HttpMethod.POST);
            if (result == null) {
                String overwrite = redisRestUrl + "/set/stock:product:" + productId + "/" + initialStock;
                executeRedisCommand(overwrite, HttpMethod.POST);
            }
        } catch (Exception e) {
            log.warn("Redis prime failed for product {}: {}", productId, e.getMessage());
        }
    }

    public boolean tryReserveStock(Long productId, int qty) {
        if (!configured()) {
            return true;
        }
        try {
            Integer current = getStock(productId);
            if (current == null) {
                var productOpt = productRepository.findById(productId);
                int dbStock = productOpt.isPresent() ? productOpt.get().getQuantity() : 0;
                String setUrl = redisRestUrl + "/set/stock:product:" + productId + "/" + dbStock + "/nx";
                executeRedisCommand(setUrl, HttpMethod.POST);
            }
            String url = redisRestUrl + "/decrby/stock:product:" + productId + "/" + qty;
            Long remaining = executeRedisCommand(url, HttpMethod.POST);
            if (remaining == null) {
                return true;
            }
            if (remaining < 0) {
                rollbackReservation(productId, qty);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("Redis reserve failed, fail-open: {}", e.getMessage());
            return true;
        }
    }

    public void rollbackReservation(Long productId, int qty) {
        if (!configured()) {
            return;
        }
        try {
            String url = redisRestUrl + "/incrby/stock:product:" + productId + "/" + qty;
            executeRedisCommand(url, HttpMethod.POST);
        } catch (Exception e) {
            log.warn("Redis rollback failed for product {}: {}", productId, e.getMessage());
        }
    }

    public Integer getStock(Long productId) {
        if (!configured()) {
            return null;
        }
        try {
            String url = redisRestUrl + "/get/stock:product:" + productId;
            Long val = executeRedisCommand(url, HttpMethod.GET);
            return val != null ? val.intValue() : null;
        } catch (Exception e) {
            return null;
        }
    }

    private boolean configured() {
        return redisRestUrl != null && !redisRestUrl.isBlank() && redisRestToken != null && !redisRestToken.isBlank();
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
