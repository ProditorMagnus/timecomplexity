import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

public class Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);
    private final Class<?> target;

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
                    Arrays.equals(method.getParameterTypes(), new Class<?>[]{int.class})) {
                Map<Integer, List<Long>> results = evaluateMethod(method);
                logger.info("results {}", results);
            }
        }
    }

    private Map<Integer, List<Long>> evaluateMethod(Method method) throws IllegalAccessException, InvocationTargetException {
        int repeats = 4;
        Map<Integer, List<Long>> results = new HashMap<>();
        for (int i = 0; i < 20; i++) {
            results.putIfAbsent(i, new ArrayList<>());
            List<Long> times = evaluateMethod(method, repeats, i);
            results.get(i).addAll(times);
            if (Collections.min(times) > 5000) break;
        }
        return results;
    }

    private List<Long> evaluateMethod(Method method, int times, int n) throws InvocationTargetException, IllegalAccessException {
        List<Long> results = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            results.add(evaluateMethodOnce(method, n));
        }
        return results;
    }

    private long evaluateMethodOnce(Method method, int n) throws InvocationTargetException, IllegalAccessException {
        long time = System.currentTimeMillis();
        method.invoke(null, n);
        long timeSpent = System.currentTimeMillis() - time;
        logger.info("Time spent: {}", timeSpent);
        return timeSpent;
    }

}
