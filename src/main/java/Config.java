import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

public class Config {
    private static final Logger logger = LoggerFactory.getLogger(Config.class);
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

    public static String value(String key) {
        return get("config.properties").getProperty(key);
    }

    public static Long valueAsLong(String key) {
        return valueAsLong(key, null);
    }

    public static Long valueAsLong(String key, Long fallback) {
        String value = value(key);
        if (value == null || value.isEmpty()) {
            logger.info("No config entry for {}, fallback {} used", key, fallback);
            return fallback;
        }
        return Long.parseLong(value);
    }

    public static Double valueAsDouble(String key) {
        return valueAsDouble(key, null);
    }

    public static Double valueAsDouble(String key, Double fallback) {
        String value = value(key);
        if (value == null || value.isEmpty()) {
            logger.info("No config entry for {}, fallback {} used", key, fallback);
            return fallback;
        }
        return Double.parseDouble(value);
    }


    public static String valueAsString(String key, String fallback) {
        String value = value(key);
        if (value == null || value.isEmpty()) {
            logger.info("No config entry for {}, fallback {} used", key, fallback);
            return fallback;
        }
        return value;
    }
}
