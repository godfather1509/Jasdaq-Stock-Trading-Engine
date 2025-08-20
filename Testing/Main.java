import LimitOrderBook.LOB;

public class Main {
    public static void main(String[] args) {

        LOB lob = new LOB();

        for (int i = 100; i < 110; i++) {
            lob.placeOrder(i, true, i * 200 + 10, (i + 1) * 100);
            // buy stock
        }

        for (int i = 110; i < 120; i++) {
            lob.placeOrder(i, false, i * 200 + 10, (i + 1) * 100);
            // sell stock
        }

        lob.execute();

        System.out.println("\nCanceled order:");
        for (int i = 105; i < 115; i++) {
            System.out.println(lob.cancelOrder(i));
            // buy stock
        }

        lob.execute();

    }

}
