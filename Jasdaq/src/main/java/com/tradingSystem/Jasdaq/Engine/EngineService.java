package com.tradingSystem.Jasdaq.Engine;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.TradeEngine;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.MatchingEngine.TradeResults;
import com.tradingSystem.Jasdaq.companies.CompanyService;
import com.tradingSystem.Jasdaq.companies.PlaceOrderEvent;
import com.tradingSystem.Jasdaq.Engine.net.MulticastBroadcaster;

// this class will interact with frontend and handle orders and trades
@Service
// this annotation makes it part of springboot project and indicates that this
// class handels buisness logic
public class EngineService {

    @Autowired
    private CompanyService companyService;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private KafkaTemplate<String, String> kafkaTemplate;

    @Autowired
    private RedisTemplate<String, String> redisTemplate;
    // String and String means both key and value of this would be string

    @Autowired
    private ObjectMapper objectMapper; // converts objects into json

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
        CompletableFuture<Object> obj = engine.submitAddRequest(buySell, price, shares, marketLimit);

        obj.whenComplete((result, throwables) -> {
            if (throwables != null) {
                throwables.printStackTrace();
                return;
            }

            if (result instanceof TradeResults tradeResults) {
                List<Trade> list = tradeResults.list();
                Order order = tradeResults.order();
                order.setCompany(companyService.singleCompany(companyId).get());

                companyService.setCurrentPrice(price, companyId);
                saveToRedis(order, list);
                saveToKafka(order, list);

                wsTemplate.convertAndSendToUser(order.orderId, "queue/orders", order);

                String marketUpdate = buildBroadcastMessage(companyId, order);
                multicast.broadcastAsync(marketUpdate);
            }
        });
    }

    public void cancelOrder(String orderId, String companyId) {

        TradeEngine engine = companyService.getTradeEngine(companyId);
        CompletableFuture<Object> obj = engine.submitCancelrequest(orderId);

        obj.whenComplete((result, throwables)->{

            if (throwables != null) {
                throwables.printStackTrace();
                return;
            }
            if (result instanceof Order order) {
                wsTemplate.convertAndSendToUser(order.orderId, "queue/orders", order);
                deleteFromDatabaseAsync(order);
            }
        });
    }

    // helper function
    @Async("persistenceExecutor") // makes function async
    private void saveOrderToDatabaseAsync(Order order) {
        try {
            orderRepository.save(order);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @Async("persistenceExecutor")
    private void saveTradeToDatabase(Trade trade){
        try{
            tradeRepository.save(trade);
        }
        catch(Exception e){
            e.printStackTrace();
        }
    }

    @Async("persistenceExecutor") // makes function async
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

    private void saveToRedis(Order order, List<Trade> list) {
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            redisTemplate.opsForValue().set("order" + order.orderId, orderJson);
            if (list != null) {
                for (Trade t : list) {
                    String tradeJson = objectMapper.writeValueAsString(t);
                    redisTemplate.opsForValue().set("trade" + t.getTradeId(), tradeJson);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

    }

    private void saveToKafka(Order order, List<Trade> list) {
        String objectId = order.orderId;
        try {
            String orderJson = objectMapper.writeValueAsString(order);
            kafkaTemplate.send("orders-topic", objectId, orderJson);

            if (list != null) {
                for (Trade t : list) {
                    String tradeJson = objectMapper.writeValueAsString(t);
                    objectId = t.getTradeId();
                    kafkaTemplate.send("trades-topic", objectId, tradeJson);
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
            sendUserWsError(objectId, null);
        }
    }

    @KafkaListener(topics = "orders-topic", groupId = "my-group")
    public void kafkaOrderConsumer(String message){
        System.out.println("Received Message"+message);
        try {
        Order order=objectMapper.readValue(message, Order.class);
        System.out.println(">> Received Order:"+order.toString());
        saveOrderToDatabaseAsync(order);
        } catch (Exception e) {
            System.out.println(">> Error Trace:");
            e.printStackTrace();
        }
    }

    @KafkaListener(topics = "trades-topic", groupId = "my-group")
    public void kafkaTradeConsumer(String message){
        System.out.println("Received Message"+message);
        try {
            Trade trade=objectMapper.readValue(message, Trade.class);
            System.out.println(">> Received Trade:"+trade.toString());
            saveTradeToDatabase(trade);
        } catch (Exception e) {
            System.out.println(">> Error Trace:");
            e.printStackTrace();
        }
    }

    private void sendUserWsError(String objectId, String message) {
        wsTemplate.convertAndSendToUser(objectId, "/queue/orders", Map.of("error:", message));
    }
}
