package edu.cit.canete.channel;

import java.util.List;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import edu.cit.canete.AppInstance;

/**
 * Publishes our listings once, after the app is fully up (Task 2).
 * Runs on ApplicationReadyEvent rather than a @PostConstruct, so it
 * fires after the whole Spring context (including the DB connection
 * pool) is ready, not mid-startup.
 */
@Component
class TianggeStartupPublisher {

    private final TianggeGateway gateway;
    private final AppInstance appInstance;

    TianggeStartupPublisher(TianggeGateway gateway, AppInstance appInstance) {
        this.gateway = gateway;
        this.appInstance = appInstance;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void publishListingsOnStartup() {
        System.out.println("App instance ID: " + appInstance.getInstanceId());

        List<Listing> listings = List.of(
                new Listing("P100", "Wireless Mouse", "CTT-5425"),
                new Listing("P200", "Mechanical Keyboard", "CTT-2971"),
                new Listing("P300", "USB-C Hub", "CTT-2181")
        );

        int maxAttempts = 3;
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                gateway.publishListings(listings);
                System.out.println("Published " + listings.size() + " listings to Tiangge.");
                return;
            } catch (Exception e) {
                System.out.println("Listings publish attempt " + attempt + " failed: "
                        + e.getClass().getSimpleName() + " - " + e.getMessage());
                if (attempt < maxAttempts) {
                    try {
                        Thread.sleep(2000L * attempt);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }
        System.out.println("Giving up on listings publish after " + maxAttempts + " attempts. "
                + "Shop will not be live until this succeeds - check /verify.");
    }
}