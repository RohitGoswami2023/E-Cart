package com.ecommerce.project.repositories;

/**
 * OrderItem Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database CRUD operations for line items attached to customer orders in table `order_items`.
 *
 * Imports & Key Dependencies:
 *   - Domain: OrderItem entity.
 *   - Spring Data JPA: JpaRepository.
 *
 * Exports & Consumers:
 *   - OrderServiceImpl: Saves purchased item snapshots (price, quantity, product link) on order creation.
 *
 * Project Impact:
 *   Persists the exact historical line-item snapshot of every purchased item at transaction time.
 */

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecommerce.project.model.OrderItem;

@Repository
public interface OrderItemRepository extends JpaRepository<OrderItem, Long> {

}