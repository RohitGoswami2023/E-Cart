package com.ecommerce.project.model;

/**
 * Payment Domain Entity
 *
 * Why & Where it is used:
 *   Represents the financial settlement record linked to an Order in table `payments`.
 *   Stores gateway transaction identifier (`pgPaymentId`), payment status, gateway response, and provider name.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @Index, @OneToOne, @Column(unique = true).
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - PaymentRepository, OrderServiceImpl, StripeWebhookController.
 *
 * Project Impact:
 *   Enforces uniqueness on `pgPaymentId` at the database index level, making it physically impossible
 *   to replay or duplicate a payment gateway transaction across separate orders.
 */

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payments_pg_payment_id", columnList = "pgPaymentId", unique = false)
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long paymentId;

    @OneToOne(mappedBy = "payment", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private Order order;

    @NotBlank
    @Size(min = 3, message = "Payment method must contain at least 3 characters")
    private String paymentMethod;

    @Column(unique = true)
    private String pgPaymentId;

    private String pgStatus;
    private String pgResponseMessage;

    private String pgName;

    public Payment(String paymentMethod, String pgPaymentId, String pgStatus,
                   String pgResponseMessage, String pgName) {
        this.paymentMethod = paymentMethod;
        this.pgPaymentId = pgPaymentId;
        this.pgStatus = pgStatus;
        this.pgResponseMessage = pgResponseMessage;
        this.pgName = pgName;
    }
}
