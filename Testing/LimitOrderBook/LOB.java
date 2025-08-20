package LimitOrderBook;
import java.util.*;

public class LOB {
    // Different LOB instances are made for stocks of different companies
    // In market an order only becomes trade when it is executed(bought/sold)

    HashMap<Integer, Order> orderMap = new HashMap<>();
    HashMap<Double, Limit> limitMap = new HashMap<>();
    Date date;
    Order order;
    LimitsRBTree buyTree = new LimitsRBTree(true);
    LimitsRBTree sellTree = new LimitsRBTree(false);
    // red black tree is used to get best buy or sell price
    Limit buyLimit;
    Limit sellLimit;

    public Order placeOrder(int orderId, boolean buySell, double price, int shares) {
        // place order
        if (!orderMap.containsKey(orderId)) {
            double unitPrice = shares / price;
            long entryTime = System.currentTimeMillis(); // get time when order is placed
            order = new Order(orderId, buySell, price, unitPrice, shares, entryTime);
            if (buySell) {
                // if buy order
                orderMap.put(orderId, order); // insert order in order map
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
            } else if (!buySell) {
                // sell order
                orderMap.put(orderId, order); // add order to order map
                if (!limitMap.containsKey(unitPrice)) {
                    // if limit map contains limit with given price
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
            }
            // System.out.println("Order Placed");
            return order;
        } else {
            System.out.println("Order with orderId " + orderId + " already exists");
            return null;
        }
    }

    public Order cancelOrder(int orderId) {
        // cancel orders
        if (orderMap.containsKey(orderId)) {
            Order order = orderMap.get(orderId);
            if (limitMap.containsKey(order.getUnitPrice())) {
                Limit limit = limitMap.get(order.getUnitPrice());
                orderMap.remove(orderId); // remove order from order map
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
                System.out.println("Order not in Limit");
                return null;
            }
        } else {
            System.out.println("No such Order exist");
            return null;
        }
    }

    public void execute() {
        // execute order
        System.out.println("\nBuy tree:");
        buyTree.display();
        System.out.println("Sell tree:");
        sellTree.display();

        System.out.println("\nBest buy price:" + buyTree.bestPrice().getPrice());
        System.out.println("Best sell price:" + sellTree.bestPrice().getPrice());

        System.out.println("\nBuy Limit:");
        buyLimit.display();
        System.out.println("Sell Limit:");
        sellLimit.display();

    }

}

// we don't delete list from hashmaps after it is empty so that we don't have to
// create new list when order of same price is placed
// speeding up our program