package com.tradingSystem.Jasdaq.Engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.TradeEngine;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.MatchingEngine.TradeResults;
import com.tradingSystem.Jasdaq.companies.Companies;
import com.tradingSystem.Jasdaq.companies.CompanyService;
import com.tradingSystem.Jasdaq.companies.PlaceOrderEvent;
import com.tradingSystem.Jasdaq.Engine.net.MulticastBroadcaster;

@Service
public class EngineService {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private SimpMessagingTemplate wsTemplate;

    @Autowired
    private MulticastBroadcaster multicast;

    @EventListener
    public void handleEvent(PlaceOrderEvent event) {
        placeOrder(event.getBuySell(), event.getPrice(), event.getShares(), event.getMarketLimit(),
                event.getCompanyId());
    }

    public void placeOrder(boolean buySell, long price, int shares, boolean marketLimit, String companyId) {

        TradeEngine engine = companyService.getTradeEngine(companyId);
        if (engine == null) {
            System.err.println("[ERROR] No TradeEngine found for companyId=" + companyId);
            broadcastRejection(companyId, null, "Trading engine not initialized for this company. Please restart the server.");
            return;
        }
        CompletableFuture<Object> obj = engine.submitAddRequest(buySell, price, shares, marketLimit);

        obj.whenComplete((result, throwables) -> {
            if (throwables != null) {
                throwables.printStackTrace();
                broadcastRejection(companyId, null, "Internal engine error: " + throwables.getMessage());
                return;
            }

            if (result instanceof TradeResults tradeResults) {
                List<Trade> list = tradeResults.list();
                Order order = tradeResults.order();

                if (order == null) {
                    String side = buySell ? "BUY" : "SELL";
                    String opposite = buySell ? "SELL" : "BUY";
                    String reason = side + " market order rejected: no " + opposite
                            + " orders available in the book to match against. Try placing a LIMIT order instead.";
                    System.err.println("[WARN] " + reason);
                    broadcastRejection(companyId, null, reason);
                    return;
                }

                // 1. Update current price in DB
                long executedPrice = order.getFinalPrice() > 0 ? order.getFinalPrice() : order.getPrice();
                companyService.setCurrentPrice(executedPrice, companyId);

                // 2. Persist order + trades directly to DB (primary path – no Kafka dependency)
                Companies company = companyService.getCompanyById(companyId);
                order.setCompany(company);
                saveOrderToDatabaseAsync(order);

                if (list != null) {
                    for (Trade t : list) {
                        t.setCompany(company);
                        saveTradeToDatabase(t);
                    }
                }

                // 3. Best-effort: Removed Redis and Kafka

                // 4. Always send WebSocket confirmation to ALL subscribers (broadcast)
                wsTemplate.convertAndSend("/topic/orders", order);

                // 5. Broadcast market price update
                Map<String, Object> update = Map.of(
                    "companyId", companyId,
                    "price", executedPrice,
                    "timestamp", System.currentTimeMillis()
                );
                wsTemplate.convertAndSend("/topic/market-updates", update);

                // 6. Best-effort multicast broadcast
                try {
                    String marketUpdate = buildBroadcastMessage(companyId, order);
                    multicast.broadcastAsync(marketUpdate);
                } catch (Exception e) {
                    System.err.println("[WARN] Multicast failed: " + e.getMessage());
                }
            }
        });
    }

    public void cancelOrder(String orderId, String companyId) {

        TradeEngine engine = companyService.getTradeEngine(companyId);
        if (engine == null) {
            System.err.println("[ERROR] No TradeEngine found for cancelOrder, companyId=" + companyId);
            return;
        }
        CompletableFuture<Object> obj = engine.submitCancelrequest(orderId);

        obj.whenComplete((result, throwables) -> {
            if (throwables != null) {
                throwables.printStackTrace();
                return;
            }
            if (result instanceof Order order) {
                wsTemplate.convertAndSend("/topic/orders", order);
                deleteFromDatabaseAsync(order);
            }
        });
    }

    public CompletableFuture<Object> getMarketMetrics(String companyId) {
        TradeEngine engine = companyService.getTradeEngine(companyId);
        if (engine == null) {
            return CompletableFuture.completedFuture(null);
        }
        return engine.submitMetricsRequest();
    }

    @Async("persistenceExecutor")
    private void saveOrderToDatabaseAsync(Order order) {
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Async("persistenceExecutor")
    private void saveTradeToDatabase(Trade trade) {
        try {
            tradeRepository.save(trade);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Async("persistenceExecutor")
    private void deleteFromDatabaseAsync(Order order) {
        try {
            orderRepository.delete(order);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private String buildBroadcastMessage(String companyId, Order order) {
        Map<String, Object> m = new HashMap<>();
        try {
            m.put("Company id", companyId);
            m.put("current price", order.finalPrice);
            m.put("timestamp", order.eventTime);
            return objectMapper.writeValueAsString(m);
        } catch (Exception e) {
            e.printStackTrace();
            return companyId + "-" + order.finalPrice + order.eventTime;
        }
    }

    private void sendUserWsError(String objectId, String message) {
        wsTemplate.convertAndSendToUser(objectId, "/queue/orders", Map.of("error:", message));
    }

    /**
     * Broadcasts an order rejection notification to all connected frontend clients.
     * Frontend subscribes to /topic/order-rejected to receive these.
     */
    private void broadcastRejection(String companyId, String symbol, String reason) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("companyId", companyId != null ? companyId : "");
        payload.put("symbol", symbol != null ? symbol : "");
        payload.put("reason", reason);
        payload.put("timestamp", System.currentTimeMillis());
        wsTemplate.convertAndSend("/topic/order-rejected", payload);
        System.err.println("[ORDER REJECTED] companyId=" + companyId + " | " + reason);
    }
}
