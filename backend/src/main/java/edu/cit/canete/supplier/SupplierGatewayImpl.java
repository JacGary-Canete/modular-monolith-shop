package edu.cit.canete.supplier;

import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import edu.cit.canete.supplier.xml.XmlUtil;

@Service
class SupplierGatewayImpl implements SupplierGateway {

    private static final String BASE_URL = "https://legacysupply.onrender.com/api/v1";
    private final LegacySupplySessionManager sessionManager;
    private final SupplierOrderRepository repository;
    private final HttpClient httpClient;

    SupplierGatewayImpl(LegacySupplySessionManager sessionManager, SupplierOrderRepository repository) {
        this.sessionManager = sessionManager;
        this.repository = repository;
        // PART D: 3-second timeout built into the client
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public ReorderResult reorder(String productId, int unitsNeeded) {
        // 1. Map Product to Supplier Sku and Pack Size (Part B)
        String sku;
        int packSize = 24; 
        switch (productId) {
            case "P100": sku = "CTT-5425"; break;
            case "P200": sku = "CTT-2971"; break;
            case "P300": sku = "CTT-2181"; break;
            default: throw new IllegalArgumentException("Unmapped product: " + productId);
        }

        // 2. Convert units to cases, rounding up (Part C)
        int casesNeeded = (int) Math.ceil((double) unitsNeeded / packSize);

        // 3. Save as PENDING first to get the DB ID, preventing lost orders (Part D)
        SupplierOrder order = new SupplierOrder();
        order.setProductId(productId);
        order.setSupplierSku(sku);
        order.setUnitsRequested(unitsNeeded);
        order.setCasesOrdered(casesNeeded);
        order.setStatus(SupplierOrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order = repository.save(order); // Now we have the ID

        // Generate BuyerRef and RequestId as required by instructions
        String buyerRef = "RO-" + order.getId();
        String requestId = UUID.randomUUID().toString();
        order.setBuyerRef(buyerRef);
        order.setRequestId(requestId);
        repository.save(order);

        // 4. API Call with 3 Retries and Backoff (Part D)
        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                String token = sessionManager.getValidSession();
                String xmlBody = "<PurchaseOrderRequest>" +
                        "<SupplierSku>" + XmlUtil.escape(sku) + "</SupplierSku>" +
                        "<Quantity>" + casesNeeded + "</Quantity>" +
                        "<Uom>CS</Uom>" +
                        "<BuyerRef>" + XmlUtil.escape(buyerRef) + "</BuyerRef>" +
                        "</PurchaseOrderRequest>";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/purchase-orders"))
                        .timeout(Duration.ofSeconds(3)) // 3-second timeout per call
                        .header("Content-Type", "application/xml")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", requestId) // Idempotency key
                        .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    sessionManager.forceRefresh();
                    continue; // Token expired mid-flight, loop again
                }

                if (response.statusCode() == 201 || response.statusCode() == 200) {
                    order.setPoNumber(XmlUtil.extractTag(response.body(), "PoNumber"));
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                    return ReorderResult.ACCEPTED;
                } else if (response.statusCode() == 409) {
                    // LegacySupply already processed this X-Request-Id (Idempotency saved us)
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                    return ReorderResult.ACCEPTED;
                } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    order.setStatus(SupplierOrderStatus.FAILED);
                    repository.save(order);
                    return ReorderResult.FAILED;
                }

            } catch (Exception e) {
                if (attempt == maxAttempts) break; // Exhausted retries
            }
            
            // Exponential backoff before retry
            try { Thread.sleep((long) Math.pow(2, attempt) * 500); } 
            catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
        }

        // Leave PENDING if all retries fail. The background job will pick it up.
        return ReorderResult.PENDING; 
    }
}