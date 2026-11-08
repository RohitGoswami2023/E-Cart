package com.ecommerce.project.controller;

/**
 * Root Service REST Controller
 *
 * Why & Where it is used:
 *   Handles GET requests to the root path (`/`) of the backend server.
 *   Provides developers and consumers with immediate links to API documentation,
 *   public catalogue endpoints, and the frontend web application.
 *
 * Imports & Key Dependencies:
 *   - Spring Web: RestController, GetMapping, ResponseEntity.
 *   - Java Util: Map.of for immutable metadata key-value representation.
 *
 * Exports & Consumers:
 *   - Direct browser visits to http://localhost:8080/
 *
 * Project Impact:
 *   Eliminates default 401/404 errors on root backend navigation by offering a clean,
 *   informative entry point to the system's public resources and Swagger UI.
 */

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RootController {

    @GetMapping("/")
    public ResponseEntity<?> getRootInfo() {
        return ResponseEntity.ok(Map.of(
                "service", "E-Cart Backend API",
                "status", "UP",
                "swaggerUI", "/swagger-ui/index.html",
                "apiDocs", "/v3/api-docs",
                "publicCategories", "/api/public/categories",
                "publicProducts", "/api/public/products",
                "frontendUrl", "http://localhost:5173"
        ));
    }
}

