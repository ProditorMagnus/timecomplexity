import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

/**
 * Klass PythonExecutor võimaldab keeles Python koostatud funktsioonide ning failide käivitamist.
 */
public class PythonExecutor extends FunctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);
    private long TIME_LIMIT;
    private boolean printProgress = Config.valueAsLong("output.printprogress", 0L) == 1;
    private final String pythonPath;


    /**
     * @param source     sisendfaili asukoht
     * @param pythonPath kaust, milles .py failid on
     */
    public PythonExecutor(Path source, String pythonPath) {
        super(source);
        this.pythonPath = pythonPath;
    }

    /**
     * Kopeerib sisendvoost loetud info väljundvoogu
     *
     * @param input  sisendvoog
     * @param output väljundvoog
     */
    //https://stackoverflow.com/a/1574857/3667389
    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    /**
     * Genereerib faili python_runner.py, mis kutsub välja vaadeldavat funktsiooni
     *
     * @return kogutud andmed
     */
    @Override
    public ResultHolder start() {
        TIME_LIMIT = Config.valueAsLong("function.goal.time", 2000L);
        switch (Config.valueAsString("mode", "auto")) {
            case "auto":
                Long maxN = Config.valueAsLong("function.n.max", (long) Integer.MAX_VALUE);
                Long minN = Config.valueAsLong("function.n.min", 0L);
                Long pointCount = Config.valueAsLong("point.count", 100L);
                String sourceFile = Config.value("source.file").replaceFirst("\\.py$", "");
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(pythonPath, "python_runner.py"), Charset.forName("utf8"), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                    String runnerBase = "import %s as source%n" +
                            "import time%n" +
                            "import sys%n" +
                            "try:%n" +
                            "    import datagen%n" +
                            "    input_value = datagen.getInput(int(sys.argv[1]))%n" +
                            "except ImportError:%n" +
                            "    input_value = int(sys.argv[1])%n" +
                            "start_time = (time.time())%n" +
                            "source.%s(input_value)%n" +
                            "end_time = (time.time())%n" +
                            "print(1000*(end_time-start_time))%n";
                    writer.write(String.format(runnerBase, sourceFile, Config.value("function.name")));
                } catch (IOException e) {
                    logger.error("Faili probleem", e);
                    return results;
                }
                int repeats = 2;
                long limit;
                if (maxN - minN < pointCount || Config.valueAsLong("point.only", 0L) != 0) {
                    limit = maxN;
                } else {
                    limit = findMaxArgument(repeats, minN, maxN);
                }
                logger.info("Suurim kasutatav sisendi suurus: {}", limit);
                fillPoints(limit, Math.toIntExact(pointCount));
                break;
            case "manual":
                int testCase = 1;
                while (invokeManual(Integer.toString(testCase))) testCase++;
                break;
        }
        return results;
    }

    /**
     * Kasutab topelkahendotsingut et leida, millise sisendi suurusega võtab meetodi käivitus seadetes määratud hulgal
     * aega
     *
     * @param repeats mitu korda sama sisendi suuruse mõõtmisi teha
     * @param minN    otsitava sisendi suuruse alumine piir
     * @param maxN    otsitava sisendi suuruse ülemine piir
     * @return sisendi suurus, mille korral käivitusaeg oli seadetes määratud piirides
     */
    private long findMaxArgument(int repeats, Long minN, Long maxN) {
        GuessProvider guessProvider = new GuessProvider(minN, maxN);
        while (true) {
            long current = guessProvider.getCurrent();
            List<Long> currentTimes = new ArrayList<>();
            for (int i = 0; i < repeats; i++) {
                long time = runPythonFunction(current);
                currentTimes.add(time);
            }
            double average = currentTimes.stream().reduce(0L, (aLong, aLong2) -> aLong + aLong2) / currentTimes.size();
            if (guessProvider.findNext(average)) {
                break;
            }
        }
        return guessProvider.getCurrent();
    }

    /**
     * Teeb täiendavaid mõõtmisi, et saada piisavas koguses ning ühtlasema jaotusega andmeid
     *
     * @param limit  suurim kasutatav sisendi väärtus
     * @param points mitu mõõtmist sooritada
     */
    private void fillPoints(long limit, int points) {
        long increment = limit / points;
        if (limit <= points) increment = 1;
        for (long i = 0; i < limit; i += increment) {
            runPythonFunction(i);
        }
    }

    /**
     * Käivitab genereeritud Pythoni faili uue protsessina
     * See fail käivitab vaadeldavat funktsiooni ning mõõdab selle tööaega
     *
     * @param current sisendi suurus
     * @return funktsiooni tööaeg
     */
    private long runPythonFunction(Long current) {
        ProcessBuilder pb = new ProcessBuilder("python3", Paths.get(pythonPath, "python_runner.py").toAbsolutePath().toString(), current.toString());
        pb.redirectErrorStream(true);
        try {
            long time = System.currentTimeMillis();
            Process p = pb.start();
            InputStream stdout = p.getInputStream();
            waitForProcess(p, current);
            InputStreamReader stdoutStreamReader = new InputStreamReader(stdout, Charset.forName("UTF-8"));
            BufferedReader stdoutReader = new BufferedReader(stdoutStreamReader);
            List<String> functionOutput = stdoutReader.lines().collect(Collectors.toList());
            stdout.close();
            stdoutStreamReader.close();
            stdoutReader.close();
            long pythonTime = Math.round(Double.parseDouble(functionOutput.get(functionOutput.size() - 1)));
            long endTime = System.currentTimeMillis();
            long timeTaken = endTime - time;
            if (printProgress) {
                logger.info("Sisendi suurusega {} kulus Java vaatepunktist aega: {}", current, timeTaken);
                logger.info("Sisendi suurusega {} kulus Pythoni vaatepunktist aega: {}", current, pythonTime);
            }
            results.addTime(current, pythonTime);
            return pythonTime;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }

    /**
     * Ootab, kuni protsess lõpetab töö
     *
     * @param p protsess
     * @param i sisendi suurus
     */
    private void waitForProcess(Process p, long i) {
        try {
            if (!p.waitFor(10 * TIME_LIMIT, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Timed out");
            }
        } catch (InterruptedException e) {
            p.destroy();
            logger.error("Töö katkestati", e);
        } catch (TimeoutException e) {
            p.destroy();
            logger.error("Sisendi suurusega {} läheb liiga kaua aega", i);
            System.exit(1);
        }
    }


    /**
     * Käivitab etteantud testkomplekti ühe testjuhu. Kirjutab väljundisse, kas meetod andis õige tulemuse
     *
     * @param testCase testjuhu järjekorranumber
     * @return kas käivitamine õnnestus
     */
    private boolean invokeManual(String testCase) {
        String testLocation = Config.value("source.tests");
        if (!Files.exists(Paths.get(testLocation, String.format("meta%s.txt", testCase)))) {
            return false;
        }
        long input_size = Long.parseLong(Config.get(Paths.get(testLocation, String.format("meta%s.txt", testCase)).toString()).getProperty("input_size"));
        ProcessBuilder pb = new ProcessBuilder("python3", source.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        try {
            long time = System.currentTimeMillis();
            Process p = pb.start();
            OutputStream stdin = p.getOutputStream();
            InputStream stdout = p.getInputStream();
            InputStream fileIS = Files.newInputStream(Paths.get(testLocation, String.format("input%s.txt", testCase)));
            InputStream inputStream = new BufferedInputStream(fileIS);
            copyStream(inputStream, stdin);
            stdin.flush();
            stdin.close();
            inputStream.close();
            fileIS.close();

            waitForProcess(p, input_size);
            InputStreamReader stdoutStreamReader = new InputStreamReader(stdout, Charset.forName("UTF-8"));
            BufferedReader stdoutReader = new BufferedReader(stdoutStreamReader);
            List<String> functionOutput = stdoutReader.lines().collect(Collectors.toList());
            stdout.close();
            stdoutStreamReader.close();
            stdoutReader.close();
            List<String> expectedOutput = Files.readAllLines(Paths.get(testLocation, String.format("output%s.txt", testCase)));

            long endTime = System.currentTimeMillis();
            long timeTaken = endTime - time;
            results.addTime(input_size, timeTaken);
            if (functionOutput.equals(expectedOutput)) {
                logger.info("Programm väljastas õige vastuse '{}'", functionOutput);
            } else {
                logger.error("Programm väljastas '{}', aga pidi väljastama '{}'", functionOutput, expectedOutput);
            }

        } catch (IOException e) {
            logger.error("Probleem käivitamisega", e);
        }
        return true;
    }


}
