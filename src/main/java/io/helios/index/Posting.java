package io.helios.index;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record Posting(UUID passageId, int termFrequency, List<Integer> positions) {
    public Posting {
        positions = positions == null ? List.of() : List.copyOf(positions);
    }

    public Posting copy() {
        return new Posting(passageId, termFrequency, new ArrayList<>(positions));
    }
}
