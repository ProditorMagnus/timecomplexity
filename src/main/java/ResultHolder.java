import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Klass ResultHolder on kasutusel käivituse käigus saadud andmete kogumiseks ning esitamiseks.
 */
public class ResultHolder {
    private static final Logger logger = LoggerFactory.getLogger(ResultHolder.class);
    private Map<Long, List<Long>> results = new HashMap<>();

    /**
     * Salvestab käivituse andmed
     *
     * @param inputSize sisendi suurus
     * @param time      kulunud aeg millisekundites
     */
    public synchronized void addTime(long inputSize, Long time) {
        results.putIfAbsent(inputSize, new ArrayList<>());
        results.get(inputSize).add(time);
    }

    /**
     * Leiab ühesuguse sisendite suuruste tööaegadest keskmise
     *
     * @return sisendi suuruse järgi sorteeritud keskmised tööajad
     */
    private TreeMap<Long, Long> average() {
        TreeMap<Long, Long> average = new TreeMap<>();
        for (Map.Entry<Long, List<Long>> entry : results.entrySet()) {
            OptionalDouble avg = entry.getValue().stream().filter(Objects::nonNull).mapToLong(v -> v).average();
            if (avg.isPresent()) {
                average.put(entry.getKey(), (long) avg.getAsDouble());
            } else {
                average.put(entry.getKey(), null);
            }
        }
        return average;
    }

    /**
     * Väljastab kogutud andmed sisendi suuruse alusel sorteeritult
     */
    public void printResults() {
        TreeMap<Long, Long> average = average();
        NavigableSet<Long> keySet = average.navigableKeySet();
        logger.info("Sisendi suurus, Kulunud aeg");
        for (Long key : keySet) {
            logger.info("{} , {}", key, average.get(key));
        }
    }

    /**
     * Kutsub välja ajalise keerukuse leidmise meetodi klassist ComplexityFinder
     *
     * @return Moodle'i formaadis kommentaar, mis sisaldab leitud ajalist keerukust
     */
    public String getFunction() {
        double[] x = new double[results.size()];
        double[] y = new double[results.size()];
        int i = 0;
        for (Map.Entry<Long, Long> entry : average().entrySet()) {
            x[i] = entry.getKey();
            y[i] = entry.getValue();
            i++;
        }
        String function = ComplexityFinder.findFunction(x, y);
        if (function.isEmpty()) logger.warn("Liiga vähe andmeid, et keerukust leida, andmemaht: {}", results.size());
        return "Comment :=>> Ajaline keerukus: O(" + function + ")";
    }

    @Override
    public String toString() {
        return "ResultHolder{" + "results=" + results + '}';
    }
}
