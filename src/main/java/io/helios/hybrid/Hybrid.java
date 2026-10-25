package io.helios.hybrid;

import io.helios.index.Hit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public final class Hybrid {
    private Hybrid() {
    }

    public record Candidate(UUID passageId, double bm25, double vector, List<String> matchedTerms) {
    }

    public static double[] minMax(double[] values) {
        double[] out = new double[values.length];
        if (values.length == 0) {
            return out;
        }
        double min = values[0];
        double max = values[0];
        for (int i = 1; i < values.length; i++) {
            min = Math.min(min, values[i]);
            max = Math.max(max, values[i]);
        }
        if (max == min) {
            double fill = max > 0 ? 1 : 0;
            java.util.Arrays.fill(out, fill);
            return out;
        }
        double span = max - min;
        for (int i = 0; i < values.length; i++) {
            out[i] = (values[i] - min) / span;
        }
        return out;
    }

    public static List<Hit> combine(List<Candidate> items, double alpha) {
        if (alpha < 0) {
            alpha = 0;
        }
        if (alpha > 1) {
            alpha = 1;
        }
        double[] bm25 = new double[items.size()];
        double[] vec = new double[items.size()];
        for (int i = 0; i < items.size(); i++) {
            bm25[i] = items.get(i).bm25();
            vec[i] = items.get(i).vector();
        }
        double[] nb = minMax(bm25);
        double[] nv = minMax(vec);
        List<Hit> hits = new ArrayList<>(items.size());
        for (int i = 0; i < items.size(); i++) {
            Candidate item = items.get(i);
            hits.add(new Hit(item.passageId(), alpha * nb[i] + (1 - alpha) * nv[i], item.matchedTerms()));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> h.passageId().toString()));
        return hits;
    }
}
