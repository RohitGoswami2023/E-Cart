package com.ecommerce.project.model;

/**
 * Order Domain Entity
 *
 * Why & Where it is used:
 *   Represents a customer purchase order in PostgreSQL table `orders`.
 *   Stores buyer email, order line items, transaction date, associated payment record, total amount,
 *   lifecycle status (e.g. Order Confirmed, Payment Failed, Cancelled), and shipping destination address.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @OneToMany, @OneToOne, @ManyToOne.
 *   - Jakarta Validation: @Email constraint.
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - OrderRepository, OrderServiceImpl, OrderController, StripeWebhookController.
 *   - Concurrency & Benchmark tests: Core target of flash sale stress testing.
 *
 * Project Impact:
 *   Central financial record of purchases. Connected to inventory tracking and payment settlement.
 */

import jakarta.persistence.*;
import jakarta.validation.constraints.Email;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Order {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderId;

    @Email
    @Column(nullable = false)
    private String email;

    @OneToMany(mappedBy = "order", cascade = { CascadeType.PERSIST, CascadeType.MERGE })
    private List<OrderItem> orderItems = new ArrayList<>();

    private LocalDate orderDate;

    @OneToOne
    @JoinColumn(name = "payment_id")
    private Payment payment;

    private Double totalAmount;
    private String orderStatus;

    @ManyToOne
    @JoinColumn(name = "address_id")
    private Address address;
}
