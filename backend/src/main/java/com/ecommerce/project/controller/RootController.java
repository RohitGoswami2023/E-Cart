package com.ecommerce.project.controller;

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
