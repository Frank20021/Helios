package io.helios.telemetry;

import java.io.StringWriter;
import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

public final class Metrics {
    private static final double[] SEARCH_BUCKETS = {0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5, 1, 2.5};

    private final ConcurrentHashMap<String, AtomicLong> searchTotal = new ConcurrentHashMap<>();
    private final AtomicLong searchErrors = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> indexJobs = new ConcurrentHashMap<>();
    private final AtomicLong indexPassages = new AtomicLong();
    private final ConcurrentHashMap<String, AtomicLong> httpTotal = new ConcurrentHashMap<>();
    private final AtomicLong httpErrors = new AtomicLong();

    private final List<Double> searchDur = new ArrayList<>();
    private final List<Double> indexDur = new ArrayList<>();
    private final Map<Double, Integer> searchHist = new HashMap<>();
    private double searchSum;
    private int searchN;
    private double indexSum;
    private int indexN;
    private long cacheHits;
    private long cacheMiss;
    private int cacheEnt;
    private final Map<String, Integer> shards = new HashMap<>();
    private int passages;

    public synchronized void observeSearch(String mode, String result, double seconds, boolean cacheHit) {
        if (mode == null || mode.isBlank()) {
            mode = "lexical";
        }
        if (result == null || result.isBlank()) {
            result = "ok";
        }
        searchTotal.computeIfAbsent(mode + "|" + result, k -> new AtomicLong()).incrementAndGet();
        if (!"ok".equals(result)) {
            searchErrors.incrementAndGet();
        }
        searchDur.add(seconds);
        if (searchDur.size() > 1024) {
            searchDur.subList(0, searchDur.size() - 1024).clear();
        }
        searchSum += seconds;
        searchN++;
        for (double b : SEARCH_BUCKETS) {
            if (seconds <= b) {
                searchHist.merge(b, 1, Integer::sum);
            }
        }
        if (cacheHit) {
            cacheHits++;
        } else {
            cacheMiss++;
        }
    }

    public synchronized void observeIndex(String result, int passagesCreated, double seconds) {
        if (result == null || result.isBlank()) {
            result = "ok";
        }
        indexJobs.computeIfAbsent(result, k -> new AtomicLong()).incrementAndGet();
        if (passagesCreated > 0) {
            indexPassages.addAndGet(passagesCreated);
        }
        indexDur.add(seconds);
        if (indexDur.size() > 256) {
            indexDur.subList(0, indexDur.size() - 256).clear();
        }
        indexSum += seconds;
        indexN++;
    }

    public void observeHttp(int code) {
        httpTotal.computeIfAbsent(Integer.toString(code), k -> new AtomicLong()).incrementAndGet();
        if (code >= 500) {
            httpErrors.incrementAndGet();
        }
    }

    public synchronized void setCacheEntries(int n) {
        cacheEnt = n;
    }

    public synchronized void setPassages(int n) {
        passages = n;
    }

    public synchronized void setShard(String id, boolean up) {
        shards.put(id, up ? 1 : 0);
    }

    public synchronized double searchQuantile(double q) {
        return quantile(searchDur, q);
    }

    public synchronized double cacheHitRate() {
        long total = cacheHits + cacheMiss;
        return total == 0 ? 0 : (double) cacheHits / total;
    }

    public synchronized String prometheus() {
        StringWriter w = new StringWriter();
        double p50 = quantile(searchDur, 0.50);
        double p95 = quantile(searchDur, 0.95);
        double p99 = quantile(searchDur, 0.99);
        w.write("# HELP helios_up 1 if the process is running\n# TYPE helios_up gauge\nhelios_up 1\n");
        w.write("# HELP helios_index_passages Number of passages in the live index\n# TYPE helios_index_passages gauge\nhelios_index_passages " + passages + "\n");
        w.write("# HELP helios_search_requests_total Search requests by mode and result\n# TYPE helios_search_requests_total counter\n");
        searchTotal.forEach((key, val) -> {
            String[] parts = key.split("\\|", 2);
            String mode = parts.length == 2 ? parts[0] : "lexical";
            String result = parts.length == 2 ? parts[1] : "ok";
            w.write("helios_search_requests_total{mode=\"" + mode + "\",result=\"" + result + "\"} " + val.get() + "\n");
        });
        w.write("# HELP helios_search_errors_total Failed search requests\n# TYPE helios_search_errors_total counter\nhelios_search_errors_total " + searchErrors.get() + "\n");
        w.write("# HELP helios_search_duration_seconds Search latency\n# TYPE helios_search_duration_seconds histogram\n");
        for (double b : SEARCH_BUCKETS) {
            w.write("helios_search_duration_seconds_bucket{le=\"" + trim(b) + "\"} " + searchHist.getOrDefault(b, 0) + "\n");
        }
        w.write("helios_search_duration_seconds_bucket{le=\"+Inf\"} " + searchN + "\n");
        w.write("helios_search_duration_seconds_sum " + searchSum + "\n");
        w.write("helios_search_duration_seconds_count " + searchN + "\n");
        w.write("# HELP helios_search_latency_p50_seconds\n# TYPE helios_search_latency_p50_seconds gauge\nhelios_search_latency_p50_seconds " + p50 + "\n");
        w.write("# HELP helios_search_latency_p95_seconds\n# TYPE helios_search_latency_p95_seconds gauge\nhelios_search_latency_p95_seconds " + p95 + "\n");
        w.write("# HELP helios_search_latency_p99_seconds\n# TYPE helios_search_latency_p99_seconds gauge\nhelios_search_latency_p99_seconds " + p99 + "\n");
        w.write("# HELP helios_cache_hits_total\n# TYPE helios_cache_hits_total counter\nhelios_cache_hits_total " + cacheHits + "\n");
        w.write("# HELP helios_cache_misses_total\n# TYPE helios_cache_misses_total counter\nhelios_cache_misses_total " + cacheMiss + "\n");
        w.write("# HELP helios_cache_entries\n# TYPE helios_cache_entries gauge\nhelios_cache_entries " + cacheEnt + "\n");
        w.write("# HELP helios_index_jobs_total\n# TYPE helios_index_jobs_total counter\n");
        indexJobs.forEach((key, val) -> w.write("helios_index_jobs_total{result=\"" + key + "\"} " + val.get() + "\n"));
        w.write("# HELP helios_index_passages_created_total\n# TYPE helios_index_passages_created_total counter\nhelios_index_passages_created_total " + indexPassages.get() + "\n");
        w.write("# HELP helios_index_duration_seconds_sum\n# TYPE helios_index_duration_seconds_sum counter\nhelios_index_duration_seconds_sum " + indexSum + "\n");
        w.write("# HELP helios_index_duration_seconds_count\n# TYPE helios_index_duration_seconds_count counter\nhelios_index_duration_seconds_count " + indexN + "\n");
        w.write("# HELP helios_http_requests_total\n# TYPE helios_http_requests_total counter\n");
        httpTotal.forEach((key, val) -> w.write("helios_http_requests_total{code=\"" + key + "\"} " + val.get() + "\n"));
        w.write("# HELP helios_http_errors_total\n# TYPE helios_http_errors_total counter\nhelios_http_errors_total " + httpErrors.get() + "\n");
        w.write("# HELP helios_shard_up\n# TYPE helios_shard_up gauge\n");
        if (shards.isEmpty()) {
            w.write("helios_shard_up{shard_id=\"local\"} 1\n");
        } else {
            for (var e : new TreeMap<>(shards).entrySet()) {
                w.write("helios_shard_up{shard_id=\"" + e.getKey() + "\"} " + e.getValue() + "\n");
            }
        }
        long mem = Runtime.getRuntime().totalMemory();
        w.write("# HELP helios_process_resident_memory_bytes\n# TYPE helios_process_resident_memory_bytes gauge\nhelios_process_resident_memory_bytes " + mem + "\n");
        w.write("# HELP helios_go_goroutines\n# TYPE helios_go_goroutines gauge\nhelios_go_goroutines " + Thread.activeCount() + "\n");
        double cpu = ManagementFactory.getRuntimeMXBean().getUptime() / 1000.0;
        w.write("# HELP helios_process_cpu_seconds_total\n# TYPE helios_process_cpu_seconds_total counter\nhelios_process_cpu_seconds_total " + cpu + "\n");
        return w.toString();
    }

    private static double quantile(List<Double> samples, double q) {
        if (samples.isEmpty()) {
            return 0;
        }
        double[] cp = samples.stream().mapToDouble(Double::doubleValue).toArray();
        Arrays.sort(cp);
        if (q <= 0) {
            return cp[0];
        }
        if (q >= 1) {
            return cp[cp.length - 1];
        }
        return cp[(int) (q * (cp.length - 1))];
    }

    private static String trim(double b) {
        String s = Double.toString(b);
        if (s.endsWith(".0")) {
            return s.substring(0, s.length() - 2);
        }
        return s;
    }
}
