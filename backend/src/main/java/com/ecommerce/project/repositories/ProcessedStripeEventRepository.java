package com.ecommerce.project.repositories;

/**
 * ProcessedStripeEvent Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Acts as an idempotency ledger storing processed Stripe event IDs in table `processed_stripe_events`.
 *   Used to reject duplicate webhook deliveries from Stripe retries.
 *
 * Imports & Key Dependencies:
 *   - Domain: ProcessedStripeEvent entity.
 *   - Spring Data JPA: JpaRepository.
 *
 * Exports & Consumers:
 *   - StripeWebhookController: Checked before processing incoming webhook payloads.
 *
 * Project Impact:
 *   Prevents duplicate order fulfillment or double compensating refunds triggered by network retries.
 */

import com.ecommerce.project.model.ProcessedStripeEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedStripeEventRepository extends JpaRepository<ProcessedStripeEvent, Long> {
    boolean existsByEventId(String eventId);
}

