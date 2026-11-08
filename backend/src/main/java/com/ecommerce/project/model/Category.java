package com.ecommerce.project.model;

/**
 * Category Domain Entity
 *
 * Why & Where it is used:
 *   Represents a product taxonomy category in PostgreSQL table `categories`.
 *   Maintains a 1-to-many relationship with catalog products.
 *
 * Imports & Key Dependencies:
 *   - Jakarta Persistence: @Entity, @Id, @GeneratedValue, @OneToMany.
 *   - Jakarta Validation: @NotBlank, @Size.
 *   - Lombok: @Data, @NoArgsConstructor, @AllArgsConstructor.
 *
 * Exports & Consumers:
 *   - CategoryRepository, CategoryServiceImpl, ProductServiceImpl.
 *
 * Project Impact:
 *   Structures and classifies the eCommerce merchandise catalog for store navigation.
 */

import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity(name = "categories")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Category {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long categoryId;

    @NotBlank
    @Size(min = 5, message = "Category name must contain atleast 5 characters")
    private String categoryName;

    @OneToMany(mappedBy = "category", cascade = CascadeType.ALL)
    private List<Product> products;
}
