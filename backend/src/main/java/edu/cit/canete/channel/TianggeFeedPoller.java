package edu.cit.canete.channel;

import java.util.Optional;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.canete.inventory.InventoryService;
import edu.cit.canete.inventory.InventoryItem;
import edu.cit.canete.shop.OrderItemRequest;
import edu.cit.canete.shop.OrderResponse;
import edu.cit.canete.shop.OrderService;

import java.util.ArrayList;
import java.util.List;

/**
 * Polls the Tiangge order feed every few seconds (Task 4), decides each
 * new order through the existing OrderService (unchanged from Lab 2),
 * and reports the decision back. Order and Inventory never know Tiangge
 * exists - this class is the only thing that imports both TianggeGateway
 * and OrderService.
 *
 * Database work and HTTP calls to Tiangge are kept in separate methods
 * on purpose. The @Transactional methods only ever touch the database
 * and return before any network call is made, so a slow or hanging
 * Tiangge response can never hold a DB transaction (and its connection)
 * open.
 */
@Configuration
@EnableScheduling
class TianggeFeedPoller {

    private static final int LIMIT = 20;

    private final TianggeGateway gateway;
    private final OrderService orderService;
    private final InventoryService inventoryService;
    private final ChannelCursorRepository cursorRepository;
    private final ChannelOrderRepository channelOrderRepository;
    private final edu.cit.canete.supplier.SupplierGateway supplierGateway;

    TianggeFeedPoller(TianggeGateway gateway, OrderService orderService,
                       InventoryService inventoryService,
                       ChannelCursorRepository cursorRepository,
                       ChannelOrderRepository channelOrderRepository,
                       edu.cit.canete.supplier.SupplierGateway supplierGateway) {
        this.gateway = gateway;
        this.orderService = orderService;
        this.inventoryService = inventoryService;
        this.cursorRepository = cursorRepository;
        this.channelOrderRepository = channelOrderRepository;
        this.supplierGateway = supplierGateway;
    }

    @Scheduled(fixedDelay = 5000)
    public void pollFeed() {
        ChannelCursor cursor = cursorRepository.findById(1).orElseThrow();

        FeedPage page;
        try {
            page = gateway.pollFeed(cursor.getLastSeq(), LIMIT);
        } catch (Exception e) {
            System.out.println("Feed poll failed: " + e.getClass().getSimpleName() + " - " + e.getMessage());
            return;
        }

        if (page.events().isEmpty()) {
            return;
        }

        for (FeedEvent event : page.events()) {
            try {
                handleEvent(event);
            } catch (Exception e) {
                System.out.println("Failed to handle event " + event.eventId() + ": "
                        + e.getClass().getSimpleName() + " - " + e.getMessage());
                return;
            }
        }

        cursor.setLastSeq(page.nextCursor());
        cursorRepository.save(cursor);
    }

    // No @Transactional - routes to a handler, which does its DB work in
    // a short transaction and then makes HTTP calls with no transaction open.
    void handleEvent(FeedEvent event) {
        if ("ORDER_PLACED".equals(event.type())) {
            handleOrderPlaced(event);
        } else if ("ORDER_CANCELLED".equals(event.type())) {
            handleOrderCancelled(event);
        }
    }

    private void handleOrderPlaced(FeedEvent event) {
        DecisionOutcome outcome = decideAndSave(event);
        if (outcome == null) {
            return; // already decided - redelivery, skip it
        }

        try {
            gateway.decide(event.orderId(), outcome.decision, String.valueOf(outcome.orderId), outcome.reason);
        } catch (Exception e) {
            System.out.println("Failed to report decision for " + event.orderId() + ": " + e.getMessage());
        }

        System.out.println("Tiangge order " + event.orderId() + " -> " + outcome.decision
                + " (our order #" + outcome.orderId + ")");
    }

    @Transactional
    DecisionOutcome decideAndSave(FeedEvent event) {
        Optional<ChannelOrder> existing = channelOrderRepository.findByTiangeOrderId(event.orderId());
        if (existing.isPresent() && existing.get().getDecision() != null) {
            return null;
        }

        List<OrderItemRequest> items = new ArrayList<>();
        for (FeedLine line : event.lines()) {
            OrderItemRequest req = new OrderItemRequest();
            req.setProductId(line.sellerSku());
            req.setQuantity(line.qty());
            items.add(req);
        }

        OrderResponse response = orderService.placeOrder(items);

        String decision;
        if ("CONFIRMED".equals(response.getStatus())) {
            decision = "ACCEPTED";
        } else if (canBackorder(items)) {
            orderService.markBackordered(response.getOrderId(), response.getReason());
            decision = "BACKORDERED";
        } else {
            decision = "REJECTED";
        }

        ChannelOrder channelOrder = new ChannelOrder(event.orderId(), event.eventId());
        channelOrder.setOrderId(response.getOrderId());
        channelOrder.setDecision(decision);
        channelOrderRepository.save(channelOrder);

        return new DecisionOutcome(decision, response.getOrderId(), response.getReason());
    }

    private boolean canBackorder(List<OrderItemRequest> items) {
        for (OrderItemRequest item : items) {
            if (!supplierGateway.hasOpenOrder(item.getProductId())) {
                return false;
            }
        }
        return true;
    }

    private void handleOrderCancelled(FeedEvent event) {
        CancelOutcome outcome = cancelAndSave(event);
        if (outcome == null) {
            return;
        }

        try {
            gateway.confirmCancellation(event.orderId());
            System.out.println("Tiangge order " + event.orderId() + " cancelled and confirmed.");
        } catch (Exception e) {
            System.out.println("Failed to confirm cancellation for " + event.orderId() + ": " + e.getMessage());
            return; // don't publish stock for a cancellation we couldn't confirm
        }

        // Manual: confirm first, then publish stock. Explicitly re-publish
        // here, after confirmation, so an update always follows it in the
        // correct order, regardless of when the restock's own event fired.
        for (String productId : outcome.productIds) {
            try {
                InventoryItem item = inventoryService.getItem(productId);
                gateway.publishStock(productId, item.getStock());
            } catch (Exception e) {
                System.out.println("Failed to publish post-cancel stock for " + productId + ": " + e.getMessage());
            }
        }
    }

    @Transactional
    CancelOutcome cancelAndSave(FeedEvent event) {
        Optional<ChannelOrder> existing = channelOrderRepository.findByTiangeOrderId(event.orderId());
        if (existing.isEmpty() || existing.get().getOrderId() == null) {
            System.out.println("Cancellation for unknown Tiangge order " + event.orderId() + ", skipping.");
            return null;
        }

        var order = orderService.cancelOrderAndReturn(existing.get().getOrderId());
        List<String> productIds = new ArrayList<>();
        for (var item : order.getItems()) {
            productIds.add(item.getProductId());
        }
        return new CancelOutcome(productIds);
    }

    private record DecisionOutcome(String decision, Long orderId, String reason) {
    }

    private record CancelOutcome(List<String> productIds) {
    }
}