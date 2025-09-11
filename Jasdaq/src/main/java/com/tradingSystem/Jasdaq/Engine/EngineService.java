package com.tradingSystem.Jasdaq.Engine;

import java.util.Queue;
import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.TradeEngine;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.MatchingEngine.TradeResults;
import com.tradingSystem.Jasdaq.companies.CompanyService;

// this class will interact with frontend and handle orders and trades
@Service // this annotation makes it part of springboot project and indicates that this
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

    public void placeOrder(boolean buySell, long price, int shares, boolean marketLimit, String companyId) {

        TradeEngine engine = companyService.getTradeEngine(companyId);
        CompletableFuture<Object> obj = engine.submitAddRequest(buySell, price, shares, marketLimit);

        obj.whenComplete((result, throwables)->{
            if(throwables!=null){
                throwables.printStackTrace();
                return;
            }

            if(result instanceof TradeResults tradeResults){
                Queue<Trade> queue= tradeResults.queue();
                Order order=tradeResults.order();
                
                saveToDatabaseAsync(queue, order);
                
                saveOrderToKafka(order);
                saveTradeToKafka(queue);

                saveOrderToRedis(order);
                saveTradeToRedis(queue);
            }
        });


    }

    public Order cancelOrder(String orderId, String companyId) {

        TradeEngine engine = companyService.getTradeEngine(companyId);
        CompletableFuture<Object> obj = engine.submitCancelrequest(orderId);

        try {
            Object result = obj.get();

            if (result instanceof Order order) {
                return order;
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

    }

    // helper function

    private void saveToDatabaseAsync(Queue<Trade> queue, Order order) {

        CompletableFuture.runAsync(() -> {

            try {
                orderRepository.save(order);

                while (!queue.isEmpty()) {
                    tradeRepository.save(queue.poll());
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

        });
    }

    private void saveOrderToKafka(Order order){

    }

    private void saveTradeToKafka(Queue<Trade> queue){

    }

    private void saveOrderToRedis(Order order){

    }

    private void saveTradeToRedis(Queue<Trade> queue){

    }

}
