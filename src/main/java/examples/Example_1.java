package examples;

public class Example_1 {
    public static void start(long n) {
        if (n < 1) return;
        for (int i = 0; i < n; i++) {
            start(n - 1);
        }
    }
}
