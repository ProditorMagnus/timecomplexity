package examples;

public class Example_1 {
    public static void start(long n) {
        for (int i = 0; i < n; i++) {
            start(n - 1);
        }
    }
}
