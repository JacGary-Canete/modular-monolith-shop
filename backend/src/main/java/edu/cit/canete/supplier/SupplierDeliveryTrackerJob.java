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

    @Scheduled(fixedDelay = 120000) // Poll every 2 minutes to respect quota
    public void trackDeliveries() {
        // Find all orders that are currently in-flight
        List<SupplierOrder> openOrders = new ArrayList<>();
        openOrders.addAll(repository.findByStatus(SupplierOrderStatus.ACCEPTED));
        openOrders.addAll(repository.findByStatus(SupplierOrderStatus.PICKING));
        openOrders.addAll(repository.findByStatus(SupplierOrderStatus.SHIPPED));

        if (openOrders.isEmpty()) return;
        
        System.out.println("Tracking delivery status for " + openOrders.size() + " open orders.");

        for (SupplierOrder order : openOrders) {
            if (order.getPoNumber() == null) continue;

            try {
                String token = sessionManager.getValidSession();
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(BASE_URL + "/purchase-orders/" + order.getPoNumber()))
                        .timeout(Duration.ofSeconds(3))
                        .header("Authorization", "Bearer " + token)
                        .GET()
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    String legacyStatus = XmlUtil.extractTag(response.body(), "Status");
                    if (legacyStatus == null) continue;

                    SupplierOrderStatus mappedStatus = mapLegacyStatus(legacyStatus);
                    
                    // If status changed, update DB
                    if (mappedStatus != order.getStatus()) {
                        order.setStatus(mappedStatus);
                        repository.save(order);
                        
                        // If DELIVERED, publish event so Inventory can restock
                        if (mappedStatus == SupplierOrderStatus.DELIVERED) {
                            events.publishEvent(new SupplierOrderDeliveredEvent(
                                    order.getProductId(), 
                                    order.getUnitsRequested() // Restock the units we originally needed
                            ));
                            System.out.println("PO " + order.getPoNumber() + " DELIVERED! Restocking " + order.getUnitsRequested() + " units.");
                        }
                    }
                } else if (response.statusCode() == 401) {
                    sessionManager.forceRefresh();
                }
            } catch (Exception e) {
                System.out.println("Failed to fetch status for PO " + order.getPoNumber() + ": " + e.getMessage());
            }
        }
    }

    private SupplierOrderStatus mapLegacyStatus(String legacyStatus) {
        return switch (legacyStatus.toUpperCase()) {
            case "PROCESSING" -> SupplierOrderStatus.PICKING;
            case "SHIPPED" -> SupplierOrderStatus.SHIPPED;
            case "DELIVERED" -> SupplierOrderStatus.DELIVERED;
            case "CANCELLED" -> SupplierOrderStatus.FAILED;
            // Part E Requirement: "Handle any status you did not expect"
            // If they introduce a new status like 'HELD_AT_CUSTOMS', we map it to ACCEPTED to keep it open in our system
            default -> SupplierOrderStatus.ACCEPTED; 
        };
    }
}