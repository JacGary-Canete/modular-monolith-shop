package edu.cit.canete.channel;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.canete.inventory.event.StockChangedEvent;

/**
 * Listens for stock changes from Inventory and pushes the new number to
 * Tiangge (Task 3). Driven entirely by events, never by a timer.
 * Inventory has no idea this listener exists.
 *
 * Runs AFTER_COMMIT, not during the transaction that changed the stock.
 * This HTTP call to Tiangge can be slow or hang; running it while a DB
 * transaction is still open would hold a connection and a lock the whole
 * time, and enough of those at once exhausts the connection pool and
 * freezes the whole app. Letting the transaction commit first means the
 * database is never blocked waiting on a network call.
 */
@Component
class TianggeStockSyncListener {

    private final TianggeGateway gateway;

    TianggeStockSyncListener(TianggeGateway gateway) {
        this.gateway = gateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        try {
            gateway.publishStock(event.getProductId(), event.getNewStock());
            System.out.println("Synced stock to Tiangge: " + event.getProductId()
                    + " = " + event.getNewStock());
        } catch (Exception e) {
            System.out.println("Failed to sync stock for " + event.getProductId()
                    + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}