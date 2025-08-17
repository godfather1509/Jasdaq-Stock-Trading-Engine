package LimitOrderBook;

import java.util.*;

public class LOB {

    // In market an order only becomes trade when it is executed(bought/sold)

    HashMap<Integer, Order> orderMap = new HashMap<>();
    HashMap<Double, Limit> limitMap = new HashMap<>();
    Date date;
    Order order;
    LimitsRBTree buyTree = new LimitsRBTree(true);
    LimitsRBTree sellTree = new LimitsRBTree(false);

    public LOB() {
        date = new Date();
    }

    public Order placeOrder(int orderId, boolean buySell, double price, int shares) {
        // place order

        if (!orderMap.containsKey(orderId)) {
            long entryTime = date.getTime(); // get time when order is placed
            order = new Order(orderId, buySell, price, shares, entryTime);
            if (buySell) {
                // if buy order
                Limit buyLimit;
                orderMap.put(orderId, order); // insert order in order map

                if (!limitMap.containsKey(price)) {
                    // if limitMap does not contains price level
                    buyLimit = new Limit(price);
                    buyLimit.insert(order);// inser torder in new limit
                    limitMap.put(price, buyLimit); // add new limit to limit map
                    buyTree.insert(buyLimit);
                } else {
                    buyLimit = limitMap.get(price);
                    buyLimit.insert(order);
                }
                // limit.display();
                // buyTree.display();
            } else if (!buySell) {
                Limit sellLimit;
                orderMap.put(orderId, order); // add order to order map
                if (!limitMap.containsKey(price)) {
                    // if limit map contains limit with given price
                    sellLimit = new Limit(price); // initialize new limit list
                    sellLimit.insert(order); // insert new sell order in list
                    limitMap.put(price, sellLimit); // put limit in limit map
                    sellTree.insert(sellLimit);
                } else {
                    sellLimit = limitMap.get(price);
                    sellLimit.insert(order);
                }
                // limit.display();
                // sellTree.display();
            }
            System.out.println("Order Placed");
            return order;
        } else {
            System.out.println("Order with orderId " + orderId + " already exists");
            return null;
        }
    }

    public void execute() {
        System.out.println("Best buy price:" + buyTree.bestPrice().getPrice());
        System.out.println("Best sell price:" + sellTree.bestPrice().getPrice());
        System.out.println("Buy tree:");
        buyTree.display();
        System.out.println("Sell tree:");
        sellTree.display();
    }

}
