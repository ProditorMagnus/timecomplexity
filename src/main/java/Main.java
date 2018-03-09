import java.nio.file.Paths;
import java.util.Properties;

public class Main {

    public static void main(String[] args) {
        Properties properties = Config.get("config.properties");
//        System.out.println(properties.getProperty("loc.source.java"));
//        Evaluator evaluator = new Evaluator(Example_3.class);
//        evaluator.estimate();
//        new PythonExecutor().run();
        FunctionExecutor executor = new JavaExecutor(Paths.get(properties.getProperty("loc.source.java"), properties.getProperty("loc.source.file")));
        executor.start();
        System.out.println("end");
//        System.exit(0); // TODO find way without system.exit to kill submitted tasks
    }
}
