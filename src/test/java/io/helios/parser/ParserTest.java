package io.helios.parser;

import io.helios.tokenizer.TokenizerOptions;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ParserTest {
    @Test
    void parsesGoFunction() {
        String src = "package auth\n\nfunc retryAuthentication(req Request) error {\n\treturn nil\n}\n";
        List<Passage> passages = Parser.parse("internal/auth/retry.go", src, TokenizerOptions.defaults());
        assertTrue(passages.stream().anyMatch(p -> "retryAuthentication".equals(p.symbolName())));
        assertEquals("go", Languages.fromPath("internal/auth/retry.go"));
    }

    @Test
    void parsesMarkdownHeading() {
        List<Passage> passages = Parser.parse("README.md", "# Auth\n\nretry notes\n", TokenizerOptions.defaults());
        assertTrue(passages.stream().anyMatch(p -> "Auth".equals(p.symbolName())));
    }
}
