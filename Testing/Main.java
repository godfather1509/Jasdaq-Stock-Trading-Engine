import LimitOrderBook.LOB;

public class Main {
    public static void main(String[] args) {

        LOB lob = new LOB();

        for (int i = 0; i < 100; i++) {
            lob.placeOrder(i, true, i * 2, (i + 1) * 10);
            // buy stock
        }

        for (int i = 100; i < 200; i++) {
            lob.placeOrder(i, false, i * 2, (i + 1) * 10);
            // sell stock
        }

        lob.execute();

    }

}
