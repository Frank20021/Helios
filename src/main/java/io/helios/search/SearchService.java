package io.helios.search;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.helios.cache.CacheKeys;
import io.helios.cache.QueryCache;
import io.helios.embed.Embedder;
import io.helios.hybrid.Hybrid;
import io.helios.index.Hit;
import io.helios.jobs.Indexer;
import io.helios.repository.Models;
import io.helios.repository.Store;
import io.helios.snippet.Snippet;
import io.helios.telemetry.Metrics;
import io.helios.tokenizer.Tokenizer;
import io.helios.tokenizer.TokenizerOptions;
import io.helios.tracing.Otlp;
import io.helios.tracing.Recorder;
import io.helios.tracing.Span;
import io.helios.vector.VectorIndex;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class SearchService {
    public static final String MODE_LEXICAL = "lexical";
    public static final String MODE_SEMANTIC = "semantic";
    public static final String MODE_HYBRID = "hybrid";
    public static final int DEFAULT_PAGE_SIZE = 10;
    public static final int MAX_PAGE_SIZE = 50;
    public static final int MAX_CANDIDATES = 1000;

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final Store store;
    private Indexer hits;
    private final Embedder embed;
    private final VectorIndex vectors;
    private final TokenizerOptions tok;
    private final String defaultMode;
    private final double alpha;
    private final int semLimit;
    private QueryCache cache;
    private final Duration cacheTtl;
    private final Metrics metrics;
    private final Recorder traces;
    private Otlp.Exporter exporter;

    public SearchService(Store store, Indexer hits, Embedder embed, VectorIndex vectors, TokenizerOptions tok,
                         String defaultMode, double alpha, int semLimit, QueryCache cache, Duration cacheTtl,
                         Metrics metrics, Recorder traces) {
        this.store = store;
        this.hits = hits;
        this.embed = embed;
        this.vectors = vectors;
        this.tok = tok;
        this.defaultMode = defaultMode;
        this.alpha = alpha;
        this.semLimit = semLimit;
        this.cache = cache;
        this.cacheTtl = cacheTtl;
        this.metrics = metrics;
        this.traces = traces;
    }

    public void setHits(Indexer hits) {
        this.hits = hits;
    }

    public void setCache(QueryCache cache) {
        this.cache = cache;
    }

    public void setExporter(Otlp.Exporter exporter) {
        this.exporter = exporter;
    }

    public SearchResponse search(SearchQuery q) {
        long started = System.nanoTime();
        q = normalize(q);
        if (q.text() == null || q.text().isBlank()) {
            throw new IllegalArgumentException("query is required");
        }
        String mode = q.mode() == null || q.mode().isBlank() ? defaultMode : q.mode();
        if (mode == null || mode.isBlank()) {
            mode = MODE_LEXICAL;
        }
        if (!validMode(mode)) {
            throw new IllegalArgumentException("invalid search mode");
        }
        double useAlpha = q.hasAlpha() ? q.alpha() : alpha;
        if (useAlpha < 0 || useAlpha > 1) {
            throw new IllegalArgumentException("alpha must be between 0 and 1");
        }
        Span root = traces == null ? null : traces.start("helios.search", Map.of("query", q.text(), "mode", mode));
        try {
            String key = CacheKeys.key(new QueryCache.QueryKey(
                    q.text(), mode, useAlpha, q.hasAlpha() || alpha != useAlpha,
                    q.repositoryId() == null ? "" : q.repositoryId().toString(),
                    q.language(), q.fileExtension(), q.page(), q.pageSize()
            ));
            if (cache != null) {
                if (traces != null && root != null) {
                    traces.child(root, "cache.lookup", Map.of("key", key));
                }
                byte[] raw = cache.get(key);
                if (raw != null) {
                    try {
                        SearchResponse cached = MAPPER.readValue(raw, SearchResponse.class);
                        cached = cached.withCached(true, millis(started));
                        if (metrics != null) {
                            metrics.observeSearch(mode, "ok", seconds(started), true);
                            metrics.setCacheEntries(cache.stats().entries());
                        }
                        return cached;
                    } catch (Exception ignored) {
                    }
                }
            }
            List<Hit> ranked = rank(q.text(), mode, useAlpha);
            List<UUID> ids = ranked.stream().map(Hit::passageId).toList();
            Map<UUID, Hit> score = new LinkedHashMap<>();
            for (Hit hit : ranked) {
                score.put(hit.passageId(), hit);
            }
            List<Models.PassageView> views = store.getPassageViews(ids);
            Map<UUID, Models.PassageView> byId = new LinkedHashMap<>();
            for (Models.PassageView view : views) {
                byId.put(view.passage.id, view);
            }
            List<SearchResult> filtered = new ArrayList<>();
            for (Hit hit : ranked) {
                Models.PassageView view = byId.get(hit.passageId());
                if (view == null) {
                    continue;
                }
                if (q.repositoryId() != null && !q.repositoryId().equals(view.repositoryId)) {
                    continue;
                }
                if (q.language() != null && !q.language().isBlank() && !q.language().equalsIgnoreCase(view.language)) {
                    continue;
                }
                if (q.fileExtension() != null && !q.fileExtension().isBlank()) {
                    String ext = extension(view.filePath);
                    if (!q.fileExtension().equals(ext)) {
                        continue;
                    }
                }
                filtered.add(new SearchResult(
                        view.repositoryName, view.filePath, view.language, view.passage.symbolName,
                        view.passage.startingLine, view.passage.endingLine,
                        Snippet.highlight(view.passage.rawContent, hit.matchedTerms(), 240),
                        hit.score(), hit.matchedTerms()
                ));
            }
            List<SearchResult> page = pageSlice(filtered, q.page(), q.pageSize());
            SearchResponse res = new SearchResponse(q.text(), mode, useAlpha, filtered.size(), millis(started),
                    q.page(), q.pageSize(), false, page);
            if (cache != null) {
                try {
                    cache.set(key, MAPPER.writeValueAsBytes(res), cacheTtl);
                } catch (Exception ignored) {
                }
            }
            if (metrics != null) {
                metrics.observeSearch(mode, "ok", seconds(started), false);
                if (cache != null) {
                    metrics.setCacheEntries(cache.stats().entries());
                }
            }
            return res;
        } catch (RuntimeException e) {
            if (metrics != null && !"query is required".equals(e.getMessage())
                    && !e.getMessage().contains("invalid search mode")
                    && !e.getMessage().contains("alpha must be between")) {
                metrics.observeSearch(mode, "error", seconds(started), false);
            }
            throw e;
        } finally {
            if (traces != null && root != null) {
                Span finished = traces.finish(root);
                if (exporter != null) {
                    exporter.export(finished);
                }
            }
        }
    }

    private List<Hit> rank(String text, String mode, double alpha) {
        return switch (mode) {
            case MODE_LEXICAL -> lexical(text);
            case MODE_SEMANTIC -> semantic(text);
            case MODE_HYBRID -> hybrid(text, alpha);
            default -> throw new IllegalArgumentException("invalid search mode");
        };
    }

    private List<Hit> lexical(String text) {
        if (hits != null) {
            return hits.searchHits(text, MAX_CANDIDATES);
        }
        return List.of();
    }

    private List<Hit> semantic(String text) {
        if (embed == null || vectors == null) {
            return List.of();
        }
        int limit = semLimit < 1 ? MAX_CANDIDATES : semLimit;
        List<String> terms = uniqueTerms(text, tok);
        List<Hit> hits = new ArrayList<>();
        for (VectorIndex.Hit hit : vectors.search(embed.embed(text), limit)) {
            hits.add(new Hit(hit.passageId(), hit.score(), terms));
        }
        return hits;
    }

    private List<Hit> hybrid(String text, double alpha) {
        List<Hit> lex = lexical(text);
        List<Hit> sem = semantic(text);
        Map<UUID, Hybrid.Candidate> byId = new LinkedHashMap<>();
        for (Hit hit : lex) {
            Hybrid.Candidate c = byId.computeIfAbsent(hit.passageId(),
                    id -> new Hybrid.Candidate(id, 0, 0, new ArrayList<>()));
            List<String> terms = new ArrayList<>(c.matchedTerms());
            mergeTerms(terms, hit.matchedTerms());
            byId.put(hit.passageId(), new Hybrid.Candidate(hit.passageId(), hit.score(), c.vector(), terms));
        }
        for (Hit hit : sem) {
            Hybrid.Candidate c = byId.computeIfAbsent(hit.passageId(),
                    id -> new Hybrid.Candidate(id, 0, 0, new ArrayList<>()));
            List<String> terms = new ArrayList<>(c.matchedTerms());
            mergeTerms(terms, hit.matchedTerms());
            byId.put(hit.passageId(), new Hybrid.Candidate(hit.passageId(), c.bm25(), hit.score(), terms));
        }
        return Hybrid.combine(new ArrayList<>(byId.values()), alpha);
    }

    private static List<String> uniqueTerms(String text, TokenizerOptions tok) {
        return Tokenizer.terms(Tokenizer.tokenize(text, tok));
    }

    private static void mergeTerms(List<String> a, List<String> b) {
        Set<String> seen = new LinkedHashSet<>(a);
        for (String term : b) {
            if (seen.add(term)) {
                a.add(term);
            }
        }
    }

    private static boolean validMode(String mode) {
        return MODE_LEXICAL.equals(mode) || MODE_SEMANTIC.equals(mode) || MODE_HYBRID.equals(mode);
    }

    private static SearchQuery normalize(SearchQuery q) {
        String text = q.text() == null ? "" : q.text().trim();
        String lang = q.language() == null ? "" : q.language().trim().toLowerCase(Locale.ROOT);
        String ext = q.fileExtension() == null ? "" : q.fileExtension().trim().toLowerCase(Locale.ROOT);
        if (!ext.isEmpty() && !ext.startsWith(".")) {
            ext = "." + ext;
        }
        String mode = q.mode() == null ? "" : q.mode().trim().toLowerCase(Locale.ROOT);
        int page = q.page() < 1 ? 1 : q.page();
        int size = q.pageSize() < 1 ? DEFAULT_PAGE_SIZE : Math.min(q.pageSize(), MAX_PAGE_SIZE);
        return new SearchQuery(text, q.repositoryId(), lang, ext, page, size, mode, q.alpha(), q.hasAlpha());
    }

    private static List<SearchResult> pageSlice(List<SearchResult> in, int page, int size) {
        int start = (page - 1) * size;
        if (start >= in.size()) {
            return List.of();
        }
        return in.subList(start, Math.min(start + size, in.size()));
    }

    private static String extension(String path) {
        String name = Paths.get(path).getFileName().toString();
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot);
    }

    private static double millis(long started) {
        return (System.nanoTime() - started) / 1_000_000.0;
    }

    private static double seconds(long started) {
        return (System.nanoTime() - started) / 1_000_000_000.0;
    }
}
