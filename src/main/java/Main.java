import java.nio.file.Paths;
import java.util.Properties;

public class Main {
    public static void main(String[] args) {
        Properties properties = Config.get("config.properties");
//        System.out.println(properties.getProperty("loc.source.java"));
//        Evaluator evaluator = new Evaluator(Example_2.class);
        FunctionExecutor executor = new JavaExecutor(Paths.get(properties.getProperty("loc.source.java"), properties.getProperty("loc.source.file")));
        ResultHolder results = executor.start();
//        new PythonExecutor().run();
//        ResultHolder results = executor.start();
//        results.printResults();
        results.getFunction();
        System.out.println("end");
//        System.exit(0); // TODO find way without system.exit to kill submitted tasks
    }
}
