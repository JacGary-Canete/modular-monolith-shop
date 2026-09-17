package edu.cit.canete.notification;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.canete.inventory.event.LowStockEvent;
import edu.cit.canete.shop.event.OrderPlacedEvent;
import edu.cit.canete.shop.event.OrderRejectedEvent;

/**
 * Listens for domain events published by Order and Inventory.
 * This class depends only on the event classes - it never calls
 * OrderService or InventoryService directly, and neither of those
 * modules imports anything from this (notification) package.
 *
 * Listeners run synchronously (Spring's default) for this lab: order
 * placement and low-stock checks are quick, in-process operations, and
 * synchronous listening keeps the notification write inside the same
 * request/transaction lifecycle as the event that triggered it, so there's
 * no risk of losing a notification if the app restarts between the event
 * firing and an async listener picking it up. See README for more detail.
 */
@Component
class NotificationEventListener {

    private final NotificationRepository repository;

    NotificationEventListener(NotificationRepository repository) {
        this.repository = repository;
    }

    @EventListener
    public void onOrderPlaced(OrderPlacedEvent event) {
        repository.save(new Notification("Order " + event.getOrderId() + " confirmed"));
    }

    @EventListener
    public void onOrderRejected(OrderRejectedEvent event) {
        repository.save(new Notification(
                "Order " + event.getOrderId() + " rejected: " + event.getReason()));
    }

    @EventListener
    public void onLowStock(LowStockEvent event) {
        repository.save(new Notification(
                "Reorder needed: " + event.getName() + " (" + event.getProductId()
                        + ") is down to " + event.getRemainingStock() + " units"));
    }
}
