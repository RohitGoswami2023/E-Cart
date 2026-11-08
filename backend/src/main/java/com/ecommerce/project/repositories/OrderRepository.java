package com.ecommerce.project.repositories;

/**
 * Order Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database operations for customer orders in PostgreSQL table `orders`.
 *   Includes custom JPQL aggregations for gross revenue (excluding cancelled/failed orders)
 *   and role-segregated paginated queries for sellers and customer accounts.
 *
 * Imports & Key Dependencies:
 *   - Domain: Order entity.
 *   - Spring Data: JpaRepository, Page, Pageable, @Query, @Param.
 *
 * Exports & Consumers:
 *   - OrderServiceImpl: Saves new orders, transitions statuses, and queries order history.
 *   - AnalyticsServiceImpl: Calculates platform gross revenue.
 *   - StripeWebhookController: Retrieves orders associated with external payment IDs.
 *
 * Project Impact:
 *   Central ledger for orders; guarantees that cancelled or failed transactions do not pollute financial totals.
 */

import com.ecommerce.project.model.Order;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrderRepository extends JpaRepository<Order, Long> {

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus NOT IN ('Payment Failed', 'Cancelled')")
    Double getTotalRevenue();

    Page<Order> findByEmail(String email, Pageable pageable);

    Optional<Order> findByOrderIdAndEmail(Long orderId, String email);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.orderItems i JOIN i.product p WHERE p.user.userId = :sellerId")
    Page<Order> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);
}
