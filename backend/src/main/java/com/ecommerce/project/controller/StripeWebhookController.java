package com.ecommerce.project.controller;

/**
 * Stripe Webhook Controller
 *
 * Why & Where it is used:
 *   Asynchronously processes server-to-server lifecycle events dispatched by Stripe
 *   (e.g., `payment_intent.succeeded`, `payment_intent.payment_failed`).
 *
 * Imports & Key Dependencies:
 *   - Stripe SDK: Webhook HMAC signature verification and Event deserialization.
 *   - Persistence: ProcessedStripeEventRepository (idempotency ledger to reject duplicate replays).
 *   - Repositories & Services: OrderRepository, PaymentRepository, ProductRepository, RedisStockGateService.
 *
 * Exports & Consumers:
 *   - Directly called by Stripe cloud infrastructure over public HTTPS.
 *   - Triggers downstream status state machines on Order and inventory restoration on failure.
 *
 * Project Impact:
 *   Critical for high-reliability checkout. Guarantees that dropped browser connections or client
 *   crashes do not leave orders in limbo or leave stock incorrectly depleted on failed payments.
 */

import com.ecommerce.project.model.Order;
import com.ecommerce.project.model.OrderItem;
import com.ecommerce.project.model.Payment;
import com.ecommerce.project.model.ProcessedStripeEvent;
import com.ecommerce.project.repositories.OrderRepository;
import com.ecommerce.project.repositories.PaymentRepository;
import com.ecommerce.project.repositories.ProcessedStripeEventRepository;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.Optional;

@RestController
@RequestMapping("/api/webhooks")
public class StripeWebhookController {

    private static final Logger log = LoggerFactory.getLogger(StripeWebhookController.class);

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProcessedStripeEventRepository processedStripeEventRepository;

    @Autowired
    private RedisStockGateService redisStockGateService;

    @PostMapping("/stripe")
    @Transactional
    public ResponseEntity<String> handleStripeWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        Event event;
        try {
            if (webhookSecret == null || webhookSecret.isBlank() || sigHeader == null || sigHeader.isBlank()) {
                log.warn("Stripe webhook rejected: missing secret or signature header");
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
            }
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (Exception e) {
            log.error("Stripe webhook verify failed: {}", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid signature");
        }

        if (event == null || event.getId() == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Missing event metadata");
        }

        // Idempotency check: Guard against duplicate event deliveries
        if (processedStripeEventRepository.existsByEventId(event.getId())) {
            log.info("Stripe event {} has already been processed. Returning 200 OK.", event.getId());
            return ResponseEntity.ok("Event already processed");
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

        if (paymentIntent == null) {
            processedStripeEventRepository.save(new ProcessedStripeEvent(event.getId(), event.getType()));
            return ResponseEntity.ok("Webhook Handled (Non-PaymentIntent event)");
        }

        Optional<Payment> optionalPayment = paymentRepository.findByPgPaymentId(paymentIntent.getId());
        if (optionalPayment.isEmpty()) {
            processedStripeEventRepository.save(new ProcessedStripeEvent(event.getId(), event.getType()));
            return ResponseEntity.ok("Webhook Handled (Payment record not found)");
        }

        Payment payment = optionalPayment.get();
        Order order = payment.getOrder();
        String eventType = event.getType();

        switch (eventType) {
            case "payment_intent.succeeded":
                payment.setPgStatus("COMPLETED");
                payment.setPgResponseMessage("verified");
                if (order != null && "Pending".equalsIgnoreCase(order.getOrderStatus())) {
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
                } else if (order != null && "Pending".equalsIgnoreCase(order.getOrderStatus())) {
                    order.setOrderStatus("Payment Failed");
                    orderRepository.save(order);
                }
                paymentRepository.save(payment);
                break;

            default:
                break;
        }

        processedStripeEventRepository.save(new ProcessedStripeEvent(event.getId(), eventType));
        return ResponseEntity.ok("Webhook Handled");
    }
}
