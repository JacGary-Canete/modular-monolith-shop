package edu.cit.canete.channel;

import java.util.List;

public record FeedEvent(
        long seq,
        String eventId,
        String type,          // "ORDER_PLACED" or "ORDER_CANCELLED"
        String orderId,       // Tiangge's order ID, e.g. "TG-K7Q2MX"
        List<FeedLine> lines, // present on ORDER_PLACED
        String placedAt,
        String decisionDeadline,
        String cancelledAt,
        String confirmDeadline,
        Buyer buyer
) {
}