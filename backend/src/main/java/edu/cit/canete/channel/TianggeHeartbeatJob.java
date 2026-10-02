package edu.cit.canete.channel;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

/**
 * Sends a heartbeat to Tiangge every 30 seconds (Task 1). Tiangge treats
 * an instance without a heartbeat in the last 90 seconds as offline, so
 * missing a few ticks in a row is what actually costs points, not a
 * single failed call.
 */
@Configuration
@EnableScheduling
class TianggeHeartbeatJob {

    private final TianggeGateway gateway;

    TianggeHeartbeatJob(TianggeGateway gateway) {
        this.gateway = gateway;
    }

    @Scheduled(fixedDelay = 30000)
    public void heartbeat() {
        try {
            gateway.sendHeartbeat();
        } catch (Exception e) {
            System.out.println("Heartbeat failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }
    }
}