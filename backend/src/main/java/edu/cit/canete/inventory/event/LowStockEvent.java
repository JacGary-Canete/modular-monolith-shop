package edu.cit.canete.inventory.event;

/**
 * Published by InventoryServiceImpl after a successful reserve() leaves a
 * product's stock below the configured low-stock threshold.
 */
public class LowStockEvent {

    private final String productId;
    private final String name;
    private final int remainingStock;

    public LowStockEvent(String productId, String name, int remainingStock) {
        this.productId = productId;
        this.name = name;
        this.remainingStock = remainingStock;
    }

    public String getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public int getRemainingStock() {
        return remainingStock;
    }
}
