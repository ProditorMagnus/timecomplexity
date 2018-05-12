public class Example_3 {
    public static void start(long n) {
        long c = 0;
        for (int i = 0; i < n; i++) {
            for (int j = 1; j < n; j *= 2) {
                c++;
            }
        }
    }
}
