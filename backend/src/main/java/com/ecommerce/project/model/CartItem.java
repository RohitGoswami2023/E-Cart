package com.ecommerce.project.model;

/**
 * CartItem Domain Entity
 *
 * Why & Where it is used:
 *   Represents an individual line item inside a customer's shopping cart in table `cart_items`.
 *   Stores quantity, discount, and product price snapshot.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @ManyToOne relationships to Cart and Product.
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - CartItemRepository, CartServiceImpl, OrderServiceImpl.
 *
 * Project Impact:
 *   Links specific catalog products and desired purchase quantities to a customer's cart.
 */

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Data
@Table(name = "cart_items")
@NoArgsConstructor
@AllArgsConstructor
public class CartItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long cartItemId;

    @ManyToOne
    @JoinColumn(name = "cart_id")
    private Cart cart;

    @ManyToOne
    @JoinColumn(name = "product_id")
    private Product product;

    private Integer quantity;
    private double discount;
    private double productPrice;
}
