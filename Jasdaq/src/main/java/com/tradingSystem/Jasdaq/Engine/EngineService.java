package com.tradingSystem.Jasdaq.Engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
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

    private static final long STATS_CACHE_TTL_MS = 5_000;
    private static final long BROADCAST_MIN_INTERVAL_MS = 200;

    private volatile Map<String, Object> cachedStats = null;
    private volatile long statsCachedAt = 0;

    private final AtomicLong lastStatsBroadcastAt = new AtomicLong(0);

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
                event.getCompanyId(), event.isIpo());
    }

    public void placeOrder(boolean buySell, long price, int shares, boolean marketLimit, String companyId) {
        placeOrder(buySell, price, shares, marketLimit, companyId, false, false);
    }

    public void placeOrder(boolean buySell, long price, int shares, boolean marketLimit, String companyId, boolean isIpo) {
        placeOrder(buySell, price, shares, marketLimit, companyId, isIpo, false);
    }

    public void placeOrder(boolean buySell, long price, int shares, boolean marketLimit, String companyId, boolean isIpo, boolean isCompanyOrder) {
        boolean treatAsCompany = isIpo || isCompanyOrder;

        // User SELL orders are only allowed if the user pool has enough shares.
        // Company/IPO SELL orders bypass this check — they represent supply, not user-owned shares.
        if (!buySell && !treatAsCompany) {
            if (!companyService.reserveSharesForSell(companyId, shares)) {
                broadcastRejection(companyId, null,
                        "Sell order rejected: you cannot sell shares you do not own. " +
                        "Buy shares first before placing a sell order.");
                return;
            }
        }

        TradeEngine engine = companyService.getTradeEngine(companyId);
        if (engine == null) {
            if (!buySell && !treatAsCompany) companyService.releaseSharesFromSell(companyId, shares);
            System.err.println("[ERROR] No TradeEngine found for companyId=" + companyId);
            broadcastRejection(companyId, null, "Trading engine not initialized for this company. Please restart the server.");
            return;
        }

        CompletableFuture<Object> obj = engine.submitAddRequest(buySell, price, shares, marketLimit, treatAsCompany);

        obj.whenComplete((result, throwables) -> {
            if (throwables != null) {
                throwables.printStackTrace();
                if (!buySell && !treatAsCompany) companyService.releaseSharesFromSell(companyId, shares);
                broadcastRejection(companyId, null, "Internal engine error: " + throwables.getMessage());
                return;
            }

            if (result == null) {
                if (!buySell && !treatAsCompany) companyService.releaseSharesFromSell(companyId, shares);
                String side = buySell ? "BUY" : "SELL";
                String opposite = buySell ? "SELL" : "BUY";
                String reason = side + " market order rejected: no " + opposite
                        + " orders available in the book to match against. Try placing a LIMIT order instead.";
                broadcastRejection(companyId, null, reason);
                return;
            }

            if (result instanceof TradeResults tradeResults) {
                List<Trade> list = tradeResults.list();
                Order order = tradeResults.order();

                if (order == null) {
                    if (!buySell && !treatAsCompany) companyService.releaseSharesFromSell(companyId, shares);
                    String side = buySell ? "BUY" : "SELL";
                    String opposite = buySell ? "SELL" : "BUY";
                    String reason = side + " market order rejected: no " + opposite
                            + " orders available in the book to match against. Try placing a LIMIT order instead.";
                    System.err.println("[WARN] " + reason);
                    broadcastRejection(companyId, null, reason);
                    return;
                }

                // 1. Update current price in DB only when a real trade occurred
                long executedPrice = order.getFinalPrice() > 0 ? order.getFinalPrice() : order.getPrice();
                boolean tradeOccurred = list != null && !list.isEmpty();
                if (tradeOccurred) {
                    companyService.setCurrentPrice(executedPrice, companyId);
                    // Credit the buyer's shares into the user ownership pool
                    if (buySell) {
                        int tradedQty = list.stream().mapToInt(Trade::getQuantity).sum();
                        companyService.creditBuyShares(companyId, tradedQty);
                    }
                }

                // 2. Persist order + trades directly to DB
                Companies company = companyService.getCompanyById(companyId);
                order.setCompany(company);
                saveOrderToDatabaseAsync(order);

                if (list != null) {
                    for (Trade t : list) {
                        t.setCompany(company);
                        saveTradeToDatabase(t);
                    }
                }

                // New: Handle affected existing orders (e.g., the partially filled IPO order)
                List<Order> affected = tradeResults.affectedOrders();
                if (affected != null) {
                    for (Order affectedOrder : affected) {
                        affectedOrder.setCompany(company);
                        saveOrderToDatabaseAsync(affectedOrder);
                        wsTemplate.convertAndSend("/topic/orders", affectedOrder);
                    }
                }

                // 3. Send WebSocket confirmation to ALL subscribers
                wsTemplate.convertAndSend("/topic/orders", order);

                // 4. Broadcast global market stats and price update only on real trades
                if (tradeOccurred) {
                    broadcastMarketStats();
                    Map<String, Object> update = Map.of(
                        "companyId", companyId,
                        "price", executedPrice,
                        "timestamp", System.currentTimeMillis()
                    );
                    wsTemplate.convertAndSend("/topic/market-updates", update);
                }

                // 5. Best-effort multicast broadcast
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

        // Block cancellation of company orders (IPO, relisting, admin-placed)
        Order existing = engine.lob.getOrder(orderId);
        if (existing != null && existing.companyOrder) {
            broadcastRejection(companyId, null,
                    "Company orders (IPO, relisting, etc.) cannot be cancelled.");
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
                // Return the remaining reserved shares to the user ownership pool
                if (!order.buySell) {
                    companyService.releaseSharesFromSell(companyId, order.shares);
                }
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

    public Map<String, Object> getGlobalMarketStats() {
        long now = System.currentTimeMillis();
        if (cachedStats != null && (now - statsCachedAt) < STATS_CACHE_TTL_MS) {
            return cachedStats;
        }
        Long totalVolume = tradeRepository.getTotalVolume();
        Double avgLatency = orderRepository.getAverageLatency();
        Map<String, Object> stats = new HashMap<>();
        stats.put("marketStatus", "Operational");
        stats.put("totalVolume", totalVolume != null ? totalVolume : 0L);
        stats.put("avgLatency", avgLatency != null ? avgLatency : 0.0);
        stats.put("timestamp", now);
        cachedStats = stats;
        statsCachedAt = now;
        return stats;
    }

    private void broadcastMarketStats() {
        long now = System.currentTimeMillis();
        long last = lastStatsBroadcastAt.get();
        if (now - last < BROADCAST_MIN_INTERVAL_MS) {
            return;
        }
        if (!lastStatsBroadcastAt.compareAndSet(last, now)) {
            return;
        }
        try {
            cachedStats = null; // invalidate cache so broadcast always sends fresh data
            wsTemplate.convertAndSend("/topic/market-stats", getGlobalMarketStats());
        } catch (Exception e) {
            System.err.println("[WARN] Failed to broadcast global market stats: " + e.getMessage());
        }
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
            System.err.println("[ERROR] Failed to delete order: " + order.getOrderId());
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
