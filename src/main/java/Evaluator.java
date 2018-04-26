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
import java.util.function.Function;
import java.util.stream.Collectors;

public class Evaluator {
    private static final Logger logger = LoggerFactory.getLogger(Evaluator.class);
    private final long TIME_LIMIT;
    private final Class<?> target;
    private final Function<Long, Object> inputProvider;


    private final ResultHolder results = new ResultHolder();

    public Evaluator(Class<?> target) {
        this.target = target;
        TIME_LIMIT = Config.valueAsLong("function.goal.time", 1000L);

        if (Objects.equals(Config.value("function.parameter"), "long")) {
            inputProvider = aLong -> aLong;
        } else {
            try {
                final Method inputClass = new JavaExecutor(Paths.get(Config.value("loc.source.java"), "DataGen.java")).loadClass().getMethod("getInput", long.class);
                inputProvider = aLong -> {
                    try {
                        return inputClass.invoke(null, aLong);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("Generating input failed", e);
                    }
                };
            } catch (NoSuchMethodException e) {
                throw new RuntimeException("No method to generate input from long");
            }
        }
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

    private Object getInput(long n) {
        return inputProvider.apply(n);
    }

    private void evaluate() throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException, InstantiationException {
        for (Method method : target.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            if (Config.valueAsString("function.parameter", null) == null) {
                logger.info("checking method {}, needed {}", method.getName(), Config.value("function.name"));
                if (Objects.equals(method.getName(), Config.value("function.name"))) {
                    evaluateMethod(method);
                    logger.info("results {}", results);
                    return;
                }
            } else {
                logger.info("checking method {} with parameters {} needed parameters {}", method.getName(), method.getParameterTypes(), parseParameters(Config.value("function.parameter")));
                if (Arrays.equals(method.getParameterTypes(),
                        parseParameters(Config.value("function.parameter")))) {
                    evaluateMethod(method);
                    logger.info("results {}", results);
                    return;
                }
            }
        }
        logger.error("No method of required signature was found {}", Config.value("function.parameter"));
    }

    private void evaluateMethod(Method method) throws IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, InstantiationException {
        switch (Config.value("mode")) {
            case "auto":
                Long maxN = Config.valueAsLong("function.n.max", (long) Integer.MAX_VALUE);
                Long minN = Config.valueAsLong("function.n.min", 0L);
                Long pointCount = Config.valueAsLong("result.point.count", 100L);
                int repeats = 2;
                long limit;
                if (maxN - minN < pointCount || Config.valueAsLong("function.n.point_only", 0L) != 0) {
                    limit = maxN;
                } else {
                    limit = findMaxArgument(method, repeats, minN, maxN);
                }

                logger.info("Limit {}", limit);
                fillPoints(method, limit, Math.toIntExact(pointCount));
                break;
            case "manual":
                int testCase = 1;
                while (invokeManual(method, Integer.toString(testCase))) testCase++;

                break;
            default:
                logger.error("config mode not auto or manual");
                break;
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
        long increment = limit / points;
        if (limit <= points) increment = 1;
        for (long i = 0; i < limit; i += increment) {
            logger.info("fillPoints {}", i);
            final long current_ = i;
            Future<List<Long>> submit = executor.submit(() -> evaluateMethod(method, 2, current_));
            try {
                submit.get(5 * TIME_LIMIT, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                submit.cancel(true);
                if (e instanceof TimeoutException) {
                    logger.error("fillPoints timeout at {}", i);
                    System.exit(1);
                }
            }
        }
        executor.shutdownNow();
    }

    private long findMaxArgument(final Method method, final int times, final long minN, final long maxN) throws InvocationTargetException, IllegalAccessException {

        GuessProvider guessProvider = new GuessProvider(minN, maxN);
        ExecutorService executor = Executors.newFixedThreadPool(1);
        while (true) {
            final long current = guessProvider.getCurrent();
            Future<List<Long>> submit = executor.submit(() -> evaluateMethod(method, times, current));
            double average;
            try {
                logger.info("getting at {}", current);
                List<Long> longs = submit.get(5 * TIME_LIMIT, TimeUnit.MILLISECONDS);
                logger.info("got longs {}", longs);
                average = longs.stream().mapToLong(Long::new).average().orElse(Double.MAX_VALUE);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                average = Double.MAX_VALUE;
                logger.info("timeout {}", current);
                results.addTime(current, null);
                logger.error("e", e);
                submit.cancel(true);
            }
            logger.info("current {} -> avg {}", current, average);

            if (guessProvider.findNext(average)) {
                break;
            }
        }
        executor.shutdownNow();
        return guessProvider.getCurrent();
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
        method.invoke(null, getInput(n));
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
