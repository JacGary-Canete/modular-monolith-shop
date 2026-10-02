package edu.cit.canete;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

/**
 * One instance ID per process run, generated fresh every time the app
 * starts. Sent as X-Client-Instance on every call to Tiangge and
 * LegacySupply, so both systems can tell which running copy made the
 * call (Lab 4, Task 1 and Task 6).
 */
@Component
public class AppInstance {

    private final UUID instanceId = UUID.randomUUID();
    private final Instant startedAt = Instant.now();

    public UUID getInstanceId() {
        return instanceId;
    }

    public Instant getStartedAt() {
        return startedAt;
    }

    public long getUptimeSeconds() {
        return java.time.Duration.between(startedAt, Instant.now()).getSeconds();
    }
}