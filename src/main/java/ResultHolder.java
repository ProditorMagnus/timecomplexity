import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class ResultHolder {
    private static final Logger logger = LoggerFactory.getLogger(ResultHolder.class);
    private Map<Long, List<Long>> results = new HashMap<>();

    public synchronized void addTime(long inputSize, Long time) {
        results.putIfAbsent(inputSize, new ArrayList<>());
        results.get(inputSize).add(time);
    }

    public synchronized void addTimes(long inputSize, Collection<Long> times) {
        results.putIfAbsent(inputSize, new ArrayList<>());
        results.get(inputSize).addAll(times);
    }

    public TreeMap<Long, Long> average() {
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

    public void printResults() {
        TreeMap<Long, Long> average = average();
        NavigableSet<Long> keySet = average.navigableKeySet();
        for (Long key : keySet) {
            System.out.printf("%d , %d%n", key, average.get(key));
        }
    }

    private String getSize() {
        TreeMap<Long, Long> average = average();
        NavigableSet<Long> keySet = average.navigableKeySet();
        StringBuilder sb = new StringBuilder();
        for (Long key : keySet) {
            sb.append(key);
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private String getTime() {
        TreeMap<Long, Long> average = average();
        NavigableSet<Long> keySet = average.navigableKeySet();
        StringBuilder sb = new StringBuilder();
        for (Long key : keySet) {
            sb.append(average.get(key));
            sb.append(",");
        }
        sb.deleteCharAt(sb.length() - 1);
        return sb.toString();
    }

    private void comment(String s) {
        System.out.println("Comment :=>> " + s);
    }

    public void getFunction() {
        if (results.size() < 10) {
            logger.warn("Not enough results to attempt finding function, size: {}", results.size());
            return;
        }
        Path path = Paths.get("python", "find_complexity.py");
        ProcessBuilder pb = new ProcessBuilder("python3", path.toAbsolutePath().toString());
        pb.redirectErrorStream(true);
        try {
            Process p = pb.start();
            OutputStream stdin = p.getOutputStream();
            InputStream stdout = p.getInputStream();
            BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(stdin));
            BufferedReader reader = new BufferedReader(new InputStreamReader(stdout));
            writer.write(getSize());
            writer.newLine();
            writer.write(getTime());
            writer.newLine();
            writer.flush();
            writer.close();
            String result = null;
            Scanner scanner = new Scanner(reader);
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if (line.contains(": time = ")) result = line;
                logger.debug("find_complexity says {}", line);
                System.out.println(line);
            }
            logger.info(result);
            comment(result);
            try {
                if (!p.waitFor(10000, TimeUnit.MILLISECONDS)) {
                    throw new InterruptedException("Timed out");
                }
            } catch (InterruptedException e) {
                p.destroy();
            }

        } catch (IOException e) {
            logger.error("process error", e);
        }
    }

    @Override
    public String toString() {
        return "ResultHolder{" + "results=" + results + '}';
    }
}
