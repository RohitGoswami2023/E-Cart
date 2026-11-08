package com.ecommerce.project.model;

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

