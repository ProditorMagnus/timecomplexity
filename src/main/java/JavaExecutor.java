import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.tools.*;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class JavaExecutor extends FunctionExecutor {
    private static final Logger logger = LoggerFactory.getLogger(JavaExecutor.class);
    private String className;

    public JavaExecutor(Path source) {
        super(source);
        className = source.getFileName().toString().replaceFirst("\\.java", "");
    }

    private Class<?> loadClass() {
        // https://stackoverflow.com/a/21544850/3667389
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        try (StandardJavaFileManager fileManager = compiler.getStandardFileManager(diagnostics, null, null)) {

            List<String> optionList = new ArrayList<>();
            optionList.add("-classpath");
            optionList.add(System.getProperty("java.class.path"));
//        optionList.add(System.getProperty("java.class.path") + ";dist/InlineCompiler.jar");

            Iterable<? extends JavaFileObject> compilationUnit
                    = fileManager.getJavaFileObjectsFromFiles(Collections.singletonList(source.toFile()));
            JavaCompiler.CompilationTask task = compiler.getTask(
                    null,
                    fileManager,
                    diagnostics,
                    optionList,
                    null,
                    compilationUnit);
            if (task.call()) {
                // Create a new custom class loader, pointing to the directory that contains the compiled
                // classes, this should point to the top of the package structure!
                URLClassLoader classLoader = new URLClassLoader(new URL[]{new File(Config.get("config.properties").getProperty("loc.source.java")).toURI().toURL()});
                // Load the class from the classloader by name....
                Class<?> loadedClass = classLoader.loadClass(className);
                logger.info("Loaded class");
                return loadedClass;
            } else {
                for (Diagnostic<? extends JavaFileObject> diagnostic : diagnostics.getDiagnostics()) {
                    System.out.format("Error on line %d in %s%n",
                            diagnostic.getLineNumber(),
                            diagnostic.getSource().toUri());
                }
            }
        } catch (IOException e) {
            logger.error("IOException", e);
        } catch (ClassNotFoundException e) {
            logger.error("Class {} not found", className);
        }
        return null;
    }

    @Override
    public ResultHolder start() {
        Class<?> loadedClass = loadClass();
        if (loadedClass == null) {
            logger.error("no class loaded");
            return null;
        }
        Evaluator evaluator = new Evaluator(loadedClass);
        return evaluator.estimate();
    }
}
