import LimitOrderBook.LOB;

public class Main {
    public static void main(String[] args) {

        LOB lob = new LOB();

        for (int i = 0; i < 10; i++) {
            if (i % 2 == 0) {
                lob.placeOrder(i, true, i * 2, (i + 1) * 10);
            } else {
                lob.placeOrder(i, false, i * 2, (i + 1) * 10);
            }
        }

    }

}
