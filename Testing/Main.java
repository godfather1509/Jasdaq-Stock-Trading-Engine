import java.util.*;
import LimitOrderBook.LOB;

public class Main {
    public static void main(String[] args) {

        LOB lob = new LOB();

        for (int i = 100; i < 110; i++) {
            // sell limit order
            lob.addOrder(i, false, false, 1000 + i, i * 100);
        }
        lob.displayBook();
        for (int i = 110; i < 120; i++) {
            lob.addOrder(i, true, false, 1000 + i, i * 100);
            // buy limit order
        }
        lob.displayBook();
        // System.out.println("\nCanceled order:");
        // for (int i = 105; i < 115; i++) {
        //     System.out.println(lob.cancelOrder(i));
        //     // cancel order
        // }

        // System.out.println("\nMarket Orders:");

        // for (int i = 125; i < 130; i++) {
        //     // sell market order
        //     lob.addOrder(i, false, true, 1000 + i, i * 10);
        // }

        // for (int i = 120; i < 125; i++) {
        //     // buy market order
        //     lob.addOrder(i, true, true, 1000 + i, i * 10);
        // }
        // lob.displayBook();

    }

}
