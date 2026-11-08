package com.ecommerce.project.service;

import com.ecommerce.project.payload.StripePaymentDto;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

import java.util.Collections;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Deterministic test double for StripeService avoiding external network calls
 * and reproducing exact checkout/saga scenarios.
 */
public class FakeStripeService implements StripeService {

    private final Map<String, PaymentIntent> intents = new ConcurrentHashMap<>();
    private final Map<String, String> refundLog = new ConcurrentHashMap<>();

    public void registerIntent(String id, String status, Long amount, String currency, String userEmail) {
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setStatus(status);
        intent.setAmount(amount);
        intent.setCurrency(currency);
        if (userEmail != null) {
            intent.setMetadata(Map.of("userEmail", userEmail));
        } else {
            intent.setMetadata(Collections.emptyMap());
        }
        intents.put(id, intent);
    }

    @Override
    public PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException {
        String id = "pi_fake_" + System.currentTimeMillis();
        PaymentIntent intent = new PaymentIntent();
        intent.setId(id);
        intent.setStatus("succeeded");
        intent.setAmount(stripePaymentDto.getAmount());
        intent.setCurrency(stripePaymentDto.getCurrency());
        if (stripePaymentDto.getEmail() != null) {
            intent.setMetadata(Map.of("userEmail", stripePaymentDto.getEmail()));
        }
        intents.put(id, intent);
        return intent;
    }

    @Override
    public PaymentIntent retrieve(String paymentIntentId) throws StripeException {
        PaymentIntent intent = intents.get(paymentIntentId);
        if (intent == null) {
            // Default deterministic behavior: if starts with "pi_failed" or contains "failed", return failed
            PaymentIntent generated = new PaymentIntent();
            generated.setId(paymentIntentId);
            if (paymentIntentId != null && paymentIntentId.toLowerCase().contains("failed")) {
                generated.setStatus("requires_payment_method");
            } else {
                generated.setStatus("succeeded");
            }
            return generated;
        }
        return intent;
    }

    @Override
    public void refund(String paymentIntentId, String reason) throws StripeException {
        refundLog.put(paymentIntentId, reason != null ? reason : "unknown");
    }

    public boolean wasRefunded(String paymentIntentId) {
        return refundLog.containsKey(paymentIntentId);
    }

    public void clear() {
        intents.clear();
        refundLog.clear();
    }
}

