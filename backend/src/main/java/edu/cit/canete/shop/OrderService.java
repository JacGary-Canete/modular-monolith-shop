package edu.cit.canete.shop;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import edu.cit.canete.inventory.InventoryItem;
import edu.cit.canete.inventory.InventoryService;
import edu.cit.canete.shop.event.OrderPlacedEvent;
import edu.cit.canete.shop.event.OrderRejectedEvent;

@Service
public class OrderService {

    // Only the InventoryService interface is visible/importable here.
    // edu.cit.canete.inventory.InventoryServiceImpl is package-private,
    // so it cannot even be named from this class.
    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher events;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository,
                         ApplicationEventPublisher events) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.events = events;
    }

    /**
     * Validates every line item against current stock BEFORE reserving
     * anything. If any single item would exceed available stock, the whole
     * order is rejected and nothing is reserved - no partial fulfillment.
     */
    @Transactional
    public OrderResponse placeOrder(List<OrderItemRequest> itemRequests) {
        // --- Validation pass: no mutation happens here ---
        List<OrderItemResult> validationResults = new ArrayList<>();
        boolean allValid = true;
        String rejectionReason = null;

        for (OrderItemRequest req : itemRequests) {
            InventoryItem current = inventoryService.getItem(req.getProductId());
            if (current.getStock() < req.getQuantity()) {
                allValid = false;
                rejectionReason = "Insufficient stock for " + req.getProductId();
                validationResults.add(new OrderItemResult(req.getProductId(), req.getQuantity(), "INSUFFICIENT_STOCK"));
            } else {
                validationResults.add(new OrderItemResult(req.getProductId(), req.getQuantity(), "OK"));
            }
        }

        Order order = new Order();

        if (!allValid) {
            order.setStatus(OrderStatus.REJECTED);
            order.setReason(rejectionReason);
            for (OrderItemRequest req : itemRequests) {
                order.addItem(new OrderItem(req.getProductId(), req.getQuantity()));
            }
            orderRepository.save(order);

            events.publishEvent(new OrderRejectedEvent(order.getOrderId(), rejectionReason));

            return new OrderResponse(order.getOrderId(), order.getStatus().name(), order.getReason(),
                    validationResults, inventoryService.getAllItems());
        }

        // --- Reservation pass: only runs once every item has passed validation ---
        List<OrderItemResult> reservedResults = new ArrayList<>();
        for (OrderItemRequest req : itemRequests) {
            boolean reserved = inventoryService.reserve(req.getProductId(), req.getQuantity());
            // Should always be true here since we just validated, but guard anyway.
            reservedResults.add(new OrderItemResult(req.getProductId(), req.getQuantity(),
                    reserved ? "RESERVED" : "INSUFFICIENT_STOCK"));
            order.addItem(new OrderItem(req.getProductId(), req.getQuantity()));
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setReason(null);
        orderRepository.save(order);

        events.publishEvent(new OrderPlacedEvent(order.getOrderId()));

        return new OrderResponse(order.getOrderId(), order.getStatus().name(), order.getReason(),
                reservedResults, inventoryService.getAllItems());
    }

    @Transactional
    public Order cancelOrder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order " + orderId + " is already cancelled");
        }

        // Only confirmed orders actually reserved stock, so only those need restocking.
        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}
