package com.ecommerce.project.service;

/**
 * Analytics Service Interface
 *
 * Why & Where it is used:
 *   Defines methods for compiling high-level store health, revenue, and inventory analytics.
 *
 * Imports & Key Dependencies:
 *   - Payloads: AnalyticsResponse.
 *
 * Exports & Consumers:
 *   - Implemented by AnalyticsServiceImpl.
 *   - Injected into AnalyticsController.
 *
 * Project Impact:
 *   Standardizes reporting data structures for administrative insights.
 */

import com.ecommerce.project.payload.AnalyticsResponse;

public interface AnalyticsService {
    AnalyticsResponse getAnalyticsData();
}
