package com.ecommerce.project.service;

import com.ecommerce.project.benchmark.BenchmarkReport;
import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class SagaCompensatingActionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CartRepository cartRepository;

    @Autowired
    private CartItemRepository cartItemRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    private Long testProductId;
    private final String testEmail = "saga_user@example.com";
    private Long testAddressId;

    @BeforeEach
    void setup() {
        // Find or create user
        User user = userRepository.findByEmail(testEmail).orElseGet(() -> {
            User u = new User();
            u.setUserName("sagaUser");
            u.setEmail(testEmail);
            u.setPassword("password123");
            return userRepository.save(u);
        });

        // Setup Address
        Address address = new Address();
        address.setStreet("123 Stripe Way");
        address.setBuildingName("Tower A Suite 100");
        address.setCity("Metropolis");
        address.setState("NY");
        address.setCountry("USA");
        address.setPincode("10001");
        address.setUser(user);
        address = addressRepository.save(address);
        testAddressId = address.getAddressId();

        // Setup Product with stock = 20
        Product product = new Product();
        product.setProductName("Saga Stress Product");
        product.setDescription("Reliable product test description");
        product.setImage("default.png");
        product.setPrice(100.0);
        product.setDiscount(0.0);
        product.setSpecialPrice(100.0);
        product.setQuantity(20);
        product = productRepository.save(product);
        testProductId = product.getProductId();

        // Create or clear Cart
        Cart cart = cartRepository.findCartByEmail(testEmail);
        if (cart == null) {
            cart = new Cart();
            cart.setUser(user);
            cart.setTotalPrice(200.0);
            cart = cartRepository.save(cart);
        } else {
            cart.getCartItems().clear();
            cart.setTotalPrice(200.0);
            cart = cartRepository.save(cart);
        }

        // Add 2 items to Cart
        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(2);
        item.setProductPrice(100.0);
        item.setDiscount(0.0);
        cartItemRepository.save(item);
        cart.getCartItems().add(item);
        cartRepository.save(cart);
    }

    @Test
    @DisplayName("Saga Compensation: Stock should be restored if Payment Gateway fails downstream")
    void shouldRestoreStockWhenPaymentFails() {
        long t0 = System.currentTimeMillis();
        int initialStock = productRepository.findById(testProductId).get().getQuantity();
        assertEquals(20, initialStock);

        assertThrows(APIException.class, () -> {
            orderService.placeOrder(
                    testEmail,
                    testAddressId,
                    "STRIPE",
                    "STRIPE",
                    "pi_failed_12345",
                    "FAILED",
                    "Card was declined: Insufficient funds"
            );
        });

        Product productAfterRollback = productRepository.findById(testProductId).orElseThrow();
        boolean passed = productAfterRollback.getQuantity() == 20;
        BenchmarkReport.record("saga-compensation", passed, System.currentTimeMillis() - t0, 20, productAfterRollback.getQuantity(), null, null);
        assertEquals(20, productAfterRollback.getQuantity(),
                "SAGA Compensation failed: Stock was not restored after payment gateway failure!");
    }
}

