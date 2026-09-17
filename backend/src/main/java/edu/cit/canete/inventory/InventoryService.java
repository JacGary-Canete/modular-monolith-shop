package edu.cit.canete.inventory;

/**
 * This is the ONLY inventory type the shop (Order) module is allowed to see.
 * The implementation (InventoryServiceImpl) is package-private, so it is
 * physically impossible for classes outside this package to import it.
 */
public interface InventoryService {

    /**
     * Fetches the current inventory record for a product.
     * @throws java.util.NoSuchElementException if the product does not exist
     */
    InventoryItem getItem(String productId);

    /**
     * Returns every product's current inventory record.
     */
    java.util.List<InventoryItem> getAllItems();

    /**
     * Attempts to reserve (decrement) stock for a product.
     * @return true if the reservation succeeded, false if requested quantity
     *         exceeds available stock.
     */
    boolean reserve(String productId, int quantity);

    /**
     * Returns previously reserved stock back to inventory (used on order
     * cancellation).
     */
    void restock(String productId, int quantity);
}
