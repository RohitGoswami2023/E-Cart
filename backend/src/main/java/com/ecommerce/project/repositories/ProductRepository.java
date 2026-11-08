package com.ecommerce.project.repositories;

import com.ecommerce.project.model.Category;
import com.ecommerce.project.model.Product;
import com.ecommerce.project.model.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {
    Page<Product> findByCategoryOrderByPriceAsc(Category category, Pageable pageDetails);

    Page<Product> findByProductNameLikeIgnoreCase(String keyword, Pageable pageDetails);

    Page<Product> findByUser(User user, Pageable pageDetails);

    /**
     * Atomic Conditional Update to prevent overselling under concurrency.
     * Evaluates quantity check directly in PostgreSQL while holding the row-level lock.
     *
     * @return 1 if stock was decremented successfully, 0 if out of stock
     */
    @Modifying
    @Query("UPDATE Product p SET p.quantity = p.quantity - :qty " +
           "WHERE p.productId = :productId AND :qty > 0 AND p.quantity >= :qty")
    int decrementStockSafely(@Param("productId") Long productId, @Param("qty") Integer qty);

    /**
     * Compensating transaction: Increments stock back if payment or checkout saga fails.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE Product p SET p.quantity = p.quantity + :qty WHERE p.productId = :productId")
    void incrementStock(@Param("productId") Long productId, @Param("qty") Integer qty);
}
