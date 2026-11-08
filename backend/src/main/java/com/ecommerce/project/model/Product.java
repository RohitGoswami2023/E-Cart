package com.ecommerce.project.model;

/**
 * Product Domain Entity
 *
 * Why & Where it is used:
 *   Represents an eCommerce catalog item in PostgreSQL table `products`.
 *   Stores product title, description, image asset path, available quantity stock, base price,
 *   discount rate, calculated special price, category taxonomy, and owner seller user.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Table, @ManyToOne relationships to Category and User (seller).
 *   - Jakarta Validation: @NotBlank, @Size.
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor, @ToString.
 *
 * Exports & Consumers:
 *   - ProductRepository, ProductServiceImpl, ProductController, CartServiceImpl, OrderServiceImpl.
 *   - Concurrency & Flash Sale Tests: Subject of high-contention row-level lock testing.
 *
 * Project Impact:
 *   The core entity for inventory. Stock decrements directly evaluate against this table's `quantity` column.
 */

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.util.ArrayList;
import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table(name = "products")
@ToString
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long productId;

    @NotBlank
    @Size(min = 3, message = "Product name must contain atleast 3 characters")
    private String productName;
    private String image;

    @NotBlank
    @Size(min = 6, message = "Product description must contain atleast 6 characters")
    private String description;
    private Integer quantity;
    private double price;
    private double discount;
    private double specialPrice;

    @ManyToOne
    @JoinColumn(name = "category_id")
    private Category category;

    @ManyToOne
    @JoinColumn(name = "seller_id")
    private User user;

    @OneToMany(mappedBy = "product", cascade = {CascadeType.PERSIST, CascadeType.MERGE}, fetch = FetchType.EAGER)
    private List<CartItem> products = new ArrayList<>();
}
