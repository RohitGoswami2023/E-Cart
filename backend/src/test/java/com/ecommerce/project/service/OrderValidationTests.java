package com.ecommerce.project.service;

import com.ecommerce.project.benchmark.BenchmarkReport;
import com.ecommerce.project.exceptions.APIException;
import com.ecommerce.project.model.*;
import com.ecommerce.project.repositories.*;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest
@ActiveProfiles("test")
public class OrderValidationTests {

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

    @Autowired
    private StripeService stripeService;

    private Long testProductId;
    private final String testEmail = "order_val@test.com";
    private Long testAddressId;

    @BeforeEach
    void setup() {
        User user = userRepository.findByEmail(testEmail).orElseGet(() -> {
            User u = new User();
            u.setUserName("orderValUser");
            u.setEmail(testEmail);
            u.setPassword("password123");
            return userRepository.save(u);
        });

        Address address = new Address();
        address.setStreet("123 Order St");
        address.setBuildingName("Suite 100");
        address.setCity("Order City");
        address.setState("OC");
        address.setCountry("OC");
        address.setPincode("12345");
        address.setUser(user);
        address = addressRepository.save(address);
        testAddressId = address.getAddressId();

        Product product = new Product();
        product.setProductName("Order Validation Product");
        product.setDescription("Test");
        product.setImage("test.png");
        product.setPrice(100.0);
        product.setSpecialPrice(100.0);
        product.setDiscount(0.0);
        product.setQuantity(10);
        product = productRepository.save(product);
        testProductId = product.getProductId();

        Cart cart = cartRepository.findCartByEmail(testEmail);
        if (cart == null) {
            cart = new Cart();
            cart.setUser(user);
            cart.setTotalPrice(100.0);
            cart = cartRepository.save(cart);
        } else {
            cart.getCartItems().clear();
            cart.setTotalPrice(100.0);
            cart = cartRepository.save(cart);
        }

        CartItem item = new CartItem();
        item.setCart(cart);
        item.setProduct(product);
        item.setQuantity(1);
        item.setProductPrice(100.0);
        item.setDiscount(0.0);
        cartItemRepository.save(item);
        cart.getCartItems().add(item);
        cartRepository.save(cart);
    }

    @Test
    @DisplayName("Client pgStatus=succeeded with bad PI id rejected")
    void badPaymentIntentIdRejected() {
        long t0 = System.currentTimeMillis();
        assertThrows(APIException.class, () -> {
            orderService.placeOrder(
                    testEmail,
                    testAddressId,
                    "STRIPE",
                    "STRIPE",
                    "pi_bad_12345",
                    "succeeded",
                    "fake success"
            );
        });
        BenchmarkReport.record("order-bad-pi-id", true, System.currentTimeMillis() - t0, null, null, null, null);
    }

    @Test
    @DisplayName("Amount mismatch rejected")
    @Transactional
    void amountMismatchRejected() throws StripeException {
        PaymentIntent intent = stripeService.paymentIntent(null);
        intent.setId("pi_test_amount");
        long t0 = System.currentTimeMillis();
        assertThrows(APIException.class, () -> {
            orderService.placeOrder(
                    testEmail,
                    testAddressId,
                    "STRIPE",
                    "STRIPE",
                    "pi_test_amount",
                    "succeeded",
                    "test"
            );
        });
        BenchmarkReport.record("order-amount-mismatch", true, System.currentTimeMillis() - t0, null, null, null, null);
    }

    @Test
    @DisplayName("Currency mismatch rejected")
    void currencyMismatchRejected() {
        long t0 = System.currentTimeMillis();
        assertThrows(APIException.class, () -> {
            orderService.placeOrder(
                    testEmail,
                    testAddressId,
                    "STRIPE",
                    "STRIPE",
                    "pi_test_currency",
                    "succeeded",
                    "test"
            );
        });
        BenchmarkReport.record("order-currency-mismatch", true, System.currentTimeMillis() - t0, null, null, null, null);
    }
}
