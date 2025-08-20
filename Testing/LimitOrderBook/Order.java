package LimitOrderBook;

public class Order {

    final int orderId; // unique for each order
    boolean buySell; // buy=true, sell=false
    int shares; // no of shares
    double price; // price of order
    long entryTime; // time when order was placed
    int eventTime; // time when order was executed
    double unitPrice;

    Order nextOrder; // next order in the list
    Order prevOrder; // previous order in the list
    private Limit parentLimit;

    public Order(int orderId, boolean buySell, double price,double unitPrice ,int shares, long entryTime) {
        this.orderId = orderId;
        this.buySell = buySell;
        this.shares = shares;
        this.price = price;
        this.entryTime = entryTime;
        this.nextOrder = null;
        this.prevOrder = null;
        this.unitPrice=unitPrice;
    }

    public double getPrice(){
        return price;
    }

    public double getUnitPrice(){
        return unitPrice;
    }

    public void setLimit(Limit limit) {
        this.parentLimit = limit;
    }

    public Limit getLimit() {
        return parentLimit;
    }

    public String toString() {
        String order = String.format("Order id:%d,Buy or Sell:%B, Shares:%d, Price:%f, Entry time:%d",
                orderId, buySell, shares, price, entryTime);
        return order;
    }

}
