package io.helios.eval;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.helios.config.ConfigLoader;
import io.helios.config.HeliosConfig;
import io.helios.index.InvertedIndex;
import io.helios.jobs.JobService;
import io.helios.repository.Store;
import io.helios.search.SearchQuery;
import io.helios.search.SearchResponse;
import io.helios.search.SearchResult;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import javax.sql.DataSource;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class EvalMain {
    public static void main(String[] args) throws Exception {
        String config = flag(args, "-config", "");
        String dataset = flag(args, "-dataset", "testdata/eval/queries.json");
        String sample = flag(args, "-sample", "testdata/repos/sample");
        HeliosConfig cfg = ConfigLoader.load(config);
        cfg.index().setSnapshotPath(System.getProperty("java.io.tmpdir") + "/helios-eval-" + UUID.randomUUID() + ".json");
        DataSource ds = io.helios.storage.DataSources.open(cfg);
        Store store = new Store(new NamedParameterJdbcTemplate(ds));
        JobService svc = new JobService(cfg, store, new InvertedIndex(cfg.tokenizerOptions(), cfg.index().bm25().k1(), cfg.index().bm25().b()));
        Quality.Dataset dsEval = Quality.load(Path.of(dataset));
        String source = "https://github.com/helios-eval/" + UUID.randomUUID();
        JobService.SubmitResult ingested = svc.ingestDirectory("helios-eval", "sample", source, Path.of(sample));
        try {
            Map<String, Quality.Metrics> modes = new LinkedHashMap<>();
            for (String mode : List.of("lexical", "semantic", "hybrid")) {
                List<Quality.Metrics> rows = new ArrayList<>();
                for (Quality.Query q : dsEval.queries) {
                    SearchResponse res = svc.search(new SearchQuery(q.text, null, "", "", 1, dsEval.k, mode, cfg.search().alpha(), false));
                    List<String> paths = res.results().stream().map(SearchResult::filePath).toList();
                    rows.add(Quality.score(paths, q.relevant, dsEval.k));
                }
                modes.put(mode, Quality.mean(rows));
            }
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("dataset", dsEval.dataset);
            out.put("k", dsEval.k);
            out.put("queries", dsEval.queries.size());
            out.put("embedder", "hashing-v1/" + cfg.search().embedDim());
            out.put("alpha", cfg.search().alpha());
            out.put("modes", modes);
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
