package com.ecommerce.project.model;

/**
 * OrderItem Domain Entity
 *
 * Why & Where it is used:
 *   Represents an immutable snapshot of a purchased product inside an order in table `order_items`.
 *   Captures the exact ordered product price and discount at purchase time to protect against future price changes.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @ManyToOne relationships to Product and Order.
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - OrderItemRepository, OrderServiceImpl, OrderResponse payloads.
 *
 * Project Impact:
 *   Preserves historical purchase integrity: changes to a catalog product's price never alter past completed order records.
 */

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "order_items")
@AllArgsConstructor
@NoArgsConstructor
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long orderItemId;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    @ManyToOne
    @JoinColumn(name = "order_id")
    private Order order;

    private Integer quantity;
    private double discount;
    private double orderedProductPrice;

}