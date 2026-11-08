package com.ecommerce.project.service;

/**
 * Analytics Service Implementation
 *
 * Why & Where it is used:
 *   Gathers platform analytics across tables (product catalog size, order volumes, and gross revenue),
 *   excluding cancelled or refunded orders from revenue calculations.
 *
 * Imports & Key Dependencies:
 *   - Repositories: ProductRepository (count), OrderRepository (count, getTotalRevenue excluding Cancelled).
 *   - Payloads: AnalyticsResponse.
 *
 * Exports & Consumers:
 *   - Injected into AnalyticsController (`/api/admin/app/analytics`).
 *
 * Project Impact:
 *   Ensures store revenue calculations accurately reflect finalized income rather than counting aborted orders.
 */

import com.ecommerce.project.payload.AnalyticsResponse;
import com.ecommerce.project.repositories.OrderRepository;
import com.ecommerce.project.repositories.ProductRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsServiceImpl implements AnalyticsService{

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Override
    public AnalyticsResponse getAnalyticsData() {
        AnalyticsResponse response = new AnalyticsResponse();

        long productCount = productRepository.count();
        long totalOrders = orderRepository.count();;
        Double totalRevenue = orderRepository.getTotalRevenue();

        response.setProductCount(String.valueOf(productCount));
        response.setTotalOrders(String.valueOf(totalOrders));
        response.setTotalRevenue(String.valueOf(totalRevenue != null ? totalRevenue : 0));
        return response;
    }
}
