package io.helios.index;

import java.util.List;
import java.util.UUID;

public record Hit(UUID passageId, double score, List<String> matchedTerms) {
    public Hit {
        matchedTerms = matchedTerms == null ? List.of() : List.copyOf(matchedTerms);
    }
}
