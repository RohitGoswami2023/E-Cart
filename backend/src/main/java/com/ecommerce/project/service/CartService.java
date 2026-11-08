package com.ecommerce.project.service;

/**
 * Shopping Cart Service Interface
 *
 * Why & Where it is used:
 *   Specifies business methods for customer shopping carts: inserting items, updating quantities,
 *   retrieving cart states, and synchronizing cart items across sessions.
 *
 * Imports & Key Dependencies:
 *   - Payloads: CartDTO, CartItemDTO.
 *   - Transaction Management: Jakarta @Transactional annotations.
 *
 * Exports & Consumers:
 *   - Implemented by CartServiceImpl.
 *   - Injected into CartController and OrderController.
 *
 * Project Impact:
 *   Defines the contract for shopping cart state isolation and quantity management.
 */

import com.ecommerce.project.payload.CartDTO;
import com.ecommerce.project.payload.CartItemDTO;
import jakarta.transaction.Transactional;

import java.util.List;

public interface CartService {
    CartDTO addProductToCart(Long productId, Integer quantity);

    List<CartDTO> getAllCarts();

    CartDTO getCart(String emailId, Long cartId);

    @Transactional
    CartDTO updateProductQuantityInCart(Long productId, Integer quantity);

    String deleteProductFromCart(Long cartId, Long productId);

    void clearProductFromCart(Long cartId, Long productId);

    void updateProductInCarts(Long cartId, Long productId);

    String createOrUpdateCartWithItems(List<CartItemDTO> cartItems);
}
