package edu.cit.canete.channel;

import java.util.List;

/**
 * The only public type other than domain records in this package.
 * Nothing outside edu.cit.canete.channel needs this interface directly
 * right now (Order/Inventory never call into channel - it's the other
 * way around), but it keeps the same ACL shape as the supplier module.
 */
public interface TianggeGateway {

    void sendHeartbeat();

    void publishListings(List<Listing> listings);

    void publishStock(String sellerSku, int available);

    FeedPage pollFeed(long after, int limit);

    void decide(String tiangeOrderId, String decision, String shopOrderId, String reason);

    void confirmCancellation(String tiangeOrderId);

    void resolveBackorder(String tiangeOrderId, String status);
}