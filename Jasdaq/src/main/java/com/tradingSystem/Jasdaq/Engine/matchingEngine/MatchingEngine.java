package com.tradingSystem.Jasdaq.Engine.matchingEngine;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.ArrayDeque;
import java.util.Queue;
import java.util.List;

import org.springframework.stereotype.Service;

import com.tradingSystem.Jasdaq.Engine.Order;
import com.tradingSystem.Jasdaq.Engine.OrderRepository;
import com.tradingSystem.Jasdaq.Engine.Trade;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.LOB.*;
import com.tradingSystem.Jasdaq.generator.IdGenerator;


@Service 
// this annotation makes it part of springboot project and indicates that this class handels buisness logic
public class MatchingEngine {
    // Different MatchingEngine instances are made for stocks of different companies
    // In market an order only becomes trade when it is executed(bought/sold)

    private HashMap<String, Order> orderMap = new HashMap<>();
    private HashMap<Long, Limit> buyLimitMap = new HashMap<>();
    private HashMap<Long, Limit> sellLimitMap = new HashMap<>();

    Trade trade;
    Queue<Trade> queue = new ArrayDeque<>();
    List<Trade> list=new LinkedList<>();
    private Order order;
    private LimitsRBTree buyTree = new LimitsRBTree(true);
    private LimitsRBTree sellTree = new LimitsRBTree(false);
    // red black tree is used to get best buy or sell price
    private long totalBuyShares = 0;
    private long totalSellShares = 0;
    // keeps tab of all the orders in a string
    private long currentPrice; // price at which last trade happened

    private Limit buyLimit;
    private Limit sellLimit;

    private String symbol;

    public void setSymbol(String sym) {
        this.symbol = sym;
    }


    public void loadOrderMap(String compayId, OrderRepository orderRepository){
        for(Order order:orderRepository.findByCompanyCompanyId(compayId)){
            if(!order.status){
                placeOrder(order);
            }
        }
        // System.out.println();
        // System.out.println("company id:"+compayId);
        // System.out.println("Map");
        // System.out.println(">> "+buyLimitMap.toString());
        // System.out.println();
    }

    public long getBuyShares() {
        // return total buy orders in the book
        return totalBuyShares;
    }

    public long getSellShares() {
        // return total buy orders in the book
        return totalSellShares;
    }

    public long getTotalShares() {
        // total shares in book
        return totalBuyShares + totalSellShares;
    }

    public long getCurrentPrice() {
        // current price of the stock
        return currentPrice;
    }

    public record TradeResults(List<Trade> list,Order order) {
        // java records is a class with certain common functions pre-implemented
        /*
         * Getters
         * Constructors
         * equals etc.
         * all above boilerplate functions come pre implemented in records
         * Records are immutable, so you cannot change queue or order after creation
         */
    }

    public TradeResults addOrder(String orderId, boolean buySell, boolean marketLimit, long price, int shares) {
        if (!orderMap.containsKey(orderId)) {
            list.clear(); // FIX: Clear trades from previous operations
            long entryTime = System.currentTimeMillis(); // get time when order is placed
            order = new Order(orderId, symbol, buySell, price, shares, entryTime, marketLimit);
            if (marketLimit) {
                // if it is a market order
                Order order1 = executeMarketOrder(order);
                if (order1 == null) {
                    return null;
                } else {
                    return new TradeResults(new LinkedList<>(list), order1);
                }
            } else {
                OrderWrapper wrap;
                if (buySell) {
                    // buy order
                    wrap = executeLimitOrder(order, sellTree, totalSellShares); // try executing incoming order first
                    totalSellShares = wrap.getShares();
                } else {
                    // sell order
                    wrap = executeLimitOrder(order, buyTree, totalBuyShares);
                    totalBuyShares = wrap.getShares();
                }
                order = wrap.getOrder();
                
                if (order.shares > 0) {
                    order = placeOrder(order); // place remaining shares in book
                } else {
                    order.status = true;
                }
                return new TradeResults(new LinkedList<>(list), order);
            }
        } else {
            System.out.println("Order with orderId " + orderId + " already exists");
            return null;
        }
    }

    public Order cancelOrder(String orderId) {
        // cancel orders
        Limit limit;
        if (orderMap.containsKey(orderId)) {
            Order order = orderMap.get(orderId);
            if (order.status) {
                // if order is laready executed it cannot be canceled
                System.out.println("Order is already executed");
                return null;
            }
            long price = order.getPrice();
            if (order.buySell) {
                limit = buyLimitMap.get(price);
            } else {
                limit = sellLimitMap.get(price);
            }
            order.eventTime = System.currentTimeMillis();
            order.status = true;
            if (order.buySell) {
                totalBuyShares -= order.shares;
            } else {
                totalSellShares -= order.shares;
            }
            orderMap.remove(orderId); // remove order from order map
            if (limit != null) {
                limit.delete(orderId); // delete order from list
                if (limit.isEmpty()) {
                    if (limit.getLimit()) {
                        // it is a buy limit
                        buyTree.delete(limit);
                    } else {
                        // it is a sell limit
                        sellTree.delete(limit);
                    }
                }
                return order;
            } else {
                System.out.println(price + " level does not exist");
                return null;
            }
        } else {
            System.out.println("Order id " + orderId + " does not exist");
            return null;
        }
    }

    private OrderWrapper executeLimitOrder(Order incomingOrder, LimitsRBTree tree, long totalShares) {
        Limit limit = tree.bestPrice(); // get priced limit from passed tree
        boolean isBuy = incomingOrder.buySell;
        if (limit != null) {
            if ((isBuy) ? incomingOrder.getPrice() >= limit.getPrice() : incomingOrder.getPrice() <= limit.getPrice()) {
                // if incoming orders price matches previous orders in the book
                return executeOrder(incomingOrder, tree, totalShares);
            } else {
                // when prices does not match
                return new OrderWrapper(incomingOrder, totalShares);
            }
        } else {
            // book is empty
            return new OrderWrapper(incomingOrder, totalShares);
        }
    }

    private Order executeMarketOrder(Order order) {
        // market orders are executed as soon as they are placed
        if (order == null) {
            System.out.println("Null Order");
            return null;
        } else if (order.status) {
            System.out.println(order.toString() + " is already executed");
            return null;
        }
        list.clear(); // FIX: Clear trades
        OrderWrapper wrap;
        if (order.buySell) {
            if (sellTree.isEmpty()) {
                System.out.println("Rejecting buy market order: no liquidity");
                return null;
            } else {
                // Buy market: match against sell side until empty or filled
                wrap = executeOrder(order, sellTree, totalSellShares);
                totalSellShares = wrap.getShares();
                return wrap.getOrder();
            }
        } else {
            // Sell market: match against buy side
            if (buyTree.isEmpty()) {
                System.out.println("Rejecting sell market order: no liquidity");
                return null;
            } else {
                wrap = executeOrder(order, buyTree, totalBuyShares);
                totalBuyShares = wrap.getShares();
                return wrap.getOrder();
            }
        }
    }

    private OrderWrapper executeOrder(Order incomingOrder, LimitsRBTree tree, long totalShares) {
        Limit limit = tree.bestPrice(); // returns limit with least price
        int remainingShares = incomingOrder.shares;
        boolean marketLimit = incomingOrder.marketLimit; // market order or limit order
        boolean isBuy = incomingOrder.buySell;
        long lastMatchedPrice = 0;

        while (remainingShares > 0 && limit != null) {

            if (!marketLimit) {
                // if limit order
                if (isBuy && incomingOrder.getPrice() < limit.getPrice()) {
                    break;
                } else if (!isBuy && incomingOrder.getPrice() > limit.getPrice()) {
                    break;
                }
            }
            Order order = limit.getHead();
            if (order == null) {
                tree.delete(limit);
                limit = tree.bestPrice();
                continue;
            }
            
            lastMatchedPrice = order.getPrice();
            int matchQuantity = Math.min(remainingShares, order.shares);
            
            trade = new Trade(IdGenerator.nextID(symbol, 't'), 
                             isBuy ? incomingOrder.orderId : order.orderId, 
                             isBuy ? order.orderId : incomingOrder.orderId,
                             order.getPrice(), matchQuantity, symbol);
            list.add(trade);

            if (order.shares == matchQuantity) {
                limit.pop();
                orderMap.remove(order.orderId);
                order.status = true;
                order.eventTime = System.currentTimeMillis();
                totalShares -= matchQuantity;
                if (limit.isEmpty()) {
                    tree.delete(limit);
                }
            } else {
                order.shares -= matchQuantity;
                totalShares -= matchQuantity;
                limit.limitVolume -= matchQuantity;
            }
            
            remainingShares -= matchQuantity;
            limit = tree.bestPrice();
        }
        
        incomingOrder.shares = remainingShares;
        if (lastMatchedPrice != 0) {
            incomingOrder.finalPrice = lastMatchedPrice;
            incomingOrder.eventTime = System.currentTimeMillis();
            currentPrice = lastMatchedPrice; 
        }
        
        if (remainingShares == 0) {
            incomingOrder.status = true;
        }

        return new OrderWrapper(incomingOrder, totalShares);
    }

    private Order placeOrder(Order order) {
        // place order recognizes order type(buy or sell) and insert it into respective Limits
        order.marketLimit = false; // limit order
        orderMap.put(order.orderId, order); // insert order in order map
        order.finalPrice = order.getPrice(); // limit orders sell only at asked prices
        if (order.buySell) {
            // buy order
            totalBuyShares += order.shares;
            Order buy = buyOrder(order, order.getPrice(), order.orderId);
            return buy;
        } else {
            // sell order
            totalSellShares += order.shares;
            Order sell = sellOrder(order, order.getPrice(), order.orderId);
            return sell;
        }
    }

    private Order buyOrder(Order order, long unitPrice, String orderId) {
        if (!buyLimitMap.containsKey(unitPrice)) {
            // if limitMap does not contains price level
            buyLimit = new Limit(unitPrice, true);
            buyLimit.insert(order);// inser torder in new limit
            buyLimitMap.put(unitPrice, buyLimit); // add new limit to limit map
            buyTree.insert(buyLimit);
        } else {
            // limitmap contains list instance for given price
            buyLimit = buyLimitMap.get(unitPrice);
            if (buyLimit.isEmpty()) {
                // insert new price level in tree
                buyTree.insert(buyLimit);
            }
            buyLimit.insert(order); // insert order in tree
        }
        return order;
    }

    private Order sellOrder(Order order, long unitPrice, String orderId) {
        if (!sellLimitMap.containsKey(unitPrice)) {
            // if limit map does not contain limit with given price
            sellLimit = new Limit(unitPrice, false); // initialize new limit list
            sellLimit.insert(order); // insert new sell order in list
            sellLimitMap.put(unitPrice, sellLimit); // put limit in limit map
            sellTree.insert(sellLimit); // insert limit in tree
        } else {
            sellLimit = sellLimitMap.get(unitPrice);
            if (sellLimit.isEmpty()) {
                // insert price level in tree
                sellTree.insert(sellLimit);
            }
            sellLimit.insert(order);
        }
        return order;
    }

    public void displayBook() {
        // Display book data
        Limit buy = buyTree.bestPrice();
        Limit sell = sellTree.bestPrice();

        // System.out.println("\nBuy Limits:");
        // for (Limit i : buyLimitMap.values()) {
        // i.display();
        // }

        // System.out.println("\nSell Limits:");
        // for (Limit i : sellLimitMap.values()) {
        // i.display();
        // }

        System.out.println("\nBest buy price:" + (buy == null ? "-" : buy.getPrice()));
        System.out.println("Best sell price:" + (sell == null ? "-" : sell.getPrice()));
        System.out.println("Current price of stock:" + currentPrice);
        System.out.println("Total Shares to buy:" + getBuyShares());
        System.out.println("Total Shares to sell:" + getSellShares());
        System.out.println("Total Shares in book:" + getTotalShares());
    }

    private class OrderWrapper {

        Order order;
        long totalShares;

        public OrderWrapper(Order order, long totalShares) {
            this.totalShares = totalShares;
            this.order = order;
        }

        public Order getOrder() {
            return order;
        }

        public long getShares() {
            return totalShares;
        }
    }

}

// we don't delete list from hashmaps after it is empty so that we don't have to
// create new list instance when order of same price is placed
// thus preventing GC from running speeding up our program