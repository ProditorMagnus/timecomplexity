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

    /**
     * Leiab ning salvestab failinime alusel klassi nime
     *
     * @param source sisendfaili asukoht
     */
    public JavaExecutor(Path source) {
        super(source);
        className = source.getFileName().toString().replaceFirst("\\.java", "");
    }

    /**
     * Kompileerib faili ning tagastab selles leiduva klassi. Kompileeritavat faili otsitakse seadega source.java
     * määratud kaustast
     *
     * @return kompileeritud klass
     */
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
                URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(Config.valueAsString("source.java", ".")).toURI().toURL()});
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

    /**
     * Valmistab ette sisendi genereerimise DataGen.java meetodi getInput abil
     *
     * @return kogutud andmed
     */
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

    /**
     * Otsib vaadeldavast klassist etteantud nimega meetodi. Kasutatakse esimest leitud sobiva nimega meetodit
     */
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

    /**
     * Vastavalt seadistusele kasutatakse etteantud testkomplekti või suurima sisendi suuruse leidmist
     *
     * @param method meetod, mida käivitada
     */
    private void evaluateMethod(Method method) throws IllegalAccessException, InvocationTargetException, IOException, InstantiationException {
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

    /**
     * Käivitab etteantud testkomplekti ühe testjuhu. Kirjutab väljundisse, kas meetod andis õige tulemuse
     *
     * @param method   vaadeldav meetod
     * @param testCase testjuhu järjekorranumber
     * @return kas käivitamine õnnestus
     */
    private boolean invokeManual(Method method, String testCase) throws InvocationTargetException, IllegalAccessException, IOException, InstantiationException {
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

    /**
     * Teeb täiendavaid mõõtmisi, et saada piisavas koguses ning ühtlasema jaotusega andmeid
     *
     * @param method vaadeldav meetod
     * @param limit  suurim kasutatav sisendi väärtus
     * @param points mitu mõõtmist sooritada
     */
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

    /**
     * Kasutab topelkahendotsingut et leida, millise sisendi suurusega võtab meetodi käivitus seadetes määratud hulgal
     * aega
     *
     * @param method vaadeldav meetod
     * @param times  mitu korda sama sisendi suuruse mõõtmisi teha
     * @param minN   otsitava sisendi suuruse alumine piir
     * @param maxN   otsitava sisendi suuruse ülemine piir
     * @return sisendi suurus, mille korral käivitusaeg oli seadetes määratud piirides
     */
    private long findMaxArgument(final Method method, final int times, final long minN, final long maxN) {
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

    /**
     * Käivitab etteantud meetodit küsitud arv kordi
     *
     * @param method vaadeldav meetod
     * @param times  mõõtmiste arv
     * @param n      sisendi suurus
     * @return meetodi tööaegade järjend
     */
    private List<Long> evaluateMethod(Method method, int times, long n) throws InvocationTargetException, IllegalAccessException {
        List<Long> results = new ArrayList<>();
        for (int i = 0; i < times; i++) {
            results.add(evaluateMethodOnce(method, n));
        }
        return results;
    }

    /**
     * Käivitab meetodit etteantud suuruse sisendiga ning mõõdab selle tööaega
     *
     * @param method vaadeldav meetod
     * @param n      sisendi suurus
     * @return meetodi tööaeg
     */
    private long evaluateMethodOnce(Method method, long n) throws InvocationTargetException, IllegalAccessException {
        long time = System.currentTimeMillis();
        method.invoke(null, getInput(n));
        long timeSpent = System.currentTimeMillis() - time;
        if (printprogress)
            logger.info("Sisendi suurusega {} kulus aega: {}", n, timeSpent);
        results.addTime(n, timeSpent);
        return timeSpent;
    }

    /**
     * Käivitab meetodit etteantud suuruse sisendiga ning mõõdab selle tööaega
     * Erineb meetodist evaluateMethodOnce selle poolest, et tööaega ei tagastata, see ainult salvestatakse
     * Selle asemel tagastatakse käivitatud meetodi tagastusväärtus. Edasiarenduses saaks selle kaudu lisada väljundi
     * kontrollimist
     *
     * @param method vaadeldav meetod
     * @param size   sisendi suurus
     * @return meetodi tagastusväärtus
     */
    private Object timeMethod(Method method, long size) throws InvocationTargetException, IllegalAccessException {
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

    /**
     * Kasutab faili DataGen.java meetodit getInput, et koostada funktsioonile sisendväärtus.
     * Kui sellist faili või meetodit ei leidu, tagastatakse sisendi suurus
     *
     * @param n sisendi suurus
     * @return sisendi väärtus
     */
    private Object getInput(long n) {
        return inputProvider.apply(n);
    }


}
