package io.helios.bm25;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Bm25Test {
    @Test
    void idfHandCalculation() {
        assertEquals(Math.log(1.6), Bm25.idf(3, 2), 1e-12);
    }

    @Test
    void termScoreHandCalculation() {
        double idf = Math.log(1.6);
        double d1 = Bm25.termScore(1, 2, 2, idf, Bm25.DEFAULT_K1, Bm25.DEFAULT_B);
        assertEquals(idf, d1, 1e-12);
        double d2 = Bm25.termScore(1, 1, 2, idf, Bm25.DEFAULT_K1, Bm25.DEFAULT_B);
        assertEquals(idf * 2.2 / 1.75, d2, 1e-12);
        assertTrue(d2 > d1);
        assertEquals(0, Bm25.termScore(0, 3, 2, idf, Bm25.DEFAULT_K1, Bm25.DEFAULT_B));
    }

    @Test
    void scoreSumsUniqueQueryTerms() {
        Map<String, Integer> df = Map.of("authentication", 2, "retry", 2);
        Map<String, Integer> tf = Map.of("authentication", 1, "retry", 1);
        double single = Bm25.score(List.of("authentication"), tf, 2, 2, 3, df, Bm25.DEFAULT_K1, Bm25.DEFAULT_B);
        double both = Bm25.score(List.of("authentication", "retry"), tf, 2, 2, 3, df, Bm25.DEFAULT_K1, Bm25.DEFAULT_B);
        double dup = Bm25.score(List.of("authentication", "authentication"), tf, 2, 2, 3, df, Bm25.DEFAULT_K1, Bm25.DEFAULT_B);
        assertTrue(both > single);
        assertEquals(single, dup, 1e-12);
    }

    @Test
    void emptyIndex() {
        assertEquals(0, Bm25.score(List.of("retry"), Map.of("retry", 1), 1, 1, 0, null, Bm25.DEFAULT_K1, Bm25.DEFAULT_B));
    }
}
