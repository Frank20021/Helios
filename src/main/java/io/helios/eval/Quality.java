package io.helios.eval;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class Quality {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private Quality() {
    }

    public static class Query {
        public String id = "";
        public String text = "";
        public List<String> relevant = List.of();
    }

    public static class Dataset {
        public String dataset = "";
        public int k = 5;
        public List<Query> queries = List.of();
    }

    public record Metrics(
            @JsonProperty("precision_at_k") double precisionAtK,
            @JsonProperty("recall_at_k") double recallAtK,
            double mrr,
            @JsonProperty("ndcg_at_k") double ndcgAtK
    ) {
    }

    public static Dataset load(Path path) throws Exception {
        Dataset ds = MAPPER.readValue(path.toFile(), Dataset.class);
        if (ds.k < 1) {
            ds.k = 5;
        }
        return ds;
    }

    public static Metrics score(List<String> ranked, List<String> relevant, int k) {
        if (k < 1) {
            k = 1;
        }
        Map<String, Boolean> want = new LinkedHashMap<>();
        for (String path : relevant) {
            if (path != null && !path.isBlank()) {
                want.put(path, true);
            }
        }
        if (want.isEmpty()) {
            return new Metrics(0, 0, 0, 0);
        }
        List<String> cut = ranked.size() > k ? ranked.subList(0, k) : ranked;
        int hits = 0;
        int first = 0;
        Map<String, Boolean> seen = new LinkedHashMap<>();
        double dcg = 0;
        for (int i = 0; i < cut.size(); i++) {
            String path = cut.get(i);
            if (!want.containsKey(path) || seen.containsKey(path)) {
                continue;
            }
            seen.put(path, true);
            hits++;
            if (first == 0) {
                first = i + 1;
            }
            dcg += 1 / (Math.log(i + 2) / Math.log(2));
        }
        double idcg = 0;
        int ideal = Math.min(want.size(), k);
        for (int i = 0; i < ideal; i++) {
            idcg += 1 / (Math.log(i + 2) / Math.log(2));
        }
        double mrr = first > 0 ? 1.0 / first : 0;
        double ndcg = idcg > 0 ? dcg / idcg : 0;
        return new Metrics((double) hits / k, (double) hits / want.size(), mrr, ndcg);
    }

    public static Metrics mean(List<Metrics> items) {
        if (items.isEmpty()) {
            return new Metrics(0, 0, 0, 0);
        }
        double p = 0, r = 0, m = 0, n = 0;
        for (Metrics item : items) {
            p += item.precisionAtK();
            r += item.recallAtK();
            m += item.mrr();
            n += item.ndcgAtK();
        }
        double d = items.size();
        return new Metrics(p / d, r / d, m / d, n / d);
    }
}
