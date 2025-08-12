package LimitOrderBook;
public class Limit {

    Order head; // points to 1st order
    Order tail; // points to last order
    double price; // price of the list
    int size; // has size of the list

    Limit left;
    Limit right;
    // left and right nodes of BST

    public Limit(double price) {
        // initialize class variables
        this.price = price;
        this.head = null;
        this.tail = null;
        size = 0;
    }

    public void insert(Order order) {
        // insert element at the end
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
        // tells if list is empty
        return size;
    }

    public double getPrice() {
        // returns price associated with this linked list
        return price;
    }

    public void display(){

        Order temp=head;
        while(temp!=null){
            System.out.println(temp.toString());
            temp=temp.nextOrder;
        }
    }

}
