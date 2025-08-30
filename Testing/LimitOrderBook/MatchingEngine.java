package LimitOrderBook;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

/*
Our main Limit order book is not compatible for parrallel read and write so we have implemented Single Threaded Matching engine 
it accepts multithreded inputs and stores them in BlockingQueue(Multithreding compatible version of Queue) 
We remove and place each of these request sequentially in order book
*/

public class MatchingEngine {

    BlockingQueue<Request> SingleThreadQueue = new LinkedBlockingQueue<>();
    // this is multithreded queue can prevent data corruption on multithreded read and write
    LOB lob = new LOB();
    enum Type{addRequest, cancelRequest, displayRequest, shutDown};
    private volatile boolean running;
    private final Thread worker; // decalre a thread

    public MatchingEngine(){
        this.running=true;
        this.worker=new Thread(this::runnerz, "Matching Engine Started"); // initialize the thread
        // this::runnerz will execute runnerz function as soon as this thread starts
        this.worker.start();
    }

    @SuppressWarnings("unused")
    private class Request {
        int orderId; // unique for each order
        boolean buySell; // buy=true, sell=false
        boolean marketLimit; // market= true, limit= false
        boolean status; // executed/canceled= true, pending=false
        int shares; // no of shares
        long price; // price per share
        Type reqType;
        int cancelOrderId;
        final CompletableFuture<Object> future;

        public Request(int orderId, boolean buySell, long price, int shares, boolean marketLimit, Type reqType) {
            // constructor for add order
            this.orderId = orderId;
            this.buySell = buySell;
            this.marketLimit = marketLimit;
            this.status = false;
            this.shares = shares;
            this.price = price;
            this.reqType=reqType; // addOrder request, cancelOrder request and displayBook request
            this.future=new CompletableFuture<>();
        }

        public Request(int orderId, Type reqType){
            // constructor for cancel order
            this.cancelOrderId=orderId;
            this.reqType=reqType;
            future=new CompletableFuture<>();
        }

        public Request(Type reqType){
            // constructor display order book
            this.future=new CompletableFuture<>();
            this.reqType=reqType;
        }
    }

    public CompletableFuture<Object> submitAddRequest(int orderId, boolean buySell, long price, int shares, boolean marketLimit){
        // this function will add order
        Request req=new Request(orderId, buySell, price, shares, marketLimit, Type.addRequest);
        SingleThreadQueue.add(req);
        return req.future;
    }

    public CompletableFuture<Object> submitCancelrequest(int orderId){
        // this function will cancel order
        Request req=new Request(orderId, Type.cancelRequest);
        SingleThreadQueue.add(req);
        return req.future;
    }

    public CompletableFuture<Object> submitDisplayrequest(){
        Request req=new Request(Type.displayRequest);
        SingleThreadQueue.add(req);
        return req.future;
    }
    // these are functions are called from outside the class to add requests to Blocking queue

    public CompletableFuture<Object> submitShutDown(){
        // adds shut down request to the queue
        Request req=new Request(Type.shutDown);
        SingleThreadQueue.add(req);
        return req.future;
    }

    public void runnerz(){
        try {
            while (running) {
                Request req=SingleThreadQueue.take();
                // take() method retrieves and removes the head of this queue, waiting if necessary until an element becomes available
                try {
                    switch (req.reqType) {
                        case addRequest:
                            lob.addOrder(req.orderId, req.buySell, req.marketLimit, req.price,  req.shares);
                            req.future.complete("Order Placed");
                            break;

                        case cancelRequest:
                            lob.cancelOrder(req.cancelOrderId);
                            req.future.complete("Order Canceled");
                            break;
                        case displayRequest:
                            lob.displayBook();
                            req.future.complete("Order Book");
                            break;
                        case shutDown:
                            running=false;
                            req.future.complete("Book Closed");
                    }
                } catch (Exception e) {
                    req.future.completeExceptionally(e);
                }   
            }
        } catch (InterruptedException exp) {
            Thread.currentThread().interrupt();
        }
    }
}
