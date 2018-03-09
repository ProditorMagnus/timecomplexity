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

    @Override
    public String toString() {
        return "ResultHolder{" + "results=" + results + '}';
    }
}
