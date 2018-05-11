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

public class PythonExecutor extends FunctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);
    private long TIME_LIMIT;
    private boolean printProgress = Config.valueAsLong("output.printprogress", 0L) == 1;
    private final String pythonPath;

    public PythonExecutor(Path source, String pythonPath) {
        super(source);
        this.pythonPath = pythonPath;
    }

    //https://stackoverflow.com/a/1574857/3667389
    private static void copyStream(InputStream input, OutputStream output) throws IOException {
        byte[] buffer = new byte[4096];
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    @Override
    public ResultHolder start() {
        TIME_LIMIT = Config.valueAsLong("function.goal.time", 1000L);
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
                    logger.error("IOE", e);
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

    private void fillPoints(long limit, int points) {
        long increment = limit / points;
        if (limit <= points) increment = 1;
        for (long i = 0; i < limit; i += increment) {
            runPythonFunction(i);
        }
    }

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

    private void waitForProcess(Process p, long i) {
        try {
            if (!p.waitFor(10 * TIME_LIMIT, TimeUnit.MILLISECONDS)) {
                throw new TimeoutException("Timed out");
            }
        } catch (InterruptedException e) {
            p.destroy();
            logger.error("Timed out", e);
        } catch (TimeoutException e) {
            p.destroy();
            logger.error("Sisendi suurusega {} läheb liiga kaua aega", i);
            System.exit(1);
        }
    }


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
            logger.info("Program wrote {}", functionOutput);
            List<String> expectedOutput = Files.readAllLines(Paths.get(testLocation, String.format("output%s.txt", testCase)));

            long endTime = System.currentTimeMillis();
            long timeTaken = endTime - time;
            logger.info("Time taken: {}", timeTaken);
            results.addTime(input_size, timeTaken);
            if (functionOutput.equals(expectedOutput)) {
                logger.info("function wrote correct value {}", functionOutput);
            } else {
                logger.error("function wrote incorrect value {} expected {}", functionOutput, expectedOutput);
            }

        } catch (IOException e) {
            logger.error("process error", e);
        }
        return true;
    }


}
