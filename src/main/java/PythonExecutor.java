import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PythonExecutor extends FunctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);
    private final double PRECISION;
    private final long TIME_LIMIT;

    public PythonExecutor(Path source) {
        super(source);
        TIME_LIMIT = Config.valueAsLong("function.goal.time", 1000L);
        PRECISION = Config.valueAsDouble("function.goal.offset", 0.75);
    }

    //https://stackoverflow.com/a/1574857/3667389
    private static void copyStream(InputStream input, OutputStream output)
            throws IOException {
        byte[] buffer = new byte[1024]; // Adjust if you want
        int bytesRead;
        while ((bytesRead = input.read(buffer)) != -1) {
            output.write(buffer, 0, bytesRead);
        }
    }

    @Override
    public ResultHolder start() {
        switch (Config.value("mode")) {
            case "auto":
                Long maxN = Config.valueAsLong("function.n.max", (long) Integer.MAX_VALUE);
                Long minN = Config.valueAsLong("function.n.min", 0L);
                Long pointCount = Config.valueAsLong("result.point.count", 100L);
                String pythonPath = Config.value("loc.source.python");
                String sourceFile = Config.value("loc.source.file").replaceFirst("\\.py$", "");
                try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(pythonPath, "python_runner.py"), Charset.forName("utf8"), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE, StandardOpenOption.CREATE)) {
                    String runnerBase = "import %s as source%n" +
                            "import time%n" +
                            "import sys%n" +
                            "start_time=(time.time())%n" +
                            "source.%s(int(sys.argv[1]))%n" +
                            "end_time=(time.time())%n" +
                            "print(1000*(end_time-start_time))%n";
                    writer.write(String.format(runnerBase, sourceFile, Config.value("function.name")));
                } catch (IOException e) {
                    logger.error("IOE", e);
                }
                int repeats = 2;
                long limit;
                if (maxN - minN < pointCount || Config.valueAsLong("function.n.point_only", 0L) != 0) {
                    limit = maxN;
                } else {
                    limit = findMaxArgument(repeats, minN, maxN);
                }
                logger.info("Limit {}", limit);
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
        String pythonPath = Config.value("loc.source.python");
        long low = minN;
        long high = maxN;
        long current = minN;
        boolean found_windows = false;
        Set<Long> attemptedValues = new HashSet<>();
        while (true) {
            if (!attemptedValues.add(current)) {
                logger.error("Same current attempted multiple times {} {} {}", low, current, high);
                break;
            }
            List<Long> currentTimes = new ArrayList<>();
            for (int i = 0; i < repeats; i++) {
                long time = runPythonFunction(pythonPath, current);
                currentTimes.add(time);
            }
            double average = currentTimes.stream().reduce(0L, (aLong, aLong2) -> aLong + aLong2) / currentTimes.size();
            if (average < TIME_LIMIT - TIME_LIMIT * PRECISION) {
                low = current;
                if (!found_windows) {
                    current *= 2;
                    current++;
                } else {
                    current = (low + high) / 2;
                }
            } else if (average > TIME_LIMIT + TIME_LIMIT * PRECISION) {
                high = current;
                found_windows = true;
                current = (low + high) / 2;
                if (attemptedValues.contains(current)) {
                    current--;
                }
            } else {
                break;
            }
            if (current > maxN) {
                current = maxN;
                break;
            }
        }
        return current;
    }

    private void fillPoints(long limit, int points) {
        String pythonPath = Config.value("loc.source.python");
        long increment = limit / points;
        if (limit <= points) increment = 1;
        for (long i = 0; i < limit; i += increment) {
            logger.info("fillPoints {}", i);
            runPythonFunction(pythonPath, i);
        }
    }

    private long runPythonFunction(String pythonPath, Long current) {
        ProcessBuilder pb = new ProcessBuilder("python3", Paths.get(pythonPath, "python_runner.py").toAbsolutePath().toString(), current.toString());
        pb.redirectErrorStream(true);
        try {
            long time = System.currentTimeMillis();
            Process p = pb.start();
            InputStream stdout = p.getInputStream();
            try {
                if (!p.waitFor(10000, TimeUnit.MILLISECONDS)) {
                    throw new InterruptedException("Timed out");
                }
            } catch (InterruptedException e) {
                p.destroy();
                logger.error("Timed out", e);
            }
            InputStreamReader stdoutStreamReader = new InputStreamReader(stdout, Charset.forName("UTF-8"));
            BufferedReader stdoutReader = new BufferedReader(stdoutStreamReader);
            List<String> functionOutput = stdoutReader.lines().collect(Collectors.toList());
            stdout.close();
            stdoutStreamReader.close();
            stdoutReader.close();
            logger.info("Program wrote {}", functionOutput);
            long pythonTime = Math.round(Double.parseDouble(functionOutput.get(functionOutput.size() - 1)));
            long endTime = System.currentTimeMillis();
            long timeTaken = endTime - time;
            logger.info("Time taken: {}", timeTaken);
            logger.info("Time taken as reported by python: {}", pythonTime);
            results.addTime(current, pythonTime);
            return pythonTime;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return -1;
    }


    public boolean invokeManual(String testCase) {
        String testLocation = Config.value("loc.tests");
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

            try {
                if (!p.waitFor(10000, TimeUnit.MILLISECONDS)) {
                    throw new InterruptedException("Timed out");
                }
            } catch (InterruptedException e) {
                p.destroy();
                logger.error("Timed out", e);
            }
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
