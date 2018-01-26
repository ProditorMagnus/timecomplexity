import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;
import java.util.concurrent.*;

public class Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);
    private static final long TIME_LIMIT = 1000;
    private final Class<?> target;
    private final double PRECISION = 0.9;

    public Evaluator(Class<?> target) {
        this.target = target;
    }

    public void estimate() {
        try {
            evaluate();
        } catch (Exception e) {
            logger.error("Evaluation failed", e);
        }
    }

    private void evaluate() throws InvocationTargetException, IllegalAccessException {
        for (Method method : target.getMethods()) {
            if (Modifier.isStatic(method.getModifiers()) &&
                    Arrays.equals(method.getParameterTypes(), new Class<?>[]{long.class})) {
                Map<Long, List<Long>> results = evaluateMethod(method);
                logger.info("results {}", results);
            }
        }
    }

    private Map<Long, List<Long>> evaluateMethod(Method method) throws IllegalAccessException, InvocationTargetException {
        int repeats = 4;
        long limit = findMaxArgument(method, repeats);
        logger.info("Limit {}", limit);

        Map<Long, List<Long>> results = new HashMap<>();
//        for (long i = 0; i < Long.MAX_VALUE; i++) {
//            results.putIfAbsent(i, new ArrayList<>());
//            List<Long> times = evaluateMethod(method, repeats, i);
//            results.get(i).addAll(times);
//            Long min = Collections.min(times);
//            if (min > 5000) break;
//            if (min < 1) i *= 100;
//            i *= 2;
//        }
        return results;
    }

    private long findMaxArgument(final Method method, final int times) throws InvocationTargetException, IllegalAccessException {
        long low = 0;
        long high = Long.MAX_VALUE;
        long current = 0;
        boolean found_windows = false;
        ExecutorService executor = Executors.newFixedThreadPool(1);
        while (true) {
            logger.info("Current {}", current);
            final long current_ = current;
            Future<List<Long>> submit = executor.submit(() -> evaluateMethod(method, times, current_));
            double average;
            try {
                logger.info("getting at {}", current_);
                List<Long> longs = submit.get(5 * TIME_LIMIT, TimeUnit.MILLISECONDS);
                logger.info("got longs {}", longs);
                average = longs.stream().mapToLong(Long::new).average().getAsDouble();
            } catch (Exception e) {
                average = Double.MAX_VALUE;
                logger.info("timeout {}", current_);
                logger.error("e", e);
                submit.cancel(true);
            }

            if (average < TIME_LIMIT * PRECISION) {
                low = current;
//                current = current + 1 + 2 * min;
                if (!found_windows) {
                    current *= 2;
                    current++;
                } else {
                    current = (low + high) / 2;
                }
            } else if (average > TIME_LIMIT / PRECISION) {
                high = current;
                found_windows = true;
                current = (low + high) / 2;
            } else {
                break;
            }
            logger.info("current avg {}", average);
        }
        executor.shutdownNow();
        return current;
    }

    private List<Long> evaluateMethod(Method method, int times, long n) throws InvocationTargetException, IllegalAccessException {
        List<Long> results = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            results.add(evaluateMethodOnce(method, n));
        }
        return results;
    }

    private long evaluateMethodOnce(Method method, long n) throws InvocationTargetException, IllegalAccessException {
        long time = System.currentTimeMillis();
        method.invoke(null, n);
        long timeSpent = System.currentTimeMillis() - time;
        logger.info("Time spent for {}: {}", n, timeSpent);
        return timeSpent;
    }

}
