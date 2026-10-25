package io.helios.embed;

import io.helios.tokenizer.TokenizerOptions;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EmbedderTest {
    @Test
    void encodeDecodeAndSelfSimilarity() {
        Embedder e = new Embedder(TokenizerOptions.defaults(), 32);
        float[] v = e.embed("retry authentication");
        assertEquals(32, v.length);
        float[] again = Embedder.decode(Embedder.encode(v));
        assertEquals(v.length, again.length);
        assertTrue(Embedder.dot(v, v) > 0.99);
        assertEquals("hashing-v1/32", e.modelId());
    }
}
