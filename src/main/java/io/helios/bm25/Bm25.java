package io.helios.bm25;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Bm25 {
    public static final double DEFAULT_K1 = 1.2;
    public static final double DEFAULT_B = 0.75;

    private Bm25() {
    }

    public static double idf(int n, int df) {
        if (n <= 0 || df < 0) {
            return 0;
        }
        return Math.log((n - df + 0.5) / (df + 0.5) + 1);
    }

    public static double termScore(int tf, double dl, double avgdl, double idf, double k1, double b) {
        if (tf <= 0 || idf == 0) {
            return 0;
        }
        if (avgdl <= 0) {
            avgdl = 1;
        }
        if (dl < 0) {
            dl = 0;
        }
        double denom = tf + k1 * (1 - b + b * dl / avgdl);
        if (denom == 0) {
            return 0;
        }
        return idf * (tf * (k1 + 1)) / denom;
    }

    public static double score(List<String> query, Map<String, Integer> tf, double dl, double avgdl,
                               int n, Map<String, Integer> df, double k1, double b) {
        if (n <= 0 || query == null || query.isEmpty()) {
            return 0;
        }
        Set<String> seen = new LinkedHashSet<>();
        double total = 0;
        for (String term : query) {
            if (!seen.add(term)) {
                continue;
            }
            int freq = tf == null ? 0 : tf.getOrDefault(term, 0);
            int docFreq = df == null ? 0 : df.getOrDefault(term, 0);
            total += termScore(freq, dl, avgdl, idf(n, docFreq), k1, b);
        }
        return total;
    }
}
