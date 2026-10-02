package edu.cit.canete.channel.json;

public class HeartbeatRequest {
    public String appName;
    public String startedAt;
    public long uptimeSeconds;

    public HeartbeatRequest(String appName, String startedAt, long uptimeSeconds) {
        this.appName = appName;
        this.startedAt = startedAt;
        this.uptimeSeconds = uptimeSeconds;
    }
}