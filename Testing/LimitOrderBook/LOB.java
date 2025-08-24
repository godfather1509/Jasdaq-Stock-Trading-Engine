package LimitOrderBook;

import java.util.*;

public class LOB {
    // Different LOB instances are made for stocks of different companies
    // In market an order only becomes trade when it is executed(bought/sold)

    private HashMap<Integer, Order> orderMap = new HashMap<>();
    private HashMap<Long, Limit> buyLimitMap = new HashMap<>();
    private HashMap<Long, Limit> sellLimitMap = new HashMap<>();

    private Order order;
    private LimitsRBTree buyTree = new LimitsRBTree(true);
    private LimitsRBTree sellTree = new LimitsRBTree(false);
    // red black tree is used to get best buy or sell price
    private int totalBuyShares = 0;
    private int totalSellShares = 0;
    // keeps tab of all the orders in a string
    private long currentPrice; // price at which last trade happened

    private Limit buyLimit;
    private Limit sellLimit;

    public int getBuyShares() {
        // return total buy orders in the book
        return totalBuyShares;
    }

    public int getSellShares() {
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

    public void addOrder(int orderId, boolean buySell, boolean marketLimit, long price, int shares) {
        if (!orderMap.containsKey(orderId)) {
            long entryTime = System.currentTimeMillis(); // get time when order is placed
            order = new Order(orderId, buySell, price, shares, entryTime, marketLimit);
            if (marketLimit) {
                // if it is a market order
                Order order1 = executeMarketOrder(order);
                System.out.println(order1.toString());
                // market order is order that executes immediately at best available price
            } else {
                /*
                 * if it is a limit order
                 * limit order is order that holds till asked price is available
                 * market order do not go in order map as they are exceuted instantaneoulsy
                 */
                boolean executed = false;
                if (buySell) {
                    // buy order
                    executed = executeLimitOrder(order, sellTree, totalSellShares); // this will sell shares
                } else {
                    // sell order
                    executed = executeLimitOrder(order, buyTree, totalBuyShares); // this will buy shares
                }
                /*
                 * once order is placed we check if it is matching with any of the current
                 * orders if yes it is executed
                 * else place it in book
                 */
                if (executed) {
                    return;
                } else {
                    placeOrder(order); // this will add order in book
                }
            }
        } else {
            System.out.println("Order with orderId " + orderId + " already exists");
            return;
        }
    }

    public Order cancelOrder(int orderId) {
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

    private Order placeOrder(Order order) {
        // place order recognizes order type(buy or sell) and insert it into respective
        // Limits

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

    private boolean executeLimitOrder(Order incomingOrder, LimitsRBTree tree, int totalShares) {
        Limit limit = tree.bestPrice(); // get priced limit from passed tree
        boolean isBuy = incomingOrder.buySell;
        if (limit != null) {
            if ((isBuy) ? incomingOrder.getPrice() >= limit.getPrice() : incomingOrder.getPrice() <= limit.getPrice()) {
                // if incoming orders price matches previous orders in the book
                Order result = executeOrder(incomingOrder, tree, totalShares);
                if (result == null)
                    return false;
                return result.shares == 0;
            } else {
                // when prices does not match
                System.out.println(incomingOrder.orderId + " Order did not match");
                return false;
            }
        } else {
            // book is empty
            System.out.println("Empty tree");
            return false;
        }
    }

    private Order executeMarketOrder(Order order) {
        // market orders are executed as soon as they are placed
        if (order == null || order.status) {
            System.out.println(order.toString() + " is either null or already executed");
            return null;
        }
        if (order.buySell) {
            if (sellTree.isEmpty()) {
                return null;
            } else {
                // Buy market: match against sell side until empty or filled
                return executeOrder(order, sellTree, totalSellShares); // remainder (if any) is zero for IOC
            }
        } else {
            // Sell market: match against buy side
            if (buyTree.isEmpty()) {
                return null;
            } else {
                return executeOrder(order, buyTree, totalBuyShares);
            }
        }
        // if a market order is cannot be executed fully then it is discarded
    }

    private Order buyOrder(Order order, long unitPrice, int orderId) {
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
                // limit is empty insert but object is present in hashmap will have to insert it
                // in buytree
                buyTree.insert(buyLimit);
            }
            buyLimit.insert(order);
        }
        return order;
    }

    private Order sellOrder(Order order, long unitPrice, int orderId) {
        if (!sellLimitMap.containsKey(unitPrice)) {
            // if limit map does not contain limit with given price
            sellLimit = new Limit(unitPrice, false); // initialize new limit list
            sellLimit.insert(order); // insert new sell order in list
            sellLimitMap.put(unitPrice, sellLimit); // put limit in limit map
            sellTree.insert(sellLimit); // insert limit in tree
        } else {
            sellLimit = sellLimitMap.get(unitPrice);
            if (sellLimit.isEmpty()) {
                sellTree.insert(sellLimit);
            }
            sellLimit.insert(order);
        }
        return order;
    }

    private Order executeOrder(Order incomingOrder, LimitsRBTree tree, int totalShares) {
        Limit limit = tree.bestPrice(); // returns limit with least price
        int remainingShares = incomingOrder.shares;
        boolean marketLimit = incomingOrder.marketLimit; // market order or limit order
        boolean isBuy = incomingOrder.buySell;
        while (remainingShares > 0) {

            if (limit == null) {
                incomingOrder.shares = remainingShares;
                incomingOrder.status = (remainingShares == 0);
                return incomingOrder;
                // execute order till shares are available if book becomes empty then partially
                // execute order
                // and return with remaining shares
            }

            Order order = limit.getHead();
            // get oldest order first
            if (order == null) {
                incomingOrder.shares = remainingShares;
                incomingOrder.status = (remainingShares == 0);
                return incomingOrder;
                // execute order till shares are available if book becomes empty then partially
                // execute order
                // and return with remaining shares
            }
            if (order.shares == remainingShares) {

                remainingShares = 0;
                incomingOrder.shares = remainingShares;
                incomingOrder.finalPrice = order.getPrice(); // update final price
                incomingOrder.status = true; // trade is executed
                incomingOrder.eventTime = System.currentTimeMillis();

                currentPrice = order.getPrice(); // update current price of share

                limit.pop(); // remove the 1st order from list
                limit.limitVolume -= order.shares;
                orderMap.remove(order.orderId); // remove the order from map
                order.status = true; // order is fulfilled/ executed
                order.eventTime = System.currentTimeMillis();

                totalShares -= order.shares; // update total available shares to sell

                if (limit.isEmpty()) {
                    // remove limit from tree if its empty
                    tree.delete(limit);
                    if (marketLimit) {
                        // if it is a market order update limit with new best order
                        limit = tree.bestPrice();
                    }
                }
            }
            if (order.shares < remainingShares) {
                // shares of incoming order are more than shares of order in the book
                remainingShares -= order.shares;
                order.eventTime = System.currentTimeMillis();
                limit.pop();
                limit.limitVolume -= order.shares;
                totalShares -= order.shares;
                if (limit.isEmpty()) {
                    // remove limit from tree if its empty
                    tree.delete(limit);
                    if (marketLimit) {
                        limit = tree.bestPrice();
                    }
                }
            } else {
                // shares of order in book are more
                order.shares -= remainingShares;
                totalShares -= remainingShares;
                limit.limitVolume -= remainingShares;
                remainingShares = 0;
                incomingOrder.finalPrice = order.getPrice(); // update final price
                incomingOrder.status = true; // trade is executed
                incomingOrder.shares = remainingShares;
                incomingOrder.eventTime = System.currentTimeMillis();
                currentPrice = order.getPrice(); // update current price of share
            }
        }
        if (isBuy) {
            // buy order
            totalSellShares = totalShares;
        } else {
            // sell order
            totalBuyShares = totalShares;
        }
        // update no of shares
        return incomingOrder;
    }

    public void displayBook() {
        // Display book data
        Limit buy = buyTree.bestPrice();
        Limit sell = sellTree.bestPrice();
        System.out.println("\nBest buy price:" + buy==null?buy.getPrice():"-");
        System.out.println("Best sell price:" + sell==null?sell.getPrice():"-");

        System.out.println("\nBuy Limits:");
        for (Limit i : buyLimitMap.values()) {
            i.display();
        }

        System.out.println("\nSell Limits:");
        for (Limit i : sellLimitMap.values()) {
            i.display();
        }

        System.out.println("Total Shares to buy:" + getBuyShares());
        System.out.println("Total Shares to sell:" + getSellShares());
        System.out.println("Total Shares in book:" + getTotalShares());
        System.out.println("Current price of stock:" + currentPrice);
    }

}

// we don't delete list from hashmaps after it is empty so that we don't have to
// create new list instance when order of same price is placed
// thus preventing GC from running speeding up our program