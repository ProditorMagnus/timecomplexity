import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.TimeUnit;

public class PythonExecutor extends FunctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(PythonExecutor.class);

    public PythonExecutor(Path source) {
        super(source);
    }

    @Override
    public void start() {

    }


    public void run() {
        Path path = Paths.get("python", "runner.py");
        ProcessBuilder pb = new ProcessBuilder("python", path.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        try {
            long time = System.currentTimeMillis();
            Process p = pb.start();
            try {
                if (!p.waitFor(10000, TimeUnit.MILLISECONDS)) {
                    throw new InterruptedException("Timed out");
                }
            } catch (InterruptedException e) {
                p.destroy();
                logger.error("Timed out", e);
            }
            long endTime = System.currentTimeMillis();
            logger.info("Time taken: {}", endTime - time);

        } catch (IOException e) {
            logger.error("process error", e);
        }
    }
}
