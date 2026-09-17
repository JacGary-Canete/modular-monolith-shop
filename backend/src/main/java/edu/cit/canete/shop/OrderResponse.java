package edu.cit.canete.shop;

import java.util.List;

import edu.cit.canete.inventory.InventoryItem;

public class OrderResponse {

    private final Long orderId;
    private final String status;
    private final String reason;
    private final List<OrderItemResult> items;
    private final List<InventoryItem> inventory;

    public OrderResponse(Long orderId, String status, String reason,
                          List<OrderItemResult> items, List<InventoryItem> inventory) {
        this.orderId = orderId;
        this.status = status;
        this.reason = reason;
        this.items = items;
        this.inventory = inventory;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getStatus() {
        return status;
    }

    public String getReason() {
        return reason;
    }

    public List<OrderItemResult> getItems() {
        return items;
    }

    public List<InventoryItem> getInventory() {
        return inventory;
    }
}
