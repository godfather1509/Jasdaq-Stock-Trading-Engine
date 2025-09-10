package com.tradingSystem.Jasdaq.Engine.matchingEngine.LOB;

import java.util.ArrayList;
import com.tradingSystem.Jasdaq.Engine.Order;

public class Limit {
    // Limit is a linked list
    // we could have used min heap but then canceling and removing some order from
    // inbetween would have been a time cosuming

    private Order head; // points to 1st order
    private Order tail; // points to last order
    private long price; // price of the list
    private int size; // has size of the list
    private boolean buySell;

    Limit right; // pointer to right limit
    Limit left; // pointer to left limit
    Limit parent; // parent of the current node
    String color; // holds color of node
    int height; // height of tree from root to this node
    public int limitVolume=0; // keeps track of no. of shares in limit

    public Limit(long price, boolean buySell) {
        // initialize class variables
        this.price = price;
        this.buySell = buySell;
        this.head = null;
        this.tail = null;
        this.color = "R";
        // initialize color of the node as Red
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
        limitVolume+=order.shares;
    }

    public Order delete(String orderId) {
        // delete order from anywhere in the list
        // ideally order will be deleted only when executed but sometimes user might
        // also cancel the order
        if (head == null) {
            System.out.println("No Order");
            return null;
        }

        Order temp = head;
        if (head.orderId.equals(orderId)) {
            // deleting head from list
            return pop();
        }

        while (temp != null && !temp.orderId.equals(orderId)) {
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
            limitVolume-=temp.shares;
            return temp;
        }

        temp.prevOrder.nextOrder = temp.nextOrder;
        temp.nextOrder.prevOrder = temp.prevOrder;
        temp.prevOrder = null;
        temp.nextOrder = null;
        size--;
        limitVolume-=temp.shares;
        return temp;
    }

    public Order pop() {
        // remove 1st order from list
        if (head == null) {
            System.out.println("No Order");
            return null;
        }
        Order temp = head;
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
        limitVolume-=temp.shares;
        return temp;
    }

    public void display() {
        // will display entire linked list
        Order temp = head;
        while (temp != null) {
            System.out.println(temp.toString());
            temp = temp.nextOrder;
        }
    }

    public Order getOrder(String orderId) {
        // will return individul order
        if (head.orderId.equals(orderId)) {
            return getHead();
        }
        if (tail.orderId.equals(orderId)) {
            return getTail();
        }
        Order temp = head;
        while (temp.orderId != orderId && temp != null) {
            temp = temp.nextOrder;
        }
        return temp;
    }

    public void sort() {
        // sort the orders in list in ascending order of time placed
        // sorts linked list in O(nlogn) time
        // space complexity O(N)
        ArrayList<Order> newList = new ArrayList<>();
        Order temp = head;
        while (temp != null) {
            newList.add(temp);
        }
        newList.sort((a, b) -> Long.compare(a.entryTime, b.entryTime));
        head = null;
        tail = null;

        for (Order order : newList) {
            insert(order);
        }
    }

    public int getSize() {
        // returns size of the list
        return size;
    }

    public int getVolume(){
        return limitVolume;
    }

    public long getPrice() {
        // returns price associated with this linked list
        return price;
    }

    public Order getHead() {
        // return 1st order
        return head;
    }

    public Order getTail() {
        // return last order
        return tail;
    }

    
    public boolean isEmpty() {
        // will tell if list is empty or not
        return head == null;
    }

    public boolean getLimit() {
        return buySell;
    }
}
