package com.ecommerce.project.model;

/**
 * Processed Stripe Event Entity (Webhook Idempotency Record)
 *
 * Why & Where it is used:
 *   Maintains an immutable record of Stripe event IDs in table `processed_stripe_events` to provide
 *   guaranteed idempotency across distributed webhook dispatches.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @Index(unique = true), @Column(unique = true).
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - ProcessedStripeEventRepository, StripeWebhookController.
 *
 * Project Impact:
 *   Guarantees at-most-once execution for webhook side-effects (e.g. order acceptance or inventory compensation).
 */

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "processed_stripe_events", indexes = {
        @Index(name = "idx_processed_event_id", columnList = "eventId", unique = true)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ProcessedStripeEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 120)
    private String eventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false)
    private Instant processedAt;

    public ProcessedStripeEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }
}

