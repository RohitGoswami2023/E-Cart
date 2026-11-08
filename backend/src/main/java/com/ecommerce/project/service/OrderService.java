package com.ecommerce.project.service;

/**
 * Order Service Interface
 *
 * Why & Where it is used:
 *   Specifies the contract for order placement, lifecycle status transitions, order history querying,
 *   and role-segregated seller/admin order modifications.
 *
 * Imports & Key Dependencies:
 *   - Payloads: OrderDTO, OrderResponse.
 *   - Transaction Management: Jakarta @Transactional annotations.
 *
 * Exports & Consumers:
 *   - Implemented by OrderServiceImpl.
 *   - Injected into OrderController and checkout integration tests.
 *
 * Project Impact:
 *   Defines the contract for monetary checkout, stock reservation, and distributed saga compensation.
 */

import com.ecommerce.project.payload.OrderDTO;
import com.ecommerce.project.payload.OrderResponse;
import jakarta.transaction.Transactional;

public interface OrderService {
    @Transactional
    OrderDTO placeOrder(String emailId, Long addressId, String paymentMethod, String pgName, String pgPaymentId, String pgStatus, String pgResponseMessage);

    OrderResponse getAllOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO updateOrder(Long orderId, String status);

    OrderDTO updateSellerOrder(Long orderId, String status);

    OrderResponse getAllSellerOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderResponse getUserOrders(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    OrderDTO getUserOrder(Long orderId);
}
