import LimitOrderBook.LOB;

public class Main {
    public static void main(String[] args) {

        LOB lob = new LOB();
        for (int i = 100; i < 110; i++) {
            if(i%2==0)
            lob.addOrder(i, true, true ,i * 200 + 10, (i + 1) * 100);
            else
            lob.addOrder(i, true, false ,i * 200 + 10, (i + 1) * 100);
            // buy stock
        }

        for (int i = 110; i < 120; i++) {
            if(i%2!=0)
            lob.addOrder(i, false, true ,i * 200 + 10, (i + 1) * 100);
            else
            lob.addOrder(i, false, false ,i * 200 + 10, (i + 1) * 100);
            // sell stock
        }
        lob.displayBook();
        System.out.println("\nCanceled order:");
        for (int i = 105; i < 115; i++) {
            System.out.println(lob.cancelOrder(i));
            // buy stock
        }
        lob.displayBook();

    }

}
