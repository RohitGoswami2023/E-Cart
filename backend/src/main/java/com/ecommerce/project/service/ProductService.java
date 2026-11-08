package com.ecommerce.project.service;

/**
 * Product Catalog Service Interface
 *
 * Why & Where it is used:
 *   Specifies business methods for product inventory management: catalog browsing, multi-criteria
 *   keyword & category search, seller ownership-enforced updates, deletion, and image asset binding.
 *
 * Imports & Key Dependencies:
 *   - Payloads: ProductDTO, ProductResponse.
 *   - Spring Web: MultipartFile for media updates.
 *
 * Exports & Consumers:
 *   - Implemented by ProductServiceImpl.
 *   - Injected into ProductController.
 *
 * Project Impact:
 *   Establishes ownership-aware product lifecycle management contracts for multi-vendor eCommerce.
 */

import com.ecommerce.project.payload.ProductDTO;
import com.ecommerce.project.payload.ProductResponse;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

public interface ProductService {
    ProductDTO addProduct(Long categoryId, ProductDTO product);

    ProductDTO getProductById(Long productId);

    ProductResponse getAllProducts(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder, String keyword, String category);

    ProductResponse searchByCategory(Long categoryId, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    ProductResponse searchProductByKeyword(String keyword, Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    ProductDTO updateProduct(Long productId, ProductDTO product, boolean ownerOnly);

    ProductDTO deleteProduct(Long productId, boolean ownerOnly);

    ProductDTO updateProductImage(Long productId, MultipartFile image, boolean ownerOnly) throws IOException;

    ProductResponse getAllProductsForAdmin(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);

    ProductResponse getAllProductsForSeller(Integer pageNumber, Integer pageSize, String sortBy, String sortOrder);
}
