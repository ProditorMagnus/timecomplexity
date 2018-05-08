public class Example_9 {
    public static void start(long n) {
        long c = 0;
        for (long j = 1; j < Math.pow(2, n); j++) {
            c++;
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
