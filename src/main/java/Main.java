import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        String fileName = Config.value("source.file");
        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("Parameeter source.file peab leiduma failis config.properties");
        FunctionExecutor executor;
        if (fileName.endsWith(".java")) {
            executor = new JavaExecutor(Paths.get(Config.valueAsString("source.java", "."), fileName));
        } else {
            executor = new PythonExecutor(Paths.get(Config.valueAsString("source.python", "."), fileName));
        }
        ResultHolder results = executor.start();
        if (Config.valueAsLong("output.printtimes", 1L) != 0)
            results.printResults();
//        results.getFunctionWithPython();
        System.out.println(results.getFunction());
        System.exit(0);
    }
}
