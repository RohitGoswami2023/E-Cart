package com.ecommerce.project.controller;

/**
 * Analytics REST Controller
 *
 * Why & Where it is used:
 *   Provides administrative dashboard aggregations including total revenue, order counts,
 *   customer volume, and catalog inventory metrics.
 *
 * Imports & Key Dependencies:
 *   - Service: AnalyticsService (queries summary statistics across database tables).
 *   - Payloads: AnalyticsResponse (contains high-level counts and financial rollups).
 *
 * Exports & Consumers:
 *   - Admin Dashboard View: Consumed by administrative reporting tools and executive dashboards.
 *
 * Project Impact:
 *   Gives store administrators real-time visibility into overall platform volume and gross sales performance.
 */

import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.service.AnalyticsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class AnalyticsController {

    @Autowired
    private AnalyticsService analyticsService;

    @GetMapping("/admin/app/analytics")
    public ResponseEntity<AnalyticsResponse> getAnalytics() {
        AnalyticsResponse response = analyticsService.getAnalyticsData();
        return new ResponseEntity<AnalyticsResponse>(response, HttpStatus.OK);
    }
}
