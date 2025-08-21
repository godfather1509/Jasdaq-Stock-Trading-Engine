package LimitOrderBook;

import java.util.*;

public class LOB {
    // Different LOB instances are made for stocks of different companies
    // In market an order only becomes trade when it is executed(bought/sold)

    private HashMap<Integer, Order> orderMap = new HashMap<>();
    private HashMap<Long, Limit> limitMap = new HashMap<>();
    private Order order;
    private LimitsRBTree buyTree = new LimitsRBTree(true);
    private LimitsRBTree sellTree = new LimitsRBTree(false);
    // red black tree is used to get best buy or sell price
    private long totalBuyShares = 0;
    private long totalSellShares = 0;
    // keeps tab of all the orders in a string
    double currentPrice;

    private Limit buyLimit;
    private Limit sellLimit;

    public void addOrder(int orderId, boolean buySell, boolean marketLimit, long price, int shares) {
        if (!orderMap.containsKey(orderId)) {
            long entryTime = System.currentTimeMillis(); // get time when order is placed
            order = new Order(orderId, buySell, price, shares, entryTime, marketLimit);
            if (marketLimit) {
                // if it is a market order
                executeOrder(order);
                // market order is order that executes immediately at best available price
            } else {
                // if it is a limit order
                // limit order is order that holds till asked price is available
                orderMap.put(orderId, order); // insert order in order map
                // market order do not go in order map as they are exceuted instantaneoulsy
                placeOrder(order);
            }
        } else {
            System.out.println("Order with orderId " + orderId + " already exists");
            return;
        }

    }

    public Order cancelOrder(int orderId) {
        // cancel orders
        if (orderMap.containsKey(orderId)) {
            Order order = orderMap.get(orderId);
            long price = order.getPrice();
            Limit limit = limitMap.get(price);
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

    private void executeOrder(Order order) {

        if (order.buySell) {
            // it is a buy order
            if (sellTree.isEmpty() || totalBuyShares < order.shares) {
                // there is no one sell order tree is empty or does not have enough sell orders
                placeOrder(order);
                // if the book is empty or does not have enough volume then order will be
                // converted to Limit order
            } else {
                executeBuyOrder(order);
            }
        } else {
            // it is a sell order
            if (sellTree.isEmpty() || totalSellShares < order.shares) {
                placeOrder(order);
            }
            executeSellOrder(order);
        }
    }

    private Order placeOrder(Order order) {
        // place order
        if (order.buySell) {
            // if buy order
            totalBuyShares += order.shares;
            return buyOrder(order, order.getPrice(), order.orderId);
        } else {
            // sell order
            totalSellShares += order.shares;
            return sellOrder(order, order.getPrice(), order.orderId);
        }
        // System.out.println("Order Placed");
    }

    public Order buyOrder(Order order, long unitPrice, int orderId) {
        if (!limitMap.containsKey(unitPrice)) {
            // if limitMap does not contains price level
            buyLimit = new Limit(unitPrice, true);
            buyLimit.insert(order);// inser torder in new limit
            limitMap.put(unitPrice, buyLimit); // add new limit to limit map
            buyTree.insert(buyLimit);
        } else {
            // limitmap contains list instance for given price
            buyLimit = limitMap.get(unitPrice);
            if (buyLimit.isEmpty()) {
                // limit is empty insert but object is present in hashmap will have to insert it
                // in buytree
                buyTree.insert(buyLimit);
            }
            buyLimit.insert(order);
        }
        return order;
    }

    public Order sellOrder(Order order, long unitPrice, int orderId) {
        if (!limitMap.containsKey(unitPrice)) {
            // if limit map does not contain limit with given price
            sellLimit = new Limit(unitPrice, false); // initialize new limit list
            sellLimit.insert(order); // insert new sell order in list
            limitMap.put(unitPrice, sellLimit); // put limit in limit map
            sellTree.insert(sellLimit); // insert limit in tree
        } else {
            sellLimit = limitMap.get(unitPrice);
            if (sellLimit.isEmpty()) {
                sellTree.insert(sellLimit);
            }
            sellLimit.insert(order);
        }
        return order;
    }

    private void executeBuyOrder(Order incomingOrder) {
        // incoming order looking to buy shares
        Limit limit = buyTree.bestPrice();
        long buyPrice = limit.getPrice();
        incomingOrder.finalPrice = buyPrice;
        incomingOrder.status = true;
        int shares = incomingOrder.shares;
        while (shares > 0) {
            Order order = limit.getHead(); // get oldest order first
            if (limit.isEmpty()) {
                limit = buyTree.bestPrice();
            }
            if (order.shares == incomingOrder.shares) {
                limit.pop();
                orderMap.remove(order.orderId);
                totalSellShares -= order.shares;
                order.status = true;
                if (limit.isEmpty()) {
                    // remove limit from tree if its empty
                    if (limit.getLimit()) {
                        // it is a buy limit
                        buyTree.delete(limit);
                    } else {
                        // it is a sell limit
                        sellTree.delete(limit);
                    }
                }
                return;
            }
            if (order.shares > incomingOrder.shares) {
                order.shares -= incomingOrder.shares;
                totalSellShares -= incomingOrder.shares;
                return;
            } else {
                // shares of incoming order are greater than shares of order
                shares -= order.shares;
                limit.pop();
                totalSellShares -= order.shares;
                if (limit.isEmpty()) {
                    // remove limit from tree if its empty
                    if (limit.getLimit()) {
                        // it is a buy limit
                        buyTree.delete(limit);
                    } else {
                        // it is a sell limit
                        sellTree.delete(limit);
                    }
                }
            }
        }
    }

    private void executeSellOrder(Order order) {

    }

    public void displayBook() {
        // execute order
        System.out.println("\nBuy tree:");
        buyTree.display();
        System.out.println("Sell tree:");
        sellTree.display();

        System.out.println("\nBest buy price:" + buyTree.bestPrice().getPrice());
        System.out.println("Best sell price:" + sellTree.bestPrice().getPrice());

        System.out.println("\nLimits:");
        for (Limit i : limitMap.values()) {
            i.display();
        }
    }

    public long getBuyOrder() {
        // return total buy orders in the book
        return totalBuyShares;
    }

    public long getSellOrder() {
        // return total buy orders in the book
        return totalSellShares;
    }
}

// we don't delete list from hashmaps after it is empty so that we don't have to
// create new list instance when order of same price is placed
// thus preventing GC from running speeding up our program