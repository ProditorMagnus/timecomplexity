import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class JavaExecutor extends FunctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(JavaExecutor.class);
    private String className;
    private Class<?> target;
    private Function<Long, Object> inputProvider;
    private long TIME_LIMIT;
    private static boolean printprogress = Config.valueAsLong("output.printprogress", 0L) == 1;

    public JavaExecutor(Path source) {
        super(source);
        className = source.getFileName().toString().replaceFirst("\\.java", "");
    }

    private Class<?> loadClass() {
        // https://stackoverflow.com/a/21544850/3667389
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null)
            throw new RuntimeException("Java kompilaatorit ei leitud. Programm tuleb käivitada JDK alamkaustas jre/bin/java väljakutsega.");
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {

            List<String> optionList = new ArrayList<>();
            optionList.add("-classpath");
            optionList.add(System.getProperty("java.class.path"));
//        optionList.add(System.getProperty("java.class.path") + ";dist/InlineCompiler.jar");

            Iterable<? extends JavaFileObject> compilationUnit
                    = fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(source.toFile()));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    optionList,
                    null,
                    compilationUnit);
            if (task.call()) {
                // Create a new custom class loader, pointing to the directory that contains the compiled
                // classes, this should point to the top of the package structure!
                URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(Config.valueAsString("source.java", ".")).toURI().toURL()});
                // Load the class from the classloader by name....
                return classLoader.loadClass(className);
            } else {
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    logger.warn("Kompileerimise probleem: {}", diagnostic.getMessage(Locale.US));
                    logger.error("Error real {} failis {}",
                            diagnostic.getLineNumber(),
                            diagnostic.getSource().toUri());
                }
            }
        } catch (IOException e) {
        } catch (ClassNotFoundException e) {
            logger.error("Klassi {} ei leitud", className);
        }
        return null;
    }

    @Override
    public ResultHolder start() {
        target = loadClass();
        if (target == null) {
            logger.error("Klassi nimega {} ei õnnestunud laadida", className);
            System.out.println("Comment :=>> Kompileerimise probleem");
            System.exit(1);
        }

        TIME_LIMIT = Config.valueAsLong("function.goal.time", 1000L);

        inputProvider = aLong -> aLong;
        try {
            Path sourcePath = Paths.get(Config.valueAsString("source.java", "."), "DataGen.java");
            if (Files.exists(sourcePath)) {
                Class<?> loadedClass = new JavaExecutor(sourcePath).loadClass();
                if (loadedClass == null) throw new NoSuchMethodException();
                final Method inputClass = loadedClass.getMethod("getInput", long.class);
                inputProvider = aLong -> {
                    try {
                        return inputClass.invoke(null, aLong);
                    } catch (IllegalAccessException | InvocationTargetException e) {
                        throw new RuntimeException("Sisendi genereerimine ebaõnnestus", e);
                    }
                };
            } else {
                logger.info("Sisendi genereerimise faili DataGen.java ei õnnestunud kompileerida, seega sisendi suurust kasutatakse sisendina");
            }
        } catch (NoSuchMethodException e) {
            logger.info("Sisendi genereerimise meetodit getInput ei leitud failist DataGen.java, seega sisendi suurust kasutatakse sisendina");
        }

        try {
            evaluate();
        } catch (Exception e) {
            logger.error("Funktsiooni analüüs ebaõnnestus", e);
        }
        return results;
    }

    private void evaluate() throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException, InstantiationException {
        for (Method method : target.getDeclaredMethods()) {
            if (!Modifier.isPublic(method.getModifiers()) || !Modifier.isStatic(method.getModifiers())) {
                continue;
            }
            String functionName = Config.value("function.name");
            if (functionName == null || functionName.isEmpty()) {
                logger.error("Parameeter function.name peab leiduma failis config.properties");
                System.exit(1);
            }
            if (!Objects.equals(method.getName(), functionName)) {
                continue;
            }
            evaluateMethod(method);
            return;
        }
        logger.error("Sobiva nime ning signatuuriga avalikku staatilist meetodit ei leitud.");
    }

    private void evaluateMethod(Method method) throws IllegalAccessException, InvocationTargetException, ClassNotFoundException, IOException, InstantiationException {
        switch (Config.valueAsString("mode", "auto")) {
            case "auto":
                Long maxN = Config.valueAsLong("function.n.max", (long) Integer.MAX_VALUE);
                Long minN = Config.valueAsLong("function.n.min", 0L);
                Long pointCount = Config.valueAsLong("point.count", 100L);
                int repeats = 2;
                long limit;
                if (maxN - minN < pointCount || Config.valueAsLong("point.only", 0L) != 0) {
                    limit = maxN;
                } else {
                    limit = findMaxArgument(method, repeats, minN, maxN);
                }

                logger.info("Suurim kasutatav sisendi suurus: {}", limit);
                fillPoints(method, limit, Math.toIntExact(pointCount));
                break;
            case "manual":
                int testCase = 1;
                while (invokeManual(method, Integer.toString(testCase))) testCase++;

                break;
            default:
                logger.error("Seadistuse probleem: mode peab olema 'auto' või 'manual'");
                break;
        }
    }

    private boolean invokeManual(Method method, String testCase) throws InvocationTargetException, IllegalAccessException, ClassNotFoundException, IOException, InstantiationException {
        String testLocation = Config.value("source.tests");
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
        List<String> expectedOutput = Files.readAllLines(Paths.get(testLocation, String.format("output%s.txt", testCase)));

        if (functionOutput.equals(expectedOutput)) {
            logger.info("Programm väljastas õige vastuse '{}'", functionOutput);
        } else {
            logger.error("Programm väljastas '{}', aga pidi väljastama '{}'", functionOutput, expectedOutput);
        }
        return true;
    }

    private void fillPoints(Method method, long limit, int points) {
        ExecutorService executor = Executors.newFixedThreadPool(1);
        long increment = limit / points;
        if (limit <= points) increment = 1;
        for (long i = 0; i < limit; i += increment) {
            final long current_ = i;
            Future<List<Long>> submit = executor.submit(() -> evaluateMethod(method, 2, current_));
            try {
                submit.get(10 * TIME_LIMIT, TimeUnit.MILLISECONDS);
            } catch (TimeoutException | ExecutionException | InterruptedException e) {
                submit.cancel(true);
                if (e instanceof TimeoutException) {
                    logger.error("Sisendi suurusega {} läheb liiga kaua aega", i);
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
                List<Long> longs = submit.get(5 * TIME_LIMIT, TimeUnit.MILLISECONDS);
                average = longs.stream().mapToLong(Long::new).average().orElse(Double.MAX_VALUE);
            } catch (TimeoutException | InterruptedException e) {
                average = Double.MAX_VALUE;
                results.addTime(current, null);
                submit.cancel(true);
            } catch (ExecutionException e) {
                logger.error("Funktsiooni käivitamine ebaõnnestus: {}", e.getMessage());
                Class<?>[] parameterTypes = method.getParameterTypes();
                if (parameterTypes.length > 0)
                    logger.error("Oodati parameetrit {}", parameterTypes[0].getName());
                else logger.error("Funktsioon peaks võtma ühe parameetri");
                logger.error("DataGen.java getInput andis tüübi {}", inputProvider.apply(0L).getClass());
                System.exit(1);
                average = Double.MAX_VALUE; // for compiler
            }

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
        long time = System.currentTimeMillis();
        method.invoke(null, getInput(n));
        long timeSpent = System.currentTimeMillis() - time;
        if (printprogress)
            logger.info("Sisendi suurusega {} kulus aega: {}", n, timeSpent);
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

    private Object getInput(long n) {
        return inputProvider.apply(n);
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


}
