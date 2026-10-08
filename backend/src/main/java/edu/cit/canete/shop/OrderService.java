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

    private final InventoryService inventoryService;
    private final OrderRepository orderRepository;
    private final ApplicationEventPublisher events;

    public OrderService(InventoryService inventoryService, OrderRepository orderRepository,
                         ApplicationEventPublisher events) {
        this.inventoryService = inventoryService;
        this.orderRepository = orderRepository;
        this.events = events;
    }

    @Transactional
    public OrderResponse placeOrder(List<OrderItemRequest> itemRequests) {
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

        if (!allValid) {
            Order order = new Order();
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

        // --- Reservation pass ---
        // The validation pass above is just an early check - the stock it saw
        // can already be gone by the time we actually reserve, since another
        // order can run concurrently (different scheduled jobs now run on
        // separate threads). reserve() re-checks atomically within its own
        // transaction and is the real source of truth, so if it ever returns
        // false here, we must not confirm the order: we restock whatever we
        // already reserved for earlier items in this same order and reject
        // it instead. Confirming anyway would be promising stock we never
        // actually hold - an oversell.
        List<OrderItemResult> reservedResults = new ArrayList<>();
        List<OrderItemRequest> actuallyReserved = new ArrayList<>();
        boolean reservationFailed = false;
        String failureReason = null;

        for (OrderItemRequest req : itemRequests) {
            boolean reserved = inventoryService.reserve(req.getProductId(), req.getQuantity());
            if (!reserved) {
                reservationFailed = true;
                failureReason = "Insufficient stock for " + req.getProductId()
                        + " (lost a race with a concurrent order)";
                reservedResults.add(new OrderItemResult(req.getProductId(), req.getQuantity(), "INSUFFICIENT_STOCK"));
                break;
            }
            reservedResults.add(new OrderItemResult(req.getProductId(), req.getQuantity(), "RESERVED"));
            actuallyReserved.add(req);
        }

        if (reservationFailed) {
            for (OrderItemRequest req : actuallyReserved) {
                inventoryService.restock(req.getProductId(), req.getQuantity());
            }

            Order rejected = new Order();
            rejected.setStatus(OrderStatus.REJECTED);
            rejected.setReason(failureReason);
            for (OrderItemRequest req : itemRequests) {
                rejected.addItem(new OrderItem(req.getProductId(), req.getQuantity()));
            }
            orderRepository.save(rejected);

            events.publishEvent(new OrderRejectedEvent(rejected.getOrderId(), failureReason));

            return new OrderResponse(rejected.getOrderId(), rejected.getStatus().name(), rejected.getReason(),
                    reservedResults, inventoryService.getAllItems());
        }

        Order order = new Order();
        for (OrderItemRequest req : itemRequests) {
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

        if (order.getStatus() == OrderStatus.CONFIRMED) {
            for (OrderItem item : order.getItems()) {
                inventoryService.restock(item.getProductId(), item.getQuantity());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        return orderRepository.save(order);
    }

    @Transactional
    public Order cancelOrderAndReturn(Long orderId) {
        try {
            return cancelOrder(orderId);
        } catch (IllegalStateException alreadyCancelled) {
            return orderRepository.findById(orderId)
                    .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        }
    }

    @Transactional
    public Order markBackordered(Long orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));
        order.setStatus(OrderStatus.BACKORDERED);
        order.setReason(reason);
        return orderRepository.save(order);
    }

    @Transactional
    public boolean tryFulfillBackorder(Long orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new NoSuchElementException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.BACKORDERED) {
            return false;
        }

        for (OrderItem item : order.getItems()) {
            InventoryItem current = inventoryService.getItem(item.getProductId());
            if (current.getStock() < item.getQuantity()) {
                return false;
            }
        }

        List<OrderItem> actuallyReserved = new ArrayList<>();
        for (OrderItem item : order.getItems()) {
            boolean reserved = inventoryService.reserve(item.getProductId(), item.getQuantity());
            if (!reserved) {
                // Lost a race after the check above - put back what we
                // already reserved for this attempt and stay BACKORDERED.
                for (OrderItem done : actuallyReserved) {
                    inventoryService.restock(done.getProductId(), done.getQuantity());
                }
                return false;
            }
            actuallyReserved.add(item);
        }

        order.setStatus(OrderStatus.CONFIRMED);
        order.setReason(null);
        orderRepository.save(order);

        events.publishEvent(new OrderPlacedEvent(order.getOrderId()));
        return true;
    }

    public List<Order> getAllOrders() {
        return orderRepository.findAllByOrderByCreatedAtDesc();
    }
}
