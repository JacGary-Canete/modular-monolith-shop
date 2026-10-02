package edu.cit.canete.inventory.event;

/**
 * Published by InventoryServiceImpl whenever stock actually changes
 * (a reservation, a restock, or a supplier delivery). Consumed by the
 * channel module to keep Tiangge's stock numbers in sync (Lab 4, Task 3).
 * Inventory has no idea Tiangge exists - it just announces "this product's
 * stock is now X."
 */
public class StockChangedEvent {

    private final String productId;
    private final int newStock;

    public StockChangedEvent(String productId, int newStock) {
        this.productId = productId;
        this.newStock = newStock;
    }

    public String getProductId() {
        return productId;
    }

    public int getNewStock() {
        return newStock;
    }
}