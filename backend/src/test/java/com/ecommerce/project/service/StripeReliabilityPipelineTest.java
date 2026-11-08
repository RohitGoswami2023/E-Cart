package com.ecommerce.project.service;

import com.ecommerce.project.benchmark.BenchmarkReport;
import com.ecommerce.project.controller.StripeWebhookController;
import com.ecommerce.project.model.*;
import com.ecommerce.project.payload.StripePaymentDto;
import com.ecommerce.project.repositories.*;
import com.stripe.model.PaymentIntent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
public class StripeReliabilityPipelineTest {

    @Autowired
    private StripeService stripeService;

    @Autowired
    private StripeWebhookController stripeWebhookController;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AddressRepository addressRepository;

    private Long testProductId;

    @BeforeEach
    void setup() {
        Product product = new Product();
        product.setProductName("Stripe Pipeline Verified Device");
        product.setDescription("Reliability test item");
        product.setImage("pipeline.png");
        product.setPrice(500.0);
        product.setSpecialPrice(500.0);
        product.setDiscount(0.0);
        product.setQuantity(10);
        product = productRepository.save(product);
        testProductId = product.getProductId();
    }

    @Test
    @DisplayName("1. Idempotency Guarantee: Same Idempotency Key returns identical PaymentIntent ID")
    void testStripeIdempotencyKey() throws Exception {
        long t0 = System.currentTimeMillis();
        String testIdempotencyKey = "test_idem_" + UUID.randomUUID();
        String uniqueCustomerEmail = "idem_" + System.currentTimeMillis() + "@example.com";

        Address testAddress = new Address();
        testAddress.setStreet("456 Market St");
        testAddress.setCity("San Francisco");
        testAddress.setState("CA");
        testAddress.setCountry("USA");
        testAddress.setPincode("94103");

        StripePaymentDto dto1 = new StripePaymentDto();
        dto1.setAmount(50000L);
        dto1.setCurrency("usd");
        dto1.setEmail(uniqueCustomerEmail);
        dto1.setName("Idem User");
        dto1.setDescription("Testing Idempotent double submit");
        dto1.setAddress(testAddress);
        dto1.setMetadata(Map.of("idempotencyKey", testIdempotencyKey));

        PaymentIntent firstIntent = stripeService.paymentIntent(dto1);
        assertThat(firstIntent).isNotNull();
        String firstId = firstIntent.getId();

        PaymentIntent secondIntent = stripeService.paymentIntent(dto1);
        assertThat(secondIntent).isNotNull();
        String secondId = secondIntent.getId();

        boolean passed = firstId.equals(secondId);
        BenchmarkReport.record("stripe-idempotency", passed, System.currentTimeMillis() - t0, null, null, null, null);
        assertThat(firstId).isEqualTo(secondId);
        System.out.println("Stripe Idempotency Verified: First ID=" + firstId + " == Second ID=" + secondId);
    }

    @Test
    @DisplayName("2. Webhook Handling & Compensation: payment_intent.payment_failed restores inventory")
    @Transactional
    void testWebhookCompensationRollback() {
        long t0 = System.currentTimeMillis();
        User user = new User();
        user.setUserName("webhookUser" + System.currentTimeMillis() % 10000);
        user.setEmail("webhook_user_" + System.currentTimeMillis() + "@example.com");
        user.setPassword("password123");
        user = userRepository.save(user);

        Address address = new Address();
        address.setStreet("789 Webhook Ave");
        address.setBuildingName("Floor 4");
        address.setCity("San Jose");
        address.setState("CA");
        address.setCountry("USA");
        address.setPincode("95112");
        address.setUser(user);
        address = addressRepository.save(address);

        Product product = productRepository.findById(testProductId).orElseThrow();
        product.setQuantity(8);
        productRepository.save(product);

        Order order = new Order();
        order.setEmail(user.getEmail());
        order.setOrderDate(LocalDate.now());
        order.setTotalAmount(1000.0);
        order.setOrderStatus("Pending Payment");
        order.setAddress(address);

        OrderItem orderItem = new OrderItem();
        orderItem.setOrder(order);
        orderItem.setProduct(product);
        orderItem.setQuantity(2);
        orderItem.setOrderedProductPrice(500.0);
        orderItem.setDiscount(0.0);
        order.getOrderItems().add(orderItem);

        order = orderRepository.save(order);

        String mockPaymentIntentId = "pi_webhook_test_" + UUID.randomUUID().toString().substring(0, 8);
        Payment payment = new Payment("STRIPE", mockPaymentIntentId, "PENDING", "Awaiting Webhook", "STRIPE");
        payment.setOrder(order);
        payment = paymentRepository.save(payment);
        order.setPayment(payment);
        order = orderRepository.save(order);

        assertThat(productRepository.findById(testProductId).get().getQuantity()).isEqualTo(8);

        String webhookJson = "{\n" +
                "  \"id\": \"evt_test_123\",\n" +
                "  \"object\": \"event\",\n" +
                "  \"api_version\": \"2024-06-20\",\n" +
                "  \"type\": \"payment_intent.payment_failed\",\n" +
                "  \"data\": {\n" +
                "    \"object\": {\n" +
                "      \"id\": \"" + mockPaymentIntentId + "\",\n" +
                "      \"object\": \"payment_intent\",\n" +
                "      \"amount\": 100000,\n" +
                "      \"currency\": \"usd\",\n" +
                "      \"last_payment_error\": {\n" +
                "        \"message\": \"Insufficient funds on card\"\n" +
                "      }\n" +
                "    }\n" +
                "  }\n" +
                "}";

        ResponseEntity<String> response = stripeWebhookController.handleStripeWebhook(webhookJson, null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        productRepository.flush();

        Product productAfterCompensation = productRepository.findById(testProductId).orElseThrow();
        boolean passed = productAfterCompensation.getQuantity() == 10;
        BenchmarkReport.record("stripe-webhook-compensation", passed, System.currentTimeMillis() - t0, 8, productAfterCompensation.getQuantity(), null, null);
        assertThat(productAfterCompensation.getQuantity()).isEqualTo(10);

        Order orderAfterWebhook = orderRepository.findById(order.getOrderId()).orElseThrow();
        assertThat(orderAfterWebhook.getOrderStatus()).isEqualTo("Payment Failed");

        System.out.println("Stripe Webhook Compensation Verified: Stock restored to " + productAfterCompensation.getQuantity());
    }
}

