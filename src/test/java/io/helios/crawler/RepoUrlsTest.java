package io.helios.crawler;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RepoUrlsTest {
    @Test
    void acceptsGithubHttps() {
        RepoRef ref = RepoUrls.parseUrl("https://github.com/owner/repo.git", List.of("github.com"));
        assertEquals("owner", ref.owner());
        assertEquals("repo", ref.name());
        assertEquals("https://github.com/owner/repo", ref.sourceUrl());
    }

    @Test
    void rejectsInsecureAndCredentials() {
        assertThrows(CrawlerException.class, () -> RepoUrls.parseUrl("git@github.com:owner/repo.git", List.of("github.com")));
        assertThrows(CrawlerException.class, () -> RepoUrls.parseUrl("https://user:pass@github.com/owner/repo", List.of("github.com")));
        assertThrows(CrawlerException.class, () -> RepoUrls.parseUrl("https://evil.example/owner/repo", List.of("github.com")));
        assertTrue(RepoUrls.isValidation(new CrawlerException(RepoUrls.ERR_INSECURE)));
    }
}
