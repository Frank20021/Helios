package io.helios.index;

import io.helios.tokenizer.TokenizerOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InvertedIndexTest {
    @Test
    void ranksRetryAuthenticationAboveUnrelated() {
        InvertedIndex idx = new InvertedIndex(new TokenizerOptions(2, java.util.Map.of()), 1.2, 0.75);
        UUID auth = UUID.randomUUID();
        UUID other = UUID.randomUUID();
        idx.add(auth, "func retryAuthentication(req Request) error { return retry(req) }");
        idx.add(other, "package notes\nthis file mentions nothing useful");
        List<Hit> hits = idx.search("authentication retry", 10);
        assertTrue(hits.size() >= 1);
        assertEquals(auth, hits.getFirst().passageId());
        assertTrue(hits.getFirst().score() > 0);
    }

    @Test
    void replaceIsIdempotent() {
        InvertedIndex idx = new InvertedIndex(TokenizerOptions.defaults(), 1.2, 0.75);
        UUID id = UUID.randomUUID();
        idx.add(id, "retry authentication");
        idx.add(id, "retry authentication");
        assertEquals(1, idx.stats().passageCount());
        assertEquals(1, idx.documentFrequency("retry"));
    }

    @Test
    void snapshotRoundTrip(@TempDir Path dir) throws Exception {
        InvertedIndex idx = new InvertedIndex(TokenizerOptions.defaults(), 1.2, 0.75);
        UUID id = UUID.randomUUID();
        idx.add(id, "retryAuthenticationRequest");
        Path path = dir.resolve("index.json");
        IndexSnapshot.save(idx, path.toString());
        InvertedIndex loaded = new InvertedIndex(TokenizerOptions.defaults(), 1.2, 0.75);
        IndexSnapshot.load(loaded, path.toString());
        assertTrue(loaded.matchesPassageSet(List.of(id)));
        assertEquals(idx.search("retry", 5).getFirst().passageId(), loaded.search("retry", 5).getFirst().passageId());
    }
}
