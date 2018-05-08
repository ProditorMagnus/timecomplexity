import java.nio.file.Paths;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        Properties properties = Config.get("config.properties");
        String fileName = properties.getProperty("loc.source.file");
        FunctionExecutor executor;
        if (fileName.endsWith(".java")) {
            executor = new JavaExecutor(Paths.get(properties.getProperty("loc.source.java"), fileName));
        } else {
            executor = new PythonExecutor(Paths.get(properties.getProperty("loc.source.python"), fileName));
        }
        ResultHolder results = executor.start();
        System.out.println(results);
        results.printResults();
        results.getFunctionWithPython();
        System.out.println(results.getFunction());
        System.out.println("end");
//        System.exit(0); // TODO find way without system.exit to kill submitted tasks
    }
}
