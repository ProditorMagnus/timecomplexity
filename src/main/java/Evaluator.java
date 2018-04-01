import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.stream.Collectors;

public class Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);
    private static final long TIME_LIMIT = 1000;
    private final Class<?> target;
    private final double PRECISION = 0.6;

    private final ResultHolder results = new ResultHolder();

    public Evaluator(Class<?> target) {
        this.target = target;
    }

    public ResultHolder estimate() {
        try {
            evaluate();
        } catch (Exception e) {
            logger.error("Evaluation failed", e);
        }
        return results;
    }

    public static Class<?>[] parseParameters(String s) throws ClassNotFoundException {
        String[] split = s.split(",");
        Class<?>[] parameters = new Class[split.length];
        for (int i = 0; i < split.length; i++) {
            switch (split[i]) {
                case "boolean":
                    parameters[i] = boolean.class;
                    break;
                case "byte":
                    parameters[i] = byte.class;
                    break;
                case "short":
                    parameters[i] = short.class;
                    break;
                case "int":
                    parameters[i] = int.class;
                    break;
                case "long":
                    parameters[i] = long.class;
                    break;
                case "float":
                    parameters[i] = float.class;
                    break;
                case "double":
                    parameters[i] = double.class;
                    break;
                case "char":
                    parameters[i] = char.class;
                    break;
                default:
                    parameters[i] = Class.forName(split[i]);
            }
        }
        return parameters;
    }

    private void evaluate() throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException, InstantiationException {
        for (Method method : target.getDeclaredMethods()) {
            logger.info("checking method {} with parameters {} needed parameters {}", method.getName(), method.getParameterTypes(), parseParameters(Config.get("config.properties").getProperty("function.parameter")));
            if (Modifier.isPublic(method.getModifiers()) && Modifier.isStatic(method.getModifiers()) && Arrays.equals(method.getParameterTypes(),
                    parseParameters(Config.get("config.properties").getProperty("function.parameter")))) {
                evaluateMethod(method);
                logger.info("results {}", results);
                return;
            }
        }
        logger.error("No method of required signature was found {}", Config.get("config.properties").getProperty("function.parameter"));
    }

    private void evaluateMethod(Method method) throws IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, InstantiationException {
        if (Config.value("mode").equals("auto")) {
            int repeats = 2;
            long limit = findMaxArgument(method, repeats);
            fillPoints(method, limit, Integer.parseInt(Config.value("result.point.count")));
            logger.info("Limit {}", limit);
        } else if (Config.value("mode").equals("manual")) {
            int testCase = 1;
            while (invokeManual(method, Integer.toString(testCase))) testCase++;

        } else {
            logger.error("config mode not auto or manual");
        }
    }

    private boolean invokeManual(Method method, String testCase) throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException, InstantiationException {
        String testLocation = Config.value("loc.tests");
        if (!Files.exists(Paths.get(testLocation, String.format("meta%s.txt", testCase)))) {
            return false;
        }
        long input_size = Long.parseLong(Config.get(Paths.get(testLocation, String.format("meta%s.txt", testCase)).toString()).getProperty("input_size"));
        InputStream inputStream = Files.newInputStream(Paths.get(testLocation, String.format("input%s.txt", testCase)));
        InputStream stdin = System.in;
        System.setIn(inputStream);
        ByteArrayOutputStream bout = new ByteArrayOutputStream();
        PrintStream outputStream = new PrintStream(bout, true, "UTF-8");
        PrintStream stdout = System.out;
        System.setOut(outputStream);
        timeMethod(method, input_size);
        System.setIn(stdin);
        System.setOut(stdout);
        List<String> functionOutput = new BufferedReader(new StringReader(bout.toString("UTF-8"))).lines().collect(Collectors.toList());
        logger.info("Program wrote {}", functionOutput);
        List<String> expectedOutput = Files.readAllLines(Paths.get(testLocation, String.format("output%s.txt", testCase)));

        if (functionOutput.equals(expectedOutput)) {
            logger.info("function wrote correct value {}", functionOutput);
        } else {
            logger.error("function wrote incorrect value {} expected {}", functionOutput, expectedOutput);
        }
        return true;
    }

    private void fillPoints(Method method, long limit, int points) {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        for (int i = 0; i < limit; i += limit / points) {
            logger.info("fillPoints {}", i);
            final long current_ = i;
            Future<List<Long>> submit = executor.submit(() -> evaluateMethod(method, 2, current_));
            try {
                submit.get(5 * TIME_LIMIT, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                submit.cancel(true);
            }
        }
        executor.shutdownNow();
    }

    private long findMaxArgument(final Method method, final int times) throws InvocationTargetException, IllegalAccessException {
        long low = 0;
        long high = Long.MAX_VALUE;
        long current = 0;
        boolean found_windows = false;
        ExecutorService executor = Executors.newFixedThreadPool(1);
        Set<Long> attemptedValues = new HashSet<>();
        while (true) {
            final long current_ = current;
            Future<List<Long>> submit = executor.submit(() -> evaluateMethod(method, times, current_));
            double average;
            if (!attemptedValues.add(current)) {
                logger.error("Same current attempted multiple times {} {} {}", low, current, high);
                submit.cancel(true);
                break;
            }
            try {
                logger.info("getting at {}", current_);
                List<Long> longs = submit.get(5 * TIME_LIMIT, TimeUnit.MILLISECONDS);
                logger.info("got longs {}", longs);
                average = longs.stream().mapToLong(Long::new).average().orElse(Double.MAX_VALUE);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                average = Double.MAX_VALUE;
                logger.info("timeout {}", current_);
                results.addTime(current, null);
                logger.error("e", e);
                submit.cancel(true);
            }
            logger.info("current {} -> avg {}", current, average);

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
                if (attemptedValues.contains(current)) {
                    current--;
                }
            } else {
                break;
            }
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
        // TODO try use timeMethod
        long time = System.currentTimeMillis();
        method.invoke(null, n);
        long timeSpent = System.currentTimeMillis() - time;
        logger.info("Time spent for {}: {}", n, timeSpent);
        results.addTime(n, timeSpent);
        return timeSpent;
    }

    private Object timeMethod(Method method, long size) throws InvocationTargetException, IllegalAccessException, InstantiationException {
        List<Object> args = new ArrayList<>();
        for (Class<?> ignored : method.getParameterTypes()) {
            args.add(null);
        }
        long time = System.currentTimeMillis();
        Object output = method.invoke(null, args.toArray());
        long timeSpent = System.currentTimeMillis() - time;
        results.addTime(size, timeSpent);
        return output;
    }
}
