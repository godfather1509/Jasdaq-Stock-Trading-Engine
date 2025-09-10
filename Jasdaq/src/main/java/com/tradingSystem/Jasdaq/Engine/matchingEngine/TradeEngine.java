package com.tradingSystem.Jasdaq.Engine.matchingEngine;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;

import com.tradingSystem.Jasdaq.Engine.Order;
import com.tradingSystem.Jasdaq.Engine.matchingEngine.MatchingEngine.TradeResults;
import com.tradingSystem.Jasdaq.generator.IdGenerator;

public class TradeEngine{

    BlockingQueue<Request> SingleThreadQueue = new LinkedBlockingQueue<>();
    // this is multithreded queue can prevent data corruption on multithreded read and write
    enum Type{addRequest, cancelRequest, displayRequest, shutDown};
    private volatile boolean running;
    private final Thread worker; // decalre a thread
    String symbol;
    MatchingEngine lob;


    public TradeEngine(String sym){
        this.running=true;
        this.symbol=sym;
        lob=new MatchingEngine();
        this.worker=new Thread(this::runnerz, "Matching Engine Started"); // initialize the thread
        // this::runnerz will execute runnerz function as soon as this thread starts
        this.worker.start();

    }

    private class Request {
        String orderId; // unique for each order
        boolean buySell; // buy=true, sell=false
        boolean marketLimit; // market= true, limit= false
        int shares; // no of shares
        long price; // price per share
        Type reqType;
        String cancelOrderId;
        final CompletableFuture<Object> future;

        public Request(String orderId, boolean buySell, long price, int shares, boolean marketLimit, Type reqType) {
            // constructor for add order
            this.orderId = orderId;
            this.buySell = buySell;
            this.marketLimit = marketLimit;
            this.shares = shares;
            this.price = price;
            this.reqType=reqType; // addOrder request, cancelOrder request and displayBook request
            this.future=new CompletableFuture<>();
        }

        public Request(String orderId, Type reqType){
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

    public CompletableFuture<Object> submitAddRequest(boolean buySell, long price, int shares, boolean marketLimit){

        // this function will add order
        String orderId=IdGenerator.nextID(symbol,'o'); // get order id
        Request req=new Request(orderId, buySell, price, shares, marketLimit, Type.addRequest);
        SingleThreadQueue.add(req); // add request in queue
        return req.future;
    }

    public CompletableFuture<Object> submitCancelrequest(String orderId){
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
            TradeResults trades=null;
            while (running) {
                Request req=SingleThreadQueue.take();
                // take() method retrieves and removes the head of this queue, waiting if necessary until an element becomes available
                try {
                    switch (req.reqType) {
                        case addRequest:
                            trades=lob.addOrder(req.orderId, req.buySell, req.marketLimit, req.price,  req.shares);
                            req.future.complete(trades);
                            break;

                        case cancelRequest:
                            Order order=lob.cancelOrder(req.cancelOrderId);
                            req.future.complete(order);
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
