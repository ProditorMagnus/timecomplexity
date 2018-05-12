public class Example_4 {
    public static void start(long n) {
        long c = 0;
        for (long j = 1; j < n; j *= 2) {
            c++;
            // This is too fast to have any measurable time taken without sleeping
            try {
                Thread.sleep(2);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }
}
