package io.helios.coordinator;

import java.util.HashMap;
import java.util.Map;

public final class HealthDetector {
    private final Map<String, Integer> fails = new HashMap<>();
    private final Map<String, Boolean> down = new HashMap<>();
    private final int threshold;

    public HealthDetector(int threshold) {
        this.threshold = threshold < 1 ? 1 : threshold;
    }

    public synchronized void report(String id, boolean ok) {
        if (ok) {
            fails.put(id, 0);
            down.put(id, false);
            return;
        }
        int n = fails.getOrDefault(id, 0) + 1;
        fails.put(id, n);
        if (n >= threshold) {
            down.put(id, true);
        }
    }

    public synchronized boolean healthy(String id) {
        return !Boolean.TRUE.equals(down.get(id));
    }

    public synchronized void markDown(String id) {
        down.put(id, true);
        fails.put(id, threshold);
    }
}
