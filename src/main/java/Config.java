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

    /**
     * Loeb sisse ning salvestab mällu etteantud nimega seadete faili
     *
     * @param fileName failinimi
     * @return seadete fail
     */
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

    /**
     * Tagastab etteantud nimega seadete faili. Kui see on mälus olemas, tagastab selle, puudumise korral loeb seaded
     * failist
     *
     * @param fileName failinimi
     * @return seadete fail
     */
    public static Properties get(String fileName) {
        return cache.computeIfAbsent(fileName, Config::loadConfig);
    }

    /**
     * Otsib peamisest seadete failist config.properties küsitud võtme väärtust
     *
     * @param key seade nimi
     * @return seade väärtus
     */
    public static String value(String key) {
        return get("config.properties").getProperty(key);
    }

    /**
     * Otsib peamisest seadete failist config.properties küsitud võtme väärtust, puudumise korral kasutab teise
     * argumendina antud väärtust
     *
     * @param key      seade nimi
     * @param fallback väärtus, mida kasutada seade puudumise korral
     * @return seade väärtus
     */
    public static Long valueAsLong(String key, Long fallback) {
        String value = value(key);
        if (value == null || value.isEmpty()) {
            logger.info("Seadistus {} puudub, kasutatakse {}", key, fallback);
            return fallback;
        }
        return Long.parseLong(value);
    }

    /**
     * Otsib peamisest seadete failist config.properties küsitud võtme väärtust, puudumise korral kasutab teise
     * argumendina antud väärtust
     *
     * @param key      seade nimi
     * @param fallback väärtus, mida kasutada seade puudumise korral
     * @return seade väärtus
     */
    public static Double valueAsDouble(String key, Double fallback) {
        String value = value(key);
        if (value == null || value.isEmpty()) {
            logger.info("Seadistus {} puudub, kasutatakse {}", key, fallback);
            return fallback;
        }
        return Double.parseDouble(value);
    }

    /**
     * Otsib peamisest seadete failist config.properties küsitud võtme väärtust, puudumise korral kasutab teise
     * argumendina antud väärtust
     *
     * @param key      seade nimi
     * @param fallback väärtus, mida kasutada seade puudumise korral
     * @return seade väärtus
     */
    public static String valueAsString(String key, String fallback) {
        String value = value(key);
        if (value == null || value.isEmpty()) {
            logger.info("Seadistus {} puudub, kasutatakse {}", key, fallback);
            return fallback;
        }
        return value;
    }
}
