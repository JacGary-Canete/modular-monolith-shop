package edu.cit.canete.supplier.event;

/**
 * Published when LegacySupply confirms an order is DELIVERED.
 * Inventory listens to this to restock units.
 */
public record SupplierOrderDeliveredEvent(String productId, int unitsDelivered) {
}