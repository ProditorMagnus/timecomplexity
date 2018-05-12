import java.util.concurrent.ThreadLocalRandom;

public class DataGen {
    public static Object getInput(long n) {
        return n;
//        return generateLongArray(n, 0, Long.MAX_VALUE);
    }

    private static long[] generateLongArray(long n, long min, long max) {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        long[] array = new long[Math.toIntExact(n)];
        for (int i = 0; i < n; i++) {
            array[i] = random.nextLong(min, max);
        }
        return array;
    }
}
