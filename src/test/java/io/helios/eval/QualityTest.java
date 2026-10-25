package io.helios.eval;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class QualityTest {
    @Test
    void scoreKnownRanking() {
        List<String> relevant = List.of("internal/auth/retry.go", "notes.txt");
        Quality.Metrics perfect = Quality.score(List.of("internal/auth/retry.go", "notes.txt", "other.go"), relevant, 2);
        assertEquals(1, perfect.precisionAtK());
        assertEquals(1, perfect.recallAtK());
        assertEquals(1, perfect.mrr());
        assertEquals(1, perfect.ndcgAtK(), 1e-12);
        Quality.Metrics miss = Quality.score(List.of("other.go", "vendor.go"), relevant, 2);
        assertEquals(0, miss.precisionAtK());
        Quality.Metrics late = Quality.score(List.of("other.go", "internal/auth/retry.go"), relevant, 2);
        assertEquals(0.5, late.mrr());
    }

    @Test
    void loadCommittedDataset() throws Exception {
        Quality.Dataset ds = Quality.load(Path.of("testdata/eval/queries.json"));
        assertEquals(5, ds.k);
        assertFalse(ds.queries.isEmpty());
        ds.queries.forEach(q -> {
            assertFalse(q.text.isBlank());
            assertFalse(q.relevant.isEmpty());
        });
    }
}
