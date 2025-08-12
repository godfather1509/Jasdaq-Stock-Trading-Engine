package LimitOrderBook;
import java.util.*;

public class LOB {

    HashMap<Integer, Order> orderMap = new HashMap<>();
    HashMap<Double, Limit> limitMap = new HashMap<>();
    Date date;
    Order order;

    public LOB() {
        date = new Date();
    }

    public Order placeOrder(int orderId, boolean buySell, int userId, double price, int shares) {

        if (!orderMap.containsKey(orderId)) {
            long entryTime = date.getTime();
            Limit limit;
            order = new Order(orderId, buySell, userId, price, shares, entryTime);
            orderMap.put(orderId, order);
            if (!limitMap.containsKey(price)) {
                limit = new Limit(price);
                limit.insert(order);
                limitMap.put(price, limit);
            } else {
                limit = limitMap.get(price);
                limit.insert(order);
            }
            System.out.println("Order Placed");
            // limit.display();
            return order;
        } else {
            System.out.println("Order with orderId " + orderId + " already exists");
            return null;
        }
    }
}
