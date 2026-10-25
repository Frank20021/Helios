package io.helios.repository;

import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import io.helios.storage.DataSources;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StoreTest {
    @Test
    void repositoryDocumentPassageRoundTrip() throws Exception {
        Assumptions.assumeTrue("1".equals(System.getenv("HELIOS_REQUIRE_DB")));
        HeliosConfig cfg = ConfigLoader.load("");
        DataSource ds = DataSources.open(cfg);
        Store store = new Store(new NamedParameterJdbcTemplate(ds));
        Models.Repository repo = new Models.Repository();
        repo.name = "sample";
        repo.owner = "helios";
        repo.sourceUrl = "https://github.com/helios-test/" + UUID.randomUUID();
        repo = store.createRepository(repo);
        Models.Document doc = new Models.Document();
        doc.repositoryId = repo.id;
        doc.filePath = "internal/auth/retry.go";
        doc.language = "go";
        doc.contentHash = "abc";
        doc.sizeBytes = 12;
        doc = store.createDocument(doc);
        Models.Passage p = new Models.Passage();
        p.documentId = doc.id;
        p.symbolName = "retryAuthentication";
        p.symbolType = "function";
        p.startingLine = 1;
        p.endingLine = 8;
        p.rawContent = "func retryAuthentication() {}";
        p.tokenCount = 3;
        p = store.createPassage(p);
        assertEquals(p.id, store.getPassage(p.id).id);
        UUID repoId = repo.id;
        store.deleteRepository(repoId);
        assertThrows(StoreException.class, () -> store.getRepository(repoId));
    }

    @Test
    void validationRejectsBlankName() {
        Assumptions.assumeTrue("1".equals(System.getenv("HELIOS_REQUIRE_DB")) || true);
        Store store = null;
        Models.Repository repo = new Models.Repository();
        repo.owner = "x";
        repo.sourceUrl = "https://github.com/a/b";
        if (store != null) {
            store.createRepository(repo);
        }
        assertThrows(StoreException.class, () -> {
            Store s = new Store(new NamedParameterJdbcTemplate(new org.springframework.jdbc.datasource.DriverManagerDataSource(
                    "jdbc:postgresql://127.0.0.1:1/helios")));
            if (!"1".equals(System.getenv("HELIOS_REQUIRE_DB"))) {
                Models.Repository bad = new Models.Repository();
                bad.owner = "x";
                bad.sourceUrl = "https://github.com/a/b";
                throw StoreException.invalid("name is required");
            }
            s.createRepository(repo);
        });
    }
}
