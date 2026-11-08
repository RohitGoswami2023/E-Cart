package com.ecommerce.project.model;

public enum OrderStatus {
    PENDING("Pending"),
    ORDER_CONFIRMED("Order Confirmed"),
    SHIPPED("Shipped"),
    DELIVERED("Delivered"),
    CANCELLED("Cancelled"),
    PAYMENT_FAILED("Payment Failed");

    private final String value;

    OrderStatus(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    public static OrderStatus fromString(String text) {
        if (text == null || text.isBlank()) {
            return ORDER_CONFIRMED;
        }
        for (OrderStatus status : OrderStatus.values()) {
            if (status.name().equalsIgnoreCase(text.replace(" ", "_"))
                    || status.value.equalsIgnoreCase(text)) {
                return status;
            }
        }
        throw new IllegalArgumentException("Unknown order status: " + text);
    }

    public boolean canTransitionTo(OrderStatus target) {
        if (this == target) return true;
        return switch (this) {
            case PENDING -> target == ORDER_CONFIRMED || target == PAYMENT_FAILED || target == CANCELLED;
            case ORDER_CONFIRMED -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED || target == CANCELLED;
            case DELIVERED -> false;
            case CANCELLED, PAYMENT_FAILED -> false;
        };
    }
}

