import java.util.HashSet;
import java.util.Set;

public class GuessProvider {
    private boolean found_windows;
    private long maxN;
    private long low;
    private long high;
    private long current;
    private Set<Long> attemptedValues;
    private final double TIME_OFFSET;
    private final long TIME_LIMIT;

    /**
     * Koostab topeltkahendotsingu algseisu
     *
     * @param low  alumine piir
     * @param high ülemine piir
     */
    public GuessProvider(long low, long high) {
        this.maxN = high;
        this.low = low;
        this.high = high;
        this.current = low;
        this.found_windows = false;
        this.attemptedValues = new HashSet<>();
        TIME_LIMIT = Config.valueAsLong("function.goal.time", 1000L);
        TIME_OFFSET = Config.valueAsDouble("function.goal.offset", 0.25);
    }

    /**
     * Tagastab praegu kasutatava sisendi suuruse
     *
     * @return praegune sisendi suurus
     */
    public long getCurrent() {
        return current;
    }

    /**
     * Viib läbi ühe topeltkahendotsingu sammu. Tagastab true, kui töö on lõppenud
     *
     * @param average funktsiooni tööaeg millisekundites
     * @return kas otsitav sisendi suurus on leitud
     */
    public boolean findNext(double average) {
        if (average < TIME_LIMIT - TIME_LIMIT * TIME_OFFSET) {
            low = current;
            if (!found_windows) {
                current *= 2;
                current++;
            } else {
                current = (low + high) / 2;
            }
        } else if (average > TIME_LIMIT + TIME_LIMIT * TIME_OFFSET) {
            high = current;
            found_windows = true;
            current = (low + high) / 2;
            if (attemptedValues.contains(current)) {
                current--;
            }
        } else {
            found_windows = true;
        }
        if (current > maxN) {
            current = maxN;
            found_windows = true;
        }
        if (attemptedValues.contains(current)) {
            found_windows = true;
        }
        // Detect overflow
        if (current < 0) {
            current = Long.MAX_VALUE;
            found_windows = true;
        }
        return found_windows;
    }
}
