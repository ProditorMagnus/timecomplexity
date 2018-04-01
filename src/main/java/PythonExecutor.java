import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class PythonExecutor extends FunctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);

    public PythonExecutor(Path source) {
        super(source);
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
        int testCase = 1;
        while (invokeManual(Integer.toString(testCase))) testCase++;
        return results;
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
