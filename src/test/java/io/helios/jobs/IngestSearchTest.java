package io.helios.jobs;

import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import io.helios.index.InvertedIndex;
import io.helios.repository.Store;
import io.helios.search.SearchQuery;
import io.helios.search.SearchResponse;
import io.helios.storage.DataSources;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IngestSearchTest {
    @Test
    void ingestSampleAndSearchAuthenticationRetry() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("HELIOS_REQUIRE_DB")));
        HeliosConfig cfg = ConfigLoader.load("");
        cfg.index().setSnapshotPath(System.getProperty("java.io.tmpdir") + "/helios-it-" + UUID.randomUUID() + ".json");
        Store store = new Store(new NamedParameterJdbcTemplate(DataSources.open(cfg)));
        JobService svc = new JobService(cfg, store, new InvertedIndex(cfg.tokenizerOptions(), cfg.index().bm25().k1(), cfg.index().bm25().b()));
        String source = "https://github.com/helios-it/" + UUID.randomUUID();
        JobService.SubmitResult ingested = svc.ingestDirectory("helios-it", "sample", source, Path.of("testdata/repos/sample"));
        try {
            SearchResponse res = svc.search(new SearchQuery("authentication retry", null, "", "", 1, 10, "lexical", 0, false));
            assertFalse(res.results().isEmpty());
            assertTrue(res.results().stream().anyMatch(h -> h.filePath().contains("retry")));
            SearchResponse hybrid = svc.search(new SearchQuery("authentication retry", null, "", "", 1, 10, "hybrid", 0.5, true));
            assertFalse(hybrid.results().isEmpty());
        } finally {
            svc.deleteRepository(ingested.repo().id);
            svc.stop();
        }
    }
}
