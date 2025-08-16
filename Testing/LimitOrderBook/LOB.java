package LimitOrderBook;

import java.util.*;

public class LOB {

    // In market an order only becomes trade when it is executed(bought/sold)

    HashMap<Integer, Order> orderMap = new HashMap<>();
    HashMap<Double, Limit> limitMap = new HashMap<>();
    Date date;
    Order order;

    public LOB() {
        date = new Date();
    }

    public Order placeOrder(int orderId, boolean buySell, double price, int shares) {
        // place order

        if (!orderMap.containsKey(orderId)) {
            long entryTime = date.getTime();
            order = new Order(orderId, buySell, price, shares, entryTime);
            if (buySell) {
                Limit buyLimit;
                orderMap.put(orderId, order);
                if (!limitMap.containsKey(price)) {
                    buyLimit = new Limit(price);
                    buyLimit.insert(order);
                    limitMap.put(price, buyLimit);
                } else {
                    buyLimit = limitMap.get(price);
                    buyLimit.insert(order);
                }
                System.out.println("Order Placed");
                // limit.display();
            } else if (!buySell) {
                Limit sellLimit;
                orderMap.put(orderId, order);
                if (!limitMap.containsKey(price)) {
                    sellLimit = new Limit(price);
                    sellLimit.insert(order);
                    limitMap.put(price, sellLimit);
                } else {
                    sellLimit = limitMap.get(price);
                    sellLimit.insert(order);
                }
                System.out.println("Order Placed");
                // limit.display();
            }

            return order;
        } else {
            System.out.println("Order with orderId " + orderId + " already exists");
            return null;
        }
    }
}
