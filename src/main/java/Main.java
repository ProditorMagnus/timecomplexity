import java.nio.file.Paths;

/**
 * Peaklass, mida jar-faili käivitamisel esimesena kasutatakse.
 */
public class Main {
    /**
     * Alustab programmi tööd
     */
    public static void main(String[] args) {
        String fileName = Config.value("source.file");
        if (fileName == null || fileName.isEmpty())
            throw new IllegalArgumentException("Parameeter source.file peab leiduma failis config.properties");
        FunctionExecutor executor;
        if (fileName.endsWith(".java")) {
            executor = new JavaExecutor(Paths.get(Config.valueAsString("source.java", "."), fileName));
        } else {
            String pythonPath = Config.valueAsString("source.python", ".");
            executor = new PythonExecutor(Paths.get(pythonPath, fileName), pythonPath);
        }
        ResultHolder results = executor.start();
        if (Config.valueAsLong("output.printtimes", 1L) != 0)
            results.printResults();
        System.out.println(results.getFunction());
        System.exit(0);
    }
}
