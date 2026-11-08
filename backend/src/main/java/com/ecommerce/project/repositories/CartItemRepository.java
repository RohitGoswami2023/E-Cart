package com.ecommerce.project.repositories;

/**
 * CartItem Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles persistence and atomic JPQL batch removals for individual cart line items in PostgreSQL table `cart_items`.
 *
 * Imports & Key Dependencies:
 *   - Domain: CartItem entity.
 *   - Spring Data JPA: JpaRepository, @Query, @Modifying, @Transactional.
 *
 * Exports & Consumers:
 *   - CartServiceImpl: Adds and updates cart lines.
 *   - OrderServiceImpl & Integration Tests: Empties cart items upon order completion without LazyInitializationException.
 *
 * Project Impact:
 *   Contains transactional modifying queries to batch-delete cart items cleanly within active database transactions.
 */

import com.ecommerce.project.model.CartItem;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    @Query("SELECT ci FROM CartItem ci WHERE ci.cart.id = ?1 AND ci.product.id = ?2")
    CartItem findCartItemByProductIdAndCartId(Long cartId, Long productId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = ?1 AND ci.product.id = ?2")
    void deleteCartItemByProductIdAndCartId(Long cartId, Long productId);

    @Modifying
    @Transactional
    @Query("DELETE FROM CartItem ci WHERE ci.cart.id = ?1")
    void deleteAllByCartId(Long cartId);
}
