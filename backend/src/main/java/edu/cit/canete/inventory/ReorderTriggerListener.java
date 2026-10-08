package edu.cit.canete.inventory;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.canete.inventory.event.LowStockEvent;
import edu.cit.canete.supplier.ReorderResult;
import edu.cit.canete.supplier.SupplierGateway;

/**
 * Listens for LowStockEvent (published by InventoryServiceImpl.reserve())
 * and triggers a supplier reorder if one isn't already open.
 *
 * AFTER_COMMIT, same reasoning as TianggeStockSyncListener: reorder() makes
 * a blocking HTTP call (up to 3 attempts with backoff) to LegacySupply.
 * Running it while reserve()'s transaction is still open would hold a DB
 * connection for seconds at a time; under rush-pace load with several
 * low-stock orders close together, that risks exhausting the connection
 * pool. Letting the stock-decrement transaction commit first means the
 * database is never blocked waiting on that network call.
 */
@Component
class ReorderTriggerListener {

    private static final int TARGET_RESTOCK_LEVEL = 50;

    private final SupplierGateway supplierGateway;

    ReorderTriggerListener(SupplierGateway supplierGateway) {
        this.supplierGateway = supplierGateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onLowStock(LowStockEvent event) {
        if (supplierGateway.hasOpenOrder(event.getProductId())) {
            return;
        }

        int unitsNeeded = TARGET_RESTOCK_LEVEL - event.getRemainingStock();
        ReorderResult result = supplierGateway.reorder(event.getProductId(), unitsNeeded);
        System.out.println("Triggered reorder for " + event.getProductId() +
                           ", Units needed: " + unitsNeeded +
                           ", Result: " + result);
    }
}
