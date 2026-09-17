package edu.cit.canete.shop;

public class OrderItemResult {

    private final String productId;
    private final int quantity;
    private final String outcome; // "RESERVED" or "INSUFFICIENT_STOCK"

    public OrderItemResult(String productId, int quantity, String outcome) {
        this.productId = productId;
        this.quantity = quantity;
        this.outcome = outcome;
    }

    public String getProductId() {
        return productId;
    }

    public int getQuantity() {
        return quantity;
    }

    public String getOutcome() {
        return outcome;
    }
}
