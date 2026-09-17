package edu.cit.canete.shop.event;

/**
 * Published by OrderService when an order is CONFIRMED.
 * This is the only thing the Notification module is allowed to know
 * about Order - it never calls OrderService directly.
 */
public class OrderPlacedEvent {

    private final Long orderId;

    public OrderPlacedEvent(Long orderId) {
        this.orderId = orderId;
    }

    public Long getOrderId() {
        return orderId;
    }
}
