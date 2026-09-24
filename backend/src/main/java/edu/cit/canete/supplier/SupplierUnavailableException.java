package edu.cit.canete.supplier;

/**
 * Thrown when LegacySupply cannot be reached or rejects our credentials
 * in a way that isn't a normal per-order error. Package-private types
 * upstream (OrderService/InventoryService) never see this directly -
 * SupplierGatewayImpl catches it and returns a PENDING/FAILED
 * ReorderResult instead of letting it propagate.
 */
public class SupplierUnavailableException extends RuntimeException {
    public SupplierUnavailableException(String message) {
        super(message);
    }

    public SupplierUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}