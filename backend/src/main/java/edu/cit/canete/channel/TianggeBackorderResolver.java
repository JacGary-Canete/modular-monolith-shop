package edu.cit.canete.channel;

import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import edu.cit.canete.inventory.event.StockChangedEvent;
import edu.cit.canete.shop.OrderService;

/**
 * Listens for stock changes (Task 3's event) and checks whether any
 * BACKORDERED Tiangge order can now be fulfilled. Reuses OrderService's
 * existing all-or-nothing reservation logic - no special-case stock
 * manipulation here. Resolves ACCEPTED through Tiangge once fulfilled
 * (Task 6). Order and Inventory still never know Tiangge exists; this
 * class is the only bridge.
 *
 * AFTER_COMMIT, same reasoning as TianggeStockSyncListener: this method
 * calls out to Tiangge over HTTP (resolveBackorder), which can be slow
 * or hang. Running it only after the triggering stock change has fully
 * committed means we're never holding a DB connection open while
 * waiting on that network call.
 */
@Component
class TianggeBackorderResolver {

    private final ChannelOrderRepository channelOrderRepository;
    private final OrderService orderService;
    private final TianggeGateway gateway;

    TianggeBackorderResolver(ChannelOrderRepository channelOrderRepository,
                              OrderService orderService,
                              TianggeGateway gateway) {
        this.channelOrderRepository = channelOrderRepository;
        this.orderService = orderService;
        this.gateway = gateway;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onStockChanged(StockChangedEvent event) {
        List<ChannelOrder> openBackorders = channelOrderRepository.findByDecisionAndResolved("BACKORDERED", false);

        for (ChannelOrder channelOrder : openBackorders) {
            try {
                boolean fulfilled = orderService.tryFulfillBackorder(channelOrder.getOrderId());
                if (fulfilled) {
                    channelOrder.setResolution("ACCEPTED");
                    channelOrderRepository.save(channelOrder);

                    gateway.resolveBackorder(channelOrder.getTiangeOrderId(), "ACCEPTED");
                    System.out.println("Backorder " + channelOrder.getTiangeOrderId()
                            + " (our order #" + channelOrder.getOrderId() + ") resolved: ACCEPTED");
                }
            } catch (Exception e) {
                System.out.println("Failed to resolve backorder " + channelOrder.getTiangeOrderId()
                        + ": " + e.getClass().getSimpleName() + " - " + e.getMessage());
            }
        }
    }
}