package com.ecommerce.project.repositories;

/**
 * Category Spring Data JPA Repository
 *
 * Why & Where it is used:
 *   Handles database operations for catalog categories in PostgreSQL table `categories`.
 *
 * Imports & Key Dependencies:
 *   - Domain: Category entity.
 *   - Spring Data JPA: JpaRepository.
 *
 * Exports & Consumers:
 *   - CategoryServiceImpl: Queries categories and verifies category name uniqueness.
 *   - ProductServiceImpl: Attaches category records when adding products to inventory.
 *
 * Project Impact:
 *   Serves as the persistence backbone for the platform's product classification taxonomy.
 */

import com.ecommerce.project.model.Category;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category,Long> {
    Category findByCategoryName(String categoryName);
}
