import java.util.*;

public class ResultHolder {
    private Map<Long, List<Long>> results = new HashMap<>();

    public synchronized void addTime(long inputSize, Long time) {
        results.putIfAbsent(inputSize, new ArrayList<>());
        results.get(inputSize).add(time);
    }

    public synchronized void addTimes(long inputSize, Collection<Long> times) {
        results.putIfAbsent(inputSize, new ArrayList<>());
        results.get(inputSize).addAll(times);
    }

    public Map<Long, Long> average() {
        Map<Long, Long> average = new HashMap<>();
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
}
