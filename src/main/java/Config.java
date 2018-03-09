import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Config {
    private static final Map<String, Properties> cache = new HashMap<>();

    private static Properties loadConfig(String fileName) {
        Properties prop = new Properties();
        try (InputStream inputStream = Files.newInputStream(Paths.get(fileName))) {
            prop.load(inputStream);
        } catch (IOException e) {
            return prop;
        }
        cache.put(fileName, prop);
        return prop;
    }

    public static Properties get(String fileName) {
        return cache.computeIfAbsent(fileName, Config::loadConfig);
    }
}
