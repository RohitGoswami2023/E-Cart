package com.ecommerce.project.repositories;

/**
 * Cart Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database operations for the primary shopping Cart entity in PostgreSQL table `carts`.
 *   Includes JPQL fetch joins to eager-load cart items and associated product records.
 *
 * Imports & Key Dependencies:
 *   - Domain: Cart entity.
 *   - Spring Data JPA: JpaRepository, @Query.
 *
 * Exports & Consumers:
 *   - CartServiceImpl: Used to retrieve active user carts and validate cart ownership.
 *   - OrderController & OrderServiceImpl: Pre-validates total amounts before calling Stripe.
 *
 * Project Impact:
 *   Enforces shopping cart isolation per user account and facilitates cascade clearing on checkout.
 */

import com.ecommerce.project.model.Cart;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface CartRepository extends JpaRepository<Cart, Long> {
    @Query("SELECT c FROM Cart c WHERE c.user.email = ?1")
    Cart findCartByEmail(String email);

    @Query("SELECT c FROM Cart c WHERE c.user.email = ?1 AND c.id = ?2")
    Cart findCartByEmailAndCartId(String emailId, Long cartId);

    @Query("SELECT c FROM Cart c JOIN FETCH c.cartItems ci JOIN FETCH ci.product p WHERE p.id = ?1")
    List<Cart> findCartsByProductId(Long productId);
}
