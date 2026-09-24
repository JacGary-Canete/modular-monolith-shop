package edu.cit.canete.inventory;

import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import edu.cit.canete.supplier.SupplierGateway;
import edu.cit.canete.supplier.ReorderResult;
import edu.cit.canete.inventory.event.LowStockEvent;

/**
 * Package-private: only edu.cit.canete.inventory can see this class.
 * The shop (Order) and notification modules can only ever depend on the
 * InventoryService interface.
 */
@Service
class InventoryServiceImpl implements InventoryService {

    private static final int LOW_STOCK_THRESHOLD = 5;
    private static final int TARGET_RESTOCK_LEVEL = 50;

    private final InventoryRepository repository;
    private final SupplierGateway supplierGateway;
    private final ApplicationEventPublisher events;

    // INJECT SupplierGateway here
    InventoryServiceImpl(InventoryRepository repository, SupplierGateway supplierGateway, ApplicationEventPublisher events) {
        this.repository = repository;
        this.supplierGateway = supplierGateway;
        this.events = events;
    }

    @Override
    public InventoryItem getItem(String productId) {
        return repository.findById(productId)
                .orElseThrow(() -> new NoSuchElementException("Unknown product: " + productId));
    }

    @Override
    public List<InventoryItem> getAllItems() {
        return repository.findAll();
    }

    @Override
    @Transactional
    public boolean reserve(String productId, int quantity) {
        InventoryItem item = getItem(productId);
        if (item.getStock() < quantity) {
            return false;
        }
        
        item.setStock(item.getStock() - quantity);
        repository.save(item);

        if (item.getStock() < LOW_STOCK_THRESHOLD) {
            // Keep the existing notification event
            events.publishEvent(new LowStockEvent(item.getProductId(), item.getName(), item.getStock()));
            
            // Trigger the automated supplier reorder
            int unitsNeeded = TARGET_RESTOCK_LEVEL - item.getStock();
            ReorderResult result = supplierGateway.reorder(item.getProductId(), unitsNeeded);
            
            System.out.println("Triggered reorder for " + item.getProductId() + 
                               ", Units needed: " + unitsNeeded + 
                               ", Result: " + result);
        }
        return true;
    }

    @Override
    @Transactional
    public void restock(String productId, int quantity) {
        InventoryItem item = getItem(productId);
        item.setStock(item.getStock() + quantity);
        repository.save(item);
    }
}