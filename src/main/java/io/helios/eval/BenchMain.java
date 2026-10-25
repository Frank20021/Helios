package io.helios.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import io.helios.index.InvertedIndex;
import io.helios.jobs.JobService;
import io.helios.repository.Store;
import io.helios.search.SearchQuery;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class BenchMain {
    public static void main(String[] args) throws Exception {
        String config = flag(args, "-config", "");
        String sample = flag(args, "-sample", "testdata/repos/sample");
        int n = Integer.parseInt(flag(args, "-n", "50"));
        HeliosConfig cfg = ConfigLoader.load(config);
        cfg.index().setSnapshotPath(System.getProperty("java.io.tmpdir") + "/helios-bench-" + UUID.randomUUID() + ".json");
        DataSource ds = io.helios.storage.DataSources.open(cfg);
        Store store = new Store(new NamedParameterJdbcTemplate(ds));
        JobService svc = new JobService(cfg, store, new InvertedIndex(cfg.tokenizerOptions(), cfg.index().bm25().k1(), cfg.index().bm25().b()));
        String source = "https://github.com/helios-bench/" + UUID.randomUUID();
        long started = System.nanoTime();
        JobService.SubmitResult ingested = svc.ingestDirectory("helios-bench", "sample", source, Path.of(sample));
        double indexSec = (System.nanoTime() - started) / 1_000_000_000.0;
        try {
            SearchQuery q = new SearchQuery("authentication retry", null, "", "", 1, 10, "lexical", 0, false);
            List<Double> lat = new ArrayList<>();
            started = System.nanoTime();
            for (int i = 0; i < n; i++) {
                long one = System.nanoTime();
                svc.search(q);
                lat.add((System.nanoTime() - one) / 1_000_000_000.0);
            }
            double elapsed = (System.nanoTime() - started) / 1_000_000_000.0;
            Collections.sort(lat);
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dataset", sample);
            out.put("passages", ingested.job().passagesCreated);
            out.put("index_seconds", indexSec);
            out.put("passages_per_second", ingested.job().passagesCreated / indexSec);
            out.put("searches", n);
            out.put("qps", n / elapsed);
            out.put("p50_ms", lat.get((int) (0.50 * (lat.size() - 1))) * 1000);
            out.put("p95_ms", lat.get((int) (0.95 * (lat.size() - 1))) * 1000);
            out.put("p99_ms", lat.get((int) (0.99 * (lat.size() - 1))) * 1000);
            out.put("cache", svc.cacheStats());
            out.put("cache_hit_rate", svc.metrics().cacheHitRate());
            new ObjectMapper().writerWithDefaultPrettyPrinter().writeValue(System.out, out);
            System.out.println();
        } finally {
            svc.deleteRepository(ingested.repo().id);
            svc.stop();
        }
    }

    private static String flag(String[] args, String name, String def) {
        for (int i = 0; i < args.length - 1; i++) {
            if (name.equals(args[i])) {
                return args[i + 1];
            }
        }
        return def;
    }
}
