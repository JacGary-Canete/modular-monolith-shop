package edu.cit.canete.supplier;

/**
 * Our own status vocabulary. LegacySupply's numeric StatusCode values
 * (10/20/30/40) are translated into this enum inside the adapter and
 * never leak past this module's boundary.
 */
public enum SupplierOrderStatus {
    PENDING,    // created locally, not yet successfully sent to LegacySupply
    ACCEPTED,   // LegacySupply StatusCode 10
    PICKING,    // StatusCode 20
    SHIPPED,    // StatusCode 30
    DELIVERED,  // StatusCode 40
    FAILED      // could not be placed after retries exhausted
}