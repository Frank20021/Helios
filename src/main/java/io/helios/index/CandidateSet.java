package io.helios.index;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public record CandidateSet(int passageCount, int sumLengths, Map<String, Integer> df, List<Candidate> candidates) {
    public CandidateSet {
        df = df == null ? new LinkedHashMap<>() : new LinkedHashMap<>(df);
        candidates = candidates == null ? new ArrayList<>() : new ArrayList<>(candidates);
    }

    public static CandidateSet empty() {
        return new CandidateSet(0, 0, new LinkedHashMap<>(), new ArrayList<>());
    }
}
