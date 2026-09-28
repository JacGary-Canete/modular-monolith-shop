package edu.cit.canete.supplier;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import edu.cit.canete.supplier.xml.XmlUtil;
import edu.cit.canete.supplier.event.SupplierOrderDeliveredEvent;

@Configuration
@EnableScheduling
class SupplierDeliveryTrackerJob {

    private static final String BASE_URL = "https://legacysupply.onrender.com/api/v1";
    private static final int MAX_CONSECUTIVE_FAILURES = 2;

    private final SupplierOrderRepository repository;
    private final LegacySupplySessionManager sessionManager;
    private final ApplicationEventPublisher events;
    private final HttpClient httpClient;

    SupplierDeliveryTrackerJob(SupplierOrderRepository repository,
                               LegacySupplySessionManager sessionManager,
                               ApplicationEventPublisher events) {
        this.repository = repository;
        this.sessionManager = sessionManager;
        this.events = events;
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    }

    @Scheduled(fixedDelay = 120000) // every 2 minutes, one GET per open order
    public void trackDeliveries() {
        List<SupplierOrder> openOrders = new ArrayList<>();
        openOrders.addAll(repository.findByStatus(SupplierOrderStatus.ACCEPTED));
        openOrders.addAll(repository.findByStatus(SupplierOrderStatus.PICKING));
        openOrders.addAll(repository.findByStatus(SupplierOrderStatus.SHIPPED));

        if (openOrders.isEmpty()) return;
        System.out.println("Tracking delivery status for " + openOrders.size() + " open orders.");

        int consecutiveFailures = 0;

        for (SupplierOrder order : openOrders) {
            if (order.getPoNumber() == null) continue;

            try {
                String token = sessionManager.getValidSession();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/purchase-orders/" + order.getPoNumber()))
                        .timeout(Duration.ofSeconds(3))
                        .header("X-LS-Session", token)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
                int http = response.statusCode();

                if (http == 200) {
                    consecutiveFailures = 0;
                    String code = XmlUtil.extractTag(response.body(), "StatusCode");
                    SupplierOrderStatus mapped = mapLegacyStatus(code);

                    if (mapped == null) {
                        System.out.println("Unexpected StatusCode '" + code + "' for PO "
                                + order.getPoNumber() + ", flagging for review: " + response.body());
                        order.setStatus(SupplierOrderStatus.NEEDS_REVIEW);
                        repository.save(order);
                        continue;
                    }

                    if (mapped != order.getStatus()) {
                        order.setStatus(mapped);
                        repository.save(order);

                        if (mapped == SupplierOrderStatus.DELIVERED) {
                            System.out.println("PO " + order.getPoNumber() + " DELIVERED! Restocking "
                                    + order.getUnitsRequested() + " units.");
                            events.publishEvent(new SupplierOrderDeliveredEvent(
                                    order.getProductId(), order.getUnitsRequested()));
                        }
                    }
                } else if (http == 404) {
                    consecutiveFailures = 0;
                    System.out.println("PO " + order.getPoNumber() + " not found at supplier, flagging for review.");
                    order.setStatus(SupplierOrderStatus.NEEDS_REVIEW);
                    repository.save(order);
                } else if (http == 401) {
                    sessionManager.forceRefresh();
                    break; // retry everything next tick with a fresh session
                } else if (http == 429) {
                    System.out.println("Rate limited by LegacySupply, skipping the rest of this tick.");
                    break;
                } else {
                    consecutiveFailures++;
                    System.out.println("Supplier error " + http + " for PO " + order.getPoNumber()
                            + " (" + consecutiveFailures + " in a row).");
                    if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                        System.out.println("Two failures in a row, stopping this tick.");
                        break;
                    }
                }
            } catch (Exception e) {
                consecutiveFailures++;
                System.out.println("Tracking failed (" + e.getClass().getSimpleName() + "), "
                        + consecutiveFailures + " in a row.");
                if (consecutiveFailures >= MAX_CONSECUTIVE_FAILURES) {
                    break; // supplier unreachable, don't burn quota
                }
            }
        }
    }

    // Manual, section 6: 10 Accepted, 20 Picking, 30 Shipped, 40 Delivered.
    // Anything else returns null and is handled by the caller.
    private SupplierOrderStatus mapLegacyStatus(String code) {
        if (code == null) return null;
        return switch (code.trim()) {
            case "10" -> SupplierOrderStatus.ACCEPTED;
            case "20" -> SupplierOrderStatus.PICKING;
            case "30" -> SupplierOrderStatus.SHIPPED;
            case "40" -> SupplierOrderStatus.DELIVERED;
            default -> null;
        };
    }
}