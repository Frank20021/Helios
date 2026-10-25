package io.helios.coordinator;

import io.helios.bm25.Bm25;
import io.helios.index.Candidate;
import io.helios.index.CandidateSet;
import io.helios.index.Hit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class Merge {
    private Merge() {
    }

    public static List<Hit> scores(List<String> terms, List<CandidateSet> sets, double k1, double b) {
        if (terms == null || terms.isEmpty()) {
            return List.of();
        }
        int n = 0;
        int sumLengths = 0;
        Map<String, Integer> df = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (CandidateSet set : sets) {
            n += set.passageCount();
            sumLengths += set.sumLengths();
            set.df().forEach((term, count) -> df.merge(term, count, Integer::sum));
            for (Candidate cand : set.candidates()) {
                String key = cand.passageId().toString();
                if (seen.add(key)) {
                    candidates.add(cand);
                }
            }
        }
        if (n == 0 || candidates.isEmpty()) {
            return List.of();
        }
        double avgdl = (double) sumLengths / n;
        if (k1 <= 0) {
            k1 = Bm25.DEFAULT_K1;
        }
        if (b < 0 || b > 1) {
            b = Bm25.DEFAULT_B;
        }
        List<Hit> hits = new ArrayList<>(candidates.size());
        for (Candidate cand : candidates) {
            hits.add(new Hit(cand.passageId(),
                    Bm25.score(terms, cand.tf(), cand.length(), avgdl, n, df, k1, b),
                    cand.matchedTerms()));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> h.passageId().toString()));
        return hits;
    }

    public static CandidateSet combinePartition(List<CandidateSet> sets, int rf) {
        if (sets.size() == 1) {
            return sets.getFirst();
        }
        int passageCount = 0;
        int sumLengths = 0;
        Map<String, Integer> df = new HashMap<>();
        List<Candidate> candidates = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        boolean useMax = rf > 2;
        for (CandidateSet set : sets) {
            if (useMax) {
                if (set.passageCount() > passageCount) {
                    passageCount = set.passageCount();
                    sumLengths = set.sumLengths();
                }
                for (var e : set.df().entrySet()) {
                    df.put(e.getKey(), Math.max(df.getOrDefault(e.getKey(), 0), e.getValue()));
                }
            } else {
                passageCount += set.passageCount();
                sumLengths += set.sumLengths();
                set.df().forEach((term, count) -> df.merge(term, count, Integer::sum));
            }
            for (Candidate cand : set.candidates()) {
                if (seen.add(cand.passageId().toString())) {
                    candidates.add(cand);
                }
            }
        }
        return new CandidateSet(passageCount, sumLengths, df, candidates);
    }
}
