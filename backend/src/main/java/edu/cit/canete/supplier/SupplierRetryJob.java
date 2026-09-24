package edu.cit.canete.supplier;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import edu.cit.canete.supplier.xml.XmlUtil;

@Configuration
@EnableScheduling
class SupplierRetryJob {
    
    private static final String BASE_URL = "https://legacysupply.onrender.com/api/v1";
    private final SupplierOrderRepository repository;
    private final LegacySupplySessionManager sessionManager;
    private final HttpClient httpClient;

    SupplierRetryJob(SupplierOrderRepository repository, LegacySupplySessionManager sessionManager) {
        this.repository = repository;
        this.sessionManager = sessionManager;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Scheduled(fixedDelay = 60000) // Polls every 1 minute
    public void retryPendingOrders() {
        List<SupplierOrder> pendingOrders = repository.findByStatus(SupplierOrderStatus.PENDING);
        
        for (SupplierOrder order : pendingOrders) {
            try {
                String token = sessionManager.getValidSession();
                String xmlBody = "<PurchaseOrderRequest>" +
                        "<SupplierSku>" + XmlUtil.escape(order.getSupplierSku()) + "</SupplierSku>" +
                        "<Quantity>" + order.getCasesOrdered() + "</Quantity>" +
                        "<Uom>CS</Uom>" +
                        "<BuyerRef>" + XmlUtil.escape(order.getBuyerRef()) + "</BuyerRef>" +
                        "</PurchaseOrderRequest>";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/purchase-orders"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/xml")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Request-Id", order.getRequestId()) // Same UUID to prevent duplicates
                        .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 201 || response.statusCode() == 200) {
                    order.setPoNumber(XmlUtil.extractTag(response.body(), "PoNumber"));
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                } else if (response.statusCode() == 409) {
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                } else if (response.statusCode() >= 400 && response.statusCode() < 500 && response.statusCode() != 401) {
                    order.setStatus(SupplierOrderStatus.FAILED);
                    repository.save(order);
                }
            } catch (Exception e) {
                // Ignore. It remains PENDING and will be retried on the next cron execution.
            }
        }
    }
}