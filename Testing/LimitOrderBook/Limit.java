package LimitOrderBook;
import java.util.*;

public class Limit {
    // Limit is a linked list 
    // we could have used min heap but then canceling and removing some order from inbetween would have been a time cosuming

    private Order head; // points to 1st order
    private Order tail; // points to last order
    private double price; // price of the list
    private int size; // has size of the list

    Limit right; // pointer to right limit 
    Limit left; // pointer to left limit
    String color; // holds color of node
    int height; // height of tree from root to this node

    public Limit(double price) {
        // initialize class variables
        this.price = price;
        this.head = null;
        this.tail = null;
        this.color="R";
        size = 0;
    }

    public void insert(Order order) {
        // insert element at the end
        // order placed first should be executed 1st
        if (head == null) {
            head = order;
            tail = head;
            head.nextOrder = null;
            head.prevOrder = null;
            size++;
            return;
        }
        tail.nextOrder = order;
        order.prevOrder = tail;
        tail = order;
        size++;
    }

    public Order delete(int orderId) {
        // delete order from anywhere in the list
        // ideally order will be deleted only when executed but sometimes user might also cancel the order
        if (head == null) {
            System.out.println("No Order");
            return null;
        }

        Order temp = head;
        if (head.orderId == orderId) {
            // deleting head from list
            head = temp.nextOrder;
            if (head != null) {
                head.prevOrder = null;
            }
            size--;
            if (size == 0) {
                tail = null;
            }
            temp.prevOrder = null;
            temp.nextOrder = null;
            return temp;
        }

        while (temp != null && temp.orderId != orderId) {
            // if order is not present then temp will iterate to null
            temp = temp.nextOrder;
        }

        if (temp == null) {
            // after traversing whole list requierd element is not found
            System.out.println("Order not found");
            return null;
        }

        if (temp == tail) {
            tail = temp.prevOrder;
            tail.nextOrder = null;
            temp.prevOrder = null;
            size--;
            return temp;
        }

        temp.prevOrder.nextOrder = temp.nextOrder;
        temp.nextOrder.prevOrder = temp.prevOrder;
        temp.prevOrder = null;
        temp.nextOrder = null;
        size--;
        return temp;
    }

    public int getSize() {
        // returns size of the list
        return size;
    }

    public double getPrice() {
        // returns price associated with this linked list
        return price;
    }

    public void display() {
        // will display entire linked list
        Order temp = head;
        while (temp != null) {
            System.out.println(temp.toString());
            temp = temp.nextOrder;
        }
    }

    public Order getHead(){
        // return 1st order
        return head;
    }

    public Order getTail(){
        // return last order
        return tail;
    }

    public Order getOrder(int orderId) {
        // will return individul order
        if(head.orderId==orderId){
            return getHead();
        }
        if(tail.orderId==orderId){
            return getTail();
        }
        Order temp = head;
        while (temp.orderId != orderId && temp != null) {
            temp = temp.nextOrder;
        }
        return temp;
    }

    public void sort(){
        // sort the orders in list in ascending order of time placed
        // sorts linked list in O(nlogn) time
        // space complexity O(N)
        ArrayList<Order> newList=new ArrayList<>();
        Order temp=head;
        while(temp!=null){
            newList.add(temp);
        }
        newList.sort((a,b)->Double.compare(a.entryTime,b.entryTime));
        head=null;
        tail=null;

        for(Order order:newList){
            insert(order);
        }
    }

}
