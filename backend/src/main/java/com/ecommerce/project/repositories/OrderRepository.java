package com.ecommerce.project.repositories;

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

    @Query("SELECT COALESCE(SUM(o.totalAmount), 0) FROM Order o WHERE o.orderStatus NOT IN ('Payment Failed')")
    Double getTotalRevenue();

    Page<Order> findByEmail(String email, Pageable pageable);

    Optional<Order> findByOrderIdAndEmail(Long orderId, String email);

    @Query("SELECT DISTINCT o FROM Order o JOIN o.orderItems i JOIN i.product p WHERE p.user.userId = :sellerId")
    Page<Order> findBySellerId(@Param("sellerId") Long sellerId, Pageable pageable);
}
