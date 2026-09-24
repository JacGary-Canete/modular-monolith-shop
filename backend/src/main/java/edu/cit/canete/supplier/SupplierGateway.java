package edu.cit.canete.supplier;

/**
 * The ONLY entry point into the supplier module. Order and Inventory may
 * depend on this interface and on ReorderResult / SupplierOrderStatus -
 * nothing else in this package is public.
 */
public interface SupplierGateway {

    /**
     * Places (or queues, if LegacySupply is unavailable) a reorder for
     * the given product and unit quantity. Unit-of-measure conversion
     * (units -> LegacySupply cases, rounded up) happens inside the
     * implementation.
     */
    ReorderResult reorder(String productId, int unitsNeeded);
}