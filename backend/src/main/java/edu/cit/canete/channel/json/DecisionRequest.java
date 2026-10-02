package edu.cit.canete.channel.json;

public class DecisionRequest {
    public String decision;
    public String shopOrderId;
    public String reason;

    public DecisionRequest(String decision, String shopOrderId, String reason) {
        this.decision = decision;
        this.shopOrderId = shopOrderId;
        this.reason = reason;
    }
}