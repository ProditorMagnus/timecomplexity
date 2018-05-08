public class Example_8 {
    public static void start(long n) {
        long c = 0;
        for (long j = 1; j < n; j *= 2) {
            c++;
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
