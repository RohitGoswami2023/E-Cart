package com.ecommerce.project.controller;

// Connected to: PaymentRepository, OrderRepository, ProductRepository, RedisStockGateService
// Purpose: Stripe webhook handling for payment_intent.succeeded/failed events with stock compensation

import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.repositories.OrderRepository;
import com.ecommerce.project.repositories.PaymentRepository;
import com.ecommerce.project.repositories.ProductRepository;
import com.ecommerce.project.service.RedisStockGateService;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.net.Webhook;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Arrays;
import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @Autowired
    private Environment environment;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisStockGateService redisStockGateService;

    @PostMapping("/stripe")
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        Event event;
        boolean testProfile = Arrays.asList(environment.getActiveProfiles()).contains("test");
        try {
            if (testProfile && (sigHeader == null || sigHeader.isBlank())) {
                event = com.stripe.net.ApiResource.GSON.fromJson(payload, Event.class);
            } else {
                if (webhookSecret == null || webhookSecret.isBlank() || sigHeader == null) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
                }
                event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
            }
        } catch (Exception e) {
            log.error("Stripe webhook verify failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        EventDataObjectDeserializer dataObjectDeserializer = event.getDataObjectDeserializer();
        PaymentIntent paymentIntent = null;
        if (dataObjectDeserializer.getObject().isPresent() && dataObjectDeserializer.getObject().get() instanceof PaymentIntent) {
            paymentIntent = (PaymentIntent) dataObjectDeserializer.getObject().get();
        } else if (dataObjectDeserializer.getRawJson() != null) {
            try {
                paymentIntent = com.stripe.net.ApiResource.GSON.fromJson(dataObjectDeserializer.getRawJson(), PaymentIntent.class);
            } catch (Exception ignored) {
            }
        }

        String eventType = event.getType();
        if (paymentIntent == null) {
            return ResponseEntity.ok("Webhook Handled");
        }

        Optional<Payment> optionalPayment = paymentRepository.findByPgPaymentId(paymentIntent.getId());
        if (optionalPayment.isEmpty()) {
            return ResponseEntity.ok("Webhook Handled");
        }

        Payment payment = optionalPayment.get();
        Order order = payment.getOrder();

        switch (eventType) {
            case "payment_intent.succeeded":
                payment.setPgStatus("COMPLETED");
                payment.setPgResponseMessage("verified");
                if (order != null) {
                    order.setOrderStatus("Order Confirmed");
                    orderRepository.save(order);
                }
                paymentRepository.save(payment);
                break;
            case "payment_intent.payment_failed":
                if ("FAILED".equalsIgnoreCase(payment.getPgStatus())) {
                    break;
                }
                payment.setPgStatus("FAILED");
                payment.setPgResponseMessage("failed");
                if (order != null && "Order Confirmed".equalsIgnoreCase(order.getOrderStatus())) {
                    order.setOrderStatus("Payment Failed");
                    orderRepository.save(order);
                    if (order.getOrderItems() != null) {
                        for (OrderItem item : order.getOrderItems()) {
                            if (item.getProduct() != null) {
                                productRepository.incrementStock(item.getProduct().getProductId(), item.getQuantity());
                                redisStockGateService.rollbackReservation(item.getProduct().getProductId(), item.getQuantity());
                            }
                        }
                    }
                } else if (order != null) {
                    order.setOrderStatus("Payment Failed");
                    orderRepository.save(order);
                }
                paymentRepository.save(payment);
                break;
            default:
                break;
        }

        return ResponseEntity.ok("Webhook Handled");
    }
}
