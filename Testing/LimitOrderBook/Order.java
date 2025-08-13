package LimitOrderBook;

public class Order {

    int orderId; // unique for each order
    int userId; // Id of each user
    boolean buySell; // buy=true, sell=false
    int shares; // no of shares
    double price; // price of order
    long entryTime; // time when order was placed
    int eventTime; // time when order was executed

    Order nextOrder; // next order in the list
    Order prevOrder; // previous order in the list
    private Limit parentLimit;

    public Order(int orderId, boolean buySell, int userId, double price, int shares, long entryTime) {
        this.orderId = orderId;
        this.userId = userId;
        this.buySell = buySell;
        this.shares = shares;
        this.price = price;
        this.entryTime = entryTime;
        this.nextOrder = null;
        this.prevOrder = null;
    }

    public void setLimit(Limit limit) {
        this.parentLimit = limit;
    }

    public Limit getLimit() {
        return parentLimit;
    }

    public String toString() {

        // String order="Order id:"+orderId+"\n"+"user id:"+userId+"\n"+"Buy or sell";

        String order = String.format("Order id:%d, User id:%d, Buy or Sell:%B, Shares:%d, Price:%f, Entry time:%d",
                orderId, userId, buySell, shares, price, entryTime);

        return order;
    }

}
