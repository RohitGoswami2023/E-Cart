package com.ecommerce.project.service;

/**
 * Saga Compensating Action Integration Test
 *
 * Why & Where it is used:
 *   Validates distributed compensating transactions under failure conditions:
 *   1) Restoring decremented inventory when downstream payment fails.
 *   2) Triggering an automated Stripe refund when local database operations fail post-payment.
 *
 * Imports & Key Dependencies:
 *   - Services: OrderService, FakeStripeService (configured via FakeStripeTestConfig).
 *   - Repositories: ProductRepository, CartRepository, CartItemRepository, UserRepository, AddressRepository.
 *   - Benchmark: BenchmarkReport (records execution durations and stock conservation metrics).
 *
 * Exports & Consumers:
 *   - Executed via `mvnw test "-Dtest=SagaCompensatingActionTest"`.
 *   - Exports evidence metrics directly into `benchmark-report.json`.
 *
 * Project Impact:
 *   Guarantees that database rollback and remote payment gateway actions remain eventually consistent,
 *   proving that neither customer money nor merchant stock is leaked during system partial failures.
 */

import com.ecommerce.project.benchmark.BenchmarkReport;
import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.repositories.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
@Import(FakeStripeTestConfig.class)
public class SagaCompensatingActionTest {

    @Autowired
    private OrderService orderService;

    @Autowired
    private FakeStripeService fakeStripeService;

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
    @Transactional
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
        if (cart != null) {
            cartItemRepository.deleteAllByCartId(cart.getCartId());
            cart.setTotalPrice(200.0);
            cart = cartRepository.save(cart);
        } else {
            cart = new Cart();
            cart.setUser(user);
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

        // Register failed intent in fake stripe service
        fakeStripeService.registerIntent("pi_failed_12345", "requires_payment_method", 20000L, "usd", testEmail);

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

    @Test
    @DisplayName("Saga Compensation: Refund issued if stock reservation fails after payment charged")
    void shouldRefundWhenStockFailsAfterPayment() {
        // Drain product stock to 0 to force stock reservation failure
        Product p = productRepository.findById(testProductId).orElseThrow();
        p.setQuantity(0);
        productRepository.save(p);

        String paymentId = "pi_succeeded_but_nostock";
        fakeStripeService.registerIntent(paymentId, "succeeded", 20000L, "usd", testEmail);

        assertThrows(APIException.class, () -> {
            orderService.placeOrder(
                    testEmail,
                    testAddressId,
                    "STRIPE",
                    "STRIPE",
                    paymentId,
                    "COMPLETED",
                    "Charge succeeded"
            );
        });

        assertTrue(fakeStripeService.wasRefunded(paymentId),
                "SAGA Compensating Action failed: Refund was not issued when stock reservation failed!");
    }
}

