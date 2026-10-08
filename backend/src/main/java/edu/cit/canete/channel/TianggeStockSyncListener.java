package edu.cit.canete.channel;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.canete.inventory.InventoryService;
import edu.cit.canete.inventory.event.StockChangedEvent;

/**
 * Listens for stock changes from Inventory and pushes the new number to
 * Tiangge (Task 3).
 *
 * AFTER_COMMIT, same reasoning as before - this HTTP call can be slow,
 * and must never hold a DB transaction open.
 *
 * Re-reads the current stock from the database at send time instead of
 * trusting the value captured in the event, and serializes publishes per
 * product with an in-JVM lock. With several scheduled jobs now running on
 * separate threads (heartbeat, feed poll, retry job, delivery tracker,
 * backorder resolver), two StockChangedEvents for the same product can
 * fire close together on different threads. Without this, their HTTP
 * calls can race and arrive at Tiangge out of order, leaving a stale
 * number in place that looks like it "ignored" a later accepted order.
 * Serializing per product and always sending the latest DB value means
 * whichever call goes last always sends the truth.
 */
@Component
class TianggeStockSyncListener {

    private final TianggeGateway gateway;
    private final InventoryService inventoryService;
    private final Map<String, Object> productLocks = new ConcurrentHashMap<>();

    TianggeStockSyncListener(TianggeGateway gateway, InventoryService inventoryService) {
        this.gateway = gateway;
        this.inventoryService = inventoryService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        String productId = event.getProductId();
        Object lock = productLocks.computeIfAbsent(productId, k -> new Object());

        synchronized (lock) {
            try {
                int currentStock = inventoryService.getItem(productId).getStock();
                gateway.publishStock(productId, currentStock);
                System.out.println("Synced stock to Tiangge: " + productId + " = " + currentStock);
            } catch (Exception e) {
                System.out.println("Failed to sync stock for " + productId
                        + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }
}
