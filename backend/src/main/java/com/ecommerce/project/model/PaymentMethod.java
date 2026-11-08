package com.ecommerce.project.model;

/**
 * Supported Payment Method Enumeration
 *
 * Why & Where it is used:
 *   Validates and enumerates accepted payment rails across the platform: STRIPE, COD (Cash On Delivery), and PAYPAL.
 *
 * Imports & Key Dependencies:
 *   - Standalone enum.
 *
 * Exports & Consumers:
 *   - OrderController: Validates `{paymentMethod}` URL path variables.
 *   - OrderServiceImpl: Enforces gateway-specific requirements (e.g. COD requires no external client secret).
 *
 * Project Impact:
 *   Prevents arbitrary payment strings from entering the system and cleanly isolates payment processing strategies.
 */

public enum PaymentMethod {
    STRIPE,
    COD,
    PAYPAL;

    public static PaymentMethod fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Payment method cannot be null");
        }
        try {
            return PaymentMethod.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported payment method: " + value);
        }
    }
}

