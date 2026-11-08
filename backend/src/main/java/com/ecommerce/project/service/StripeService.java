package com.ecommerce.project.service;

/**
 * Stripe Payment Gateway Service Interface
 *
 * Why & Where it is used:
 *   Specifies the contract for external Stripe payment operations: creating PaymentIntents,
 *   retrieving payment transaction status, and issuing compensating refunds.
 *
 * Imports & Key Dependencies:
 *   - Stripe Java SDK: PaymentIntent, StripeException.
 *   - Payloads: StripePaymentDto.
 *
 * Exports & Consumers:
 *   - Implemented by StripeServiceImpl.
 *   - Fake implementation: FakeStripeService (used during deterministic test pipelines).
 *   - Injected into OrderController and OrderServiceImpl.
 *
 * Project Impact:
 *   Allows flexible swapping between live Stripe API and offline mock/fake services during testing.
 */

import com.ecommerce.project.payload.StripePaymentDto;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;

public interface StripeService {
    PaymentIntent paymentIntent(StripePaymentDto stripePaymentDto) throws StripeException;

    PaymentIntent retrieve(String paymentIntentId) throws StripeException;

    void refund(String paymentIntentId, String reason) throws StripeException;
}
