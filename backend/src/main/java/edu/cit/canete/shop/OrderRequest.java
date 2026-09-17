package edu.cit.canete.shop;

import java.util.List;

public class OrderRequest {

    private List<OrderItemRequest> items;

    public OrderRequest() {
    }

    public List<OrderItemRequest> getItems() {
        return items;
    }

    public void setItems(List<OrderItemRequest> items) {
        this.items = items;
    }
}
