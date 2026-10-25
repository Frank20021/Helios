package io.helios.snippet;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SnippetTest {
    @Test
    void marksCamelCaseParts() {
        String got = Snippet.highlight("func retryAuthentication(req Request) error {", List.of("authentication", "retry"), 240);
        assertTrue(got.contains("<mark>retry</mark>"), got);
        assertTrue(got.contains("<mark>Authentication</mark>"), got);
    }

    @Test
    void windowsAroundMatch() {
        String prefix = "aaaa ".repeat(80);
        String got = Snippet.highlight(prefix + "authentication retry" + " bbbb".repeat(80), List.of("authentication"), 60);
        assertTrue(got.contains("<mark>authentication</mark>"), got);
        assertTrue(got.startsWith("…"));
        assertTrue(got.endsWith("…"));
    }

    @Test
    void empty() {
        assertEquals("", Snippet.highlight("   ", List.of("retry"), 40));
        assertEquals("no matches here", Snippet.highlight("no matches here", null, 40));
    }
}
