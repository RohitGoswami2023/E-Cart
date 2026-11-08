package com.ecommerce.project.controller;

/**
 * Health Check REST Controller
 *
 * Why & Where it is used:
 *   Provides an unauthenticated, lightweight liveness ping endpoint (`/api/public/health`)
 *   for infrastructure monitors, load balancers, and frontend connectivity indicators.
 *
 * Imports & Key Dependencies:
 *   - Java Time: Instant (provides current UTC timestamp).
 *   - Spring Web: ResponseEntity, RestController.
 *
 * Exports & Consumers:
 *   - Frontend Application Header: Powers the real-time "backend: up" status badge in `App.jsx`.
 *   - Cloud Health Probes: Can be targeted by Kubernetes or cloud load balancers.
 *
 * Project Impact:
 *   Allows instantaneous uptime verification without triggering database queries or security overhead.
 */

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class HealthController {

    @GetMapping("/public/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "sb-ecom",
                "time", Instant.now().toString()
        ));
    }
}
