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
import edu.cit.canete.AppInstance;
import edu.cit.canete.supplier.xml.XmlUtil;

@Configuration
@EnableScheduling
class SupplierRetryJob {

    private static final String BASE_URL = "https://legacysupply.onrender.com/api/v1";
    private final SupplierOrderRepository repository;
    private final LegacySupplySessionManager sessionManager;
    private final HttpClient httpClient;
    private final AppInstance appInstance;

    SupplierRetryJob(SupplierOrderRepository repository, LegacySupplySessionManager sessionManager,
                      AppInstance appInstance) {
        this.repository = repository;
        this.sessionManager = sessionManager;
        this.appInstance = appInstance;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Scheduled(fixedDelay = 60000)
    public void retryPendingOrders() {
        List<SupplierOrder> pendingOrders = repository.findByStatus(SupplierOrderStatus.PENDING);
        if (pendingOrders.isEmpty()) {
            return;
        }
        System.out.println("Retry job: " + pendingOrders.size() + " pending order(s).");

        for (SupplierOrder order : pendingOrders) {
            try {
                String token = sessionManager.getValidSession();
                String xmlBody = "<PurchaseOrder>" +
                        "<SupplierSku>" + XmlUtil.escape(order.getSupplierSku()) + "</SupplierSku>" +
                        "<Qty>" + order.getCasesOrdered() + "</Qty>" +
                        "<BuyerRef>" + XmlUtil.escape(order.getBuyerRef()) + "</BuyerRef>" +
                        "</PurchaseOrder>";

                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/purchase-orders"))
                        .timeout(Duration.ofSeconds(3))
                        .header("Content-Type", "application/xml")
                        .header("X-LS-Session", token)
                        .header("X-Request-Id", order.getRequestId()) // same id as the first attempt
                        .header("X-Client-Instance", appInstance.getInstanceId().toString())
                        .POST(HttpRequest.BodyPublishers.ofString(xmlBody))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();

                if (status == 200 || status == 201) {
                    order.setPoNumber(XmlUtil.extractTag(response.body(), "PoNumber"));
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                    System.out.println("Retry job: " + order.getBuyerRef() + " accepted.");
                } else if (status == 409) {
                    order.setStatus(SupplierOrderStatus.ACCEPTED);
                    repository.save(order);
                    System.out.println("Retry job: " + order.getBuyerRef() + " already known (409).");
                } else if (status == 401) {
                    sessionManager.forceRefresh(); // stays PENDING, next tick retries
                } else if (status >= 400 && status < 500) {
                    System.out.println("Retry job: " + order.getBuyerRef() + " rejected " + status + ": " + response.body());
                    order.setStatus(SupplierOrderStatus.FAILED);
                    repository.save(order);
                } else {
                    System.out.println("Retry job: server error " + status + " for " + order.getBuyerRef() + ", will retry.");
                }
            } catch (Exception e) {
                System.out.println("Retry job: " + order.getBuyerRef() + " failed: "
                        + e.getClass().getSimpleName() + " - " + e.getMessage());
                break; // LegacySupply is unreachable, so skip the rest of this tick
            }
        }
    }
}