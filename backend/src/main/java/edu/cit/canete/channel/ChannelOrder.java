package edu.cit.canete.channel;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "channel_orders")
public class ChannelOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tiangge_order_id", nullable = false, unique = true)
    private String tiangeOrderId;

    @Column(name = "tiangge_event_id", unique = true)
    private String tiangeEventId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "decision")
    private String decision;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt = Instant.now();

    @Column(name = "resolved", nullable = false)
    private boolean resolved = false;

    @Column(name = "resolution")
    private String resolution; // "ACCEPTED" or "CANCELLED", set once a backorder is resolved

    protected ChannelOrder() {
    }

    public ChannelOrder(String tiangeOrderId, String tiangeEventId) {
        this.tiangeOrderId = tiangeOrderId;
        this.tiangeEventId = tiangeEventId;
    }

    public Long getId() { return id; }
    public String getTiangeOrderId() { return tiangeOrderId; }
    public String getTiangeEventId() { return tiangeEventId; }
    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; this.decidedAt = Instant.now(); }
    public Instant getDecidedAt() { return decidedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public boolean isResolved() { return resolved; }
    public String getResolution() { return resolution; }
    public void setResolution(String resolution) { this.resolution = resolution; this.resolved = true; }
}