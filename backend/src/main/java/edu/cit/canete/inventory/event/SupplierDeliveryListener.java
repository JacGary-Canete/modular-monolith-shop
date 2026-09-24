package edu.cit.canete.inventory;

import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import edu.cit.canete.supplier.event.SupplierOrderDeliveredEvent;

@Component
class SupplierDeliveryListener {

    private final InventoryService inventoryService;

    SupplierDeliveryListener(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @EventListener
    public void onSupplierOrderDelivered(SupplierOrderDeliveredEvent event) {
        System.out.println("Inventory Module received delivery event. Restocking " + 
                           event.unitsDelivered() + " units for " + event.productId());
        
        inventoryService.restock(event.productId(), event.unitsDelivered());
    }
}