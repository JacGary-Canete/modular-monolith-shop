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
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .build();
    }

    @Override
    public ReorderResult reorder(String productId, int unitsNeeded) {
        String sku;
        int packSize = 24;
        switch (productId) {
            case "P100": sku = "CTT-5425"; break;
            case "P200": sku = "CTT-2971"; break;
            case "P300": sku = "CTT-2181"; break;
            default: throw new IllegalArgumentException("Unmapped product: " + productId);
        }

        int casesNeeded = (int) Math.ceil((double) unitsNeeded / packSize);

        String buyerRef = "RO-" + UUID.randomUUID();
        String requestId = UUID.randomUUID().toString();

        SupplierOrder order = new SupplierOrder();
        order.setProductId(productId);
        order.setSupplierSku(sku);
        order.setUnitsRequested(casesNeeded * packSize); // units that will actually arrive
        order.setCasesOrdered(casesNeeded);
        order.setStatus(SupplierOrderStatus.PENDING);
        order.setCreatedAt(Instant.now());
        order.setBuyerRef(buyerRef);
        order.setRequestId(requestId);
        order = repository.save(order);

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                System.out.println("Attempt " + attempt + ": getting session token...");
                String token = sessionManager.getValidSession();
                System.out.println("Got token, sending PO request for " + sku + " x" + casesNeeded + " cases...");

                String xmlBody = "<PurchaseOrder>" +
                        "<SupplierSku>" + XmlUtil.escape(sku) + "</SupplierSku>" +
                        "<Qty>" + casesNeeded + "</Qty>" +
                        "<BuyerRef>" + XmlUtil.escape(buyerRef) + "</BuyerRef>" +
                        "</PurchaseOrder>";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/purchase-orders"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/xml")
                        .header("X-LS-Session", token)
                        .header("X-Request-Id", requestId)
                        .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 401) {
                    System.out.println("Attempt " + attempt + " got 401, forcing session refresh...");
                    sessionManager.forceRefresh();
                    continue;
                }

                if (response.statusCode() == 201 || response.statusCode() == 200) {
                    order.setPoNumber(XmlUtil.extractTag(response.body(), "PoNumber"));
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                    return ReorderResult.ACCEPTED;
                } else if (response.statusCode() == 409) {
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                    return ReorderResult.ACCEPTED;
                } else if (response.statusCode() >= 400 && response.statusCode() < 500) {
                    System.out.println("Attempt " + attempt + " got client error " 
                        + response.statusCode() + ": " + response.body());
                    order.setStatus(SupplierOrderStatus.FAILED);
                    repository.save(order);
                    return ReorderResult.FAILED;
                } else {
                    System.out.println("Attempt " + attempt + " got unexpected status "
                        + response.statusCode() + ": " + response.body());
                }

            } catch (Exception e) {
                System.out.println("Attempt " + attempt + " failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
                if (attempt == maxAttempts) break;
            }

            try {
                Thread.sleep((long) Math.pow(2, attempt) * 500);
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
            }
        }

        return ReorderResult.PENDING;
    }
}