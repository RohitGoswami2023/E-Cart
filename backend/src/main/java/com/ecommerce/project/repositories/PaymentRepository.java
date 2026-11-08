package com.ecommerce.project.repositories;

/**
 * Payment Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database operations for payments in PostgreSQL table `payments`.
 *   Provides lookups and uniqueness checks by Stripe payment intent identifier (`pg_payment_id`).
 *
 * Imports & Key Dependencies:
 *   - Domain: Payment entity.
 *   - Spring Data JPA: JpaRepository.
 *
 * Exports & Consumers:
 *   - OrderServiceImpl: Verifies payment uniqueness to prevent payment intent replay attacks.
 *   - StripeWebhookController: Matches incoming Stripe webhooks to local payment and order records.
 *
 * Project Impact:
 *   Enforces payment replay defense at the persistence layer, stopping bad actors from reusing
 *   a single successful Stripe PaymentIntent across multiple orders.
 */

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.ecommerce.project.model.Payment;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    Optional<Payment> findByPgPaymentId(String pgPaymentId);
    boolean existsByPgPaymentId(String pgPaymentId);
}
