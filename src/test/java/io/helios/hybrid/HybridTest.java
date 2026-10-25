package io.helios.hybrid;

import io.helios.index.Hit;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HybridTest {
    @Test
    void minMaxAndCombine() {
        UUID a = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID b = UUID.fromString("00000000-0000-0000-0000-000000000002");
        List<Hit> hits = Hybrid.combine(List.of(
                new Hybrid.Candidate(a, 10, 0.1, List.of("retry")),
                new Hybrid.Candidate(b, 0, 1.0, List.of("auth"))
        ), 0.5);
        assertEquals(2, hits.size());
        assertTrue(hits.getFirst().score() >= hits.get(1).score());
        assertEquals(1.0, Hybrid.minMax(new double[]{3, 3})[0]);
        assertEquals(0.0, Hybrid.minMax(new double[]{0, 0})[0]);
    }
}
