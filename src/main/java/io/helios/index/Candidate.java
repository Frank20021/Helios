package io.helios.index;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public record Candidate(UUID passageId, int length, Map<String, Integer> tf, List<String> matchedTerms) {
    public Candidate {
        tf = tf == null ? Map.of() : Map.copyOf(new LinkedHashMap<>(tf));
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }
}
