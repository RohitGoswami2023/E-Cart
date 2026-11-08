package com.ecommerce.project.service;

/**
 * Category Service Interface
 *
 * Why & Where it is used:
 *   Specifies business methods for product taxonomy management: paginated category browsing,
 *   new category creation, updates, and cascading category deletion.
 *
 * Imports & Key Dependencies:
 *   - Payloads: CategoryDTO, CategoryResponse.
 *
 * Exports & Consumers:
 *   - Implemented by CategoryServiceImpl.
 *   - Injected into CategoryController.
 *
 * Project Impact:
 *   Standardizes catalogue taxonomy access across administrative and public storefront channels.
 */

import com.ecommerce.project.payload.CategoryDTO;
import com.ecommerce.project.payload.CategoryResponse;

public interface CategoryService {
    CategoryResponse getAllCategories(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
    CategoryDTO createCategory(CategoryDTO categoryDTO);

    CategoryDTO deleteCategory(Long categoryId);

    CategoryDTO updateCategory(CategoryDTO categoryDTO, Long categoryId);
}
