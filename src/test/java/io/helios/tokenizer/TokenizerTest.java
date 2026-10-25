package io.helios.tokenizer;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TokenizerTest {
    private static List<String> termsOf(List<Token> tokens) {
        return tokens.stream().map(Token::term).toList();
    }

    @Test
    void lowercaseAndPunctuation() {
        List<String> got = termsOf(Tokenizer.tokenize("Hello, World! Auth==OK", new TokenizerOptions(2, java.util.Map.of())));
        assertEquals(List.of("hello", "world", "auth", "ok"), got);
    }

    @Test
    void camelAndPascalCase() {
        TokenizerOptions opts = new TokenizerOptions(2, java.util.Map.of());
        assertEquals(List.of("retry", "authentication", "request", "retryauthenticationrequest"),
                termsOf(Tokenizer.tokenize("retryAuthenticationRequest", opts)));
        assertEquals(List.of("xml", "http", "request", "xmlhttprequest"),
                termsOf(Tokenizer.tokenize("XMLHttpRequest", opts)));
    }

    @Test
    void snakeAndKebabCase() {
        TokenizerOptions opts = new TokenizerOptions(2, java.util.Map.of());
        List<String> want = List.of("retry", "authentication", "request", "retryauthenticationrequest");
        assertEquals(want, termsOf(Tokenizer.tokenize("retry_authentication_request", opts)));
        assertEquals(want, termsOf(Tokenizer.tokenize("retry-authentication-request", opts)));
    }

    @Test
    void preservesIdentifierAndPositions() {
        List<Token> tokens = Tokenizer.tokenize("retryAuthenticationRequest() retryAuthenticationRequest",
                new TokenizerOptions(2, java.util.Map.of()));
        assertEquals(8, tokens.size());
        tokens.subList(0, 4).forEach(t -> assertEquals(0, t.position()));
        tokens.subList(4, 8).forEach(t -> assertEquals(1, t.position()));
    }

    @Test
    void stopWordsAndMinLength() {
        assertEquals(List.of("where", "authentication", "retry", "logic"),
                termsOf(Tokenizer.tokenize("Where is the authentication retry logic", TokenizerOptions.defaults())));
        assertEquals(List.of("retry"),
                termsOf(Tokenizer.tokenize("a I x retry", new TokenizerOptions(2, java.util.Map.of()))));
    }

    @Test
    void skipsOperatorsAndPureNumbers() {
        assertEquals(List.of("func", "add", "int", "return"),
                termsOf(Tokenizer.tokenize("func add(a, b int) { return a + b + 42 }", new TokenizerOptions(2, java.util.Map.of()))));
    }

    @Test
    void http2Client() {
        assertEquals(List.of("http2", "client", "http2client"),
                termsOf(Tokenizer.tokenize("http2Client", new TokenizerOptions(2, java.util.Map.of()))));
    }

    @Test
    void empty() {
        assertTrue(Tokenizer.tokenize("", TokenizerOptions.defaults()).isEmpty());
    }

    @Test
    void uniqueTerms() {
        assertEquals(List.of("retry"), Tokenizer.terms(Tokenizer.tokenize("retry retry", new TokenizerOptions(2, java.util.Map.of()))));
    }
}
