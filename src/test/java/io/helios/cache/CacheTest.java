package io.helios.cache;

import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class CacheTest {
    @Test
    void memoryRoundTripAndPrefixDelete() {
        MemoryCache cache = new MemoryCache(Duration.ofSeconds(30));
        String key = CacheKeys.key(new QueryCache.QueryKey("Authentication Retry", "lexical", 0.5, false, "", "", "", 1, 10));
        assertTrue(key.startsWith(QueryCache.PREFIX));
        cache.set(key, "hello".getBytes(), Duration.ofSeconds(30));
        assertEquals("hello", new String(cache.get(key)));
        cache.deletePrefix(QueryCache.PREFIX);
        assertNull(cache.get(key));
        assertEquals("memory", cache.stats().backend());
    }

    @Test
    void keyIsStableForNormalizedQuery() {
        String a = CacheKeys.key(new QueryCache.QueryKey("  AUTH retry ", "LEXICAL", 0.5, false, "", "Go", "java", 0, 0));
        String b = CacheKeys.key(new QueryCache.QueryKey("auth retry", "lexical", 0.5, false, "", "go", ".java", 1, 10));
        assertEquals(a, b);
        assertNotNull(a);
    }

    private static void assertTrue(boolean v) {
        org.junit.jupiter.api.Assertions.assertTrue(v);
    }
}
