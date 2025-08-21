package LimitOrderBook;

public class Order {

    final int orderId; // unique for each order
    boolean buySell; // buy=true, sell=false
    boolean marketLimit; // market= true, limit= false
    int shares; // no of shares
    long price; // price per share
    long entryTime; // time when order was placed
    long eventTime; // time when order was executed

    Order nextOrder; // next order in the list
    Order prevOrder; // previous order in the list
    private Limit parentLimit;

    public Order(int orderId, boolean buySell, long price ,int shares, long entryTime, boolean marketLimit) {
        this.orderId = orderId;
        this.buySell = buySell;
        this.marketLimit=marketLimit;
        this.shares = shares;
        this.price = price;
        this.entryTime = entryTime;
        this.nextOrder = null;
        this.prevOrder = null;
    }

    public long getPrice(){
        // returns price per share
        return price;
    }

    public void setLimit(Limit limit) {
        this.parentLimit = limit;
    }

    public Limit getLimit() {
        return parentLimit;
    }

    public String toString() {
        String order = String.format("Order id:%d,Buy or Sell:%B, Market order or Limit order:%B ,Shares:%d, Price per share:%d, Entry time:%d",
                orderId, buySell, marketLimit, shares, price,entryTime);
        return order;
    }

}
