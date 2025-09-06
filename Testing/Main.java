import MatchingEngine.TradeEngine;

public class Main {

    public static double runbenchMarking(){

        long start=System.nanoTime();

        TradeEngine mt = new TradeEngine("APL");

        System.out.println("Limit Orders:");
        for (int i = 0; i < 100000; i++) {
            // sell limit order
            mt.submitAddRequest(i, false, i+2, (i+20)/10, false);
            // submitAddRequest(int orderId, boolean buySell, long price, int shares, boolean marketLimit)
        }
        // mt.submitDisplayrequest();

        for (int i = 100000; i < 200000; i++) {
            // buy limit order
            mt.submitAddRequest(i, true, i/100, i/10, false);
            // submitAddRequest(int orderId, boolean buySell, long price, int shares, boolean marketLimit)
        }
        mt.submitDisplayrequest();

        mt.submitAddRequest(-1, true, 100, 10, false);
        mt.submitCancelrequest(-1);
            // cancel order

        // System.out.println("Market Orders:");
        // for (int i = 300000; i < 400000; i++) {
        //     // sell market order
        //     mt.submitAddRequest(i, false, i/100, i/10, true);
        // }
        // for (int i = 400000; i < 500000; i++) {
        //     // buy market order
        //     mt.submitAddRequest(i, true, i/100, i/10, true);
        // }
        // mt.submitDisplayrequest();
        mt.submitShutDown();

        long end=System.nanoTime();
        return (end-start)/100_00_00.0;
    }


    public static void main(String[] args) {
        double total=0;
        for(int i=0;i<10;i++){
            runbenchMarking();
        }
        for(int i=0;i<5;i++){
            total+=runbenchMarking();
        }
        System.out.println("Average run time:"+total/10+"ms");
        System.out.println("Total Execution Time:"+runbenchMarking()+"ms");
        // runbenchMarking();
    }

}
