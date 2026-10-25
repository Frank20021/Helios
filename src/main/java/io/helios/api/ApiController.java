package io.helios.api;

import io.helios.crawler.CrawlerException;
import io.helios.jobs.JobService;
import io.helios.repository.Models;
import io.helios.repository.StoreException;
import io.helios.search.SearchQuery;
import io.helios.search.SearchResponse;
import io.helios.tracing.Otlp;
import io.helios.tracing.Span;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
public class ApiController {
    private final JobService svc;

    public ApiController(JobService svc) {
        this.svc = svc;
    }

    @PostMapping("/api/v1/repositories")
    public ResponseEntity<Map<String, String>> create(@RequestBody Map<String, String> body) {
        String url = body == null ? "" : body.getOrDefault("url", "");
        JobService.SubmitResult out = svc.submitRepo(url);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "repository_id", out.repo().id.toString(),
                "job_id", out.job().id.toString(),
                "status", out.job().status
        ));
    }

    @GetMapping("/api/v1/repositories")
    public List<Map<String, Object>> list() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Models.Repository repo : svc.listRepositories()) {
            out.add(repoJson(repo, null));
        }
        return out;
    }

    @GetMapping("/api/v1/repositories/{repositoryId}")
    public Map<String, Object> get(@PathVariable String repositoryId) {
        Models.Repository repo = svc.getRepository(parseId(repositoryId, "invalid repository id"));
        Models.Job job = null;
        try {
            job = svc.latestJob(repo.id);
        } catch (StoreException ignored) {
        }
        return repoJson(repo, job);
    }

    @PostMapping("/api/v1/repositories/{repositoryId}/reindex")
    public ResponseEntity<Map<String, String>> reindex(@PathVariable String repositoryId) {
        UUID id = parseId(repositoryId, "invalid repository id");
        Models.Job job = svc.reindex(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(Map.of(
                "repository_id", id.toString(),
                "job_id", job.id.toString(),
                "status", job.status
        ));
    }

    @DeleteMapping("/api/v1/repositories/{repositoryId}")
    public ResponseEntity<Void> delete(@PathVariable String repositoryId) {
        svc.deleteRepository(parseId(repositoryId, "invalid repository id"));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/api/v1/jobs/{jobId}")
    public Map<String, Object> job(@PathVariable String jobId) {
        return jobJson(svc.getJob(parseId(jobId, "invalid job id")));
    }

    @GetMapping("/api/v1/search")
    public SearchResponse search(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "language", required = false) String language,
            @RequestParam(name = "file_extension", required = false) String fileExtension,
            @RequestParam(name = "repository_id", required = false) String repositoryId,
            @RequestParam(name = "page", required = false) Integer page,
            @RequestParam(name = "page_size", required = false) Integer pageSize,
            @RequestParam(name = "mode", required = false) String mode,
            @RequestParam(name = "alpha", required = false) Double alpha
    ) {
        UUID repo = null;
        if (repositoryId != null && !repositoryId.isBlank()) {
            repo = parseId(repositoryId, "invalid repository id");
        }
        boolean hasAlpha = alpha != null;
        return svc.search(new SearchQuery(
                q, repo, language, fileExtension,
                page == null ? 0 : page, pageSize == null ? 0 : pageSize,
                mode, hasAlpha ? alpha : 0, hasAlpha
        ));
    }

    @GetMapping("/api/v1/status")
    public Map<String, Object> status() {
        var st = svc.cacheStats();
        Span trace = svc.lastTrace();
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("live", Map.of("status", "ok"));
        out.put("passages", svc.indexStats().passageCount());
        out.put("cache", Map.of("Hits", st.hits(), "Misses", st.misses(), "Entries", st.entries(), "Backend", st.backend()));
        out.put("has_trace", trace != null);
        out.put("last_trace", trace);
        out.put("shards", svc.shardHealth());
        out.put("cache_hit_rate", svc.metrics().cacheHitRate());
        out.put("search_p50_seconds", svc.metrics().searchQuantile(0.50));
        out.put("search_p95_seconds", svc.metrics().searchQuantile(0.95));
        out.put("search_p99_seconds", svc.metrics().searchQuantile(0.99));
        return out;
    }

    @GetMapping(value = "/api/v1/traces", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<byte[]> traces() {
        Span span = svc.lastTrace();
        if (span == null) {
            return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON)
                    .body("{\"resourceSpans\":[]}".getBytes());
        }
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(Otlp.encode(span));
    }

    @GetMapping(value = "/health/live", produces = MediaType.APPLICATION_JSON_VALUE)
    public Map<String, String> live() {
        return Map.of("status", "ok");
    }

    @GetMapping(value = "/health/ready", produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<Map<String, String>> ready() {
        try {
            svc.pingCache();
            svc.readyCluster();
            return ResponseEntity.ok(Map.of("status", "ok"));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(Map.of("status", "unavailable"));
        }
    }

    @GetMapping(value = "/metrics", produces = "text/plain; version=0.0.4; charset=utf-8")
    public String metrics() {
        svc.metrics().setPassages(svc.indexStats().passageCount());
        return svc.metrics().prometheus();
    }

    @ExceptionHandler(StoreException.class)
    public ResponseEntity<Map<String, String>> store(StoreException e) {
        if (e.isNotFound()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "not found"));
        }
        if (e.isConflict()) {
            return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("error", "repository already registered"));
        }
        if (e.isInvalid()) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal error"));
    }

    @ExceptionHandler({CrawlerException.class, IllegalArgumentException.class})
    public ResponseEntity<Map<String, String>> bad(RuntimeException e) {
        return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, String>> other(Exception e) {
        if (JobService.isValidation(e)) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Map.of("error", "internal error"));
    }

    private static UUID parseId(String raw, String msg) {
        try {
            return UUID.fromString(raw.trim());
        } catch (Exception e) {
            throw new IllegalArgumentException(msg);
        }
    }

    private static Map<String, Object> repoJson(Models.Repository repo, Models.Job job) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", repo.id.toString());
        out.put("name", repo.name);
        out.put("owner", repo.owner);
        out.put("source_url", repo.sourceUrl);
        out.put("default_branch", repo.defaultBranch);
        if (repo.latestCommitHash != null && !repo.latestCommitHash.isBlank()) {
            out.put("latest_commit_hash", repo.latestCommitHash);
        }
        out.put("status", repo.status);
        out.put("document_count", repo.documentCount);
        if (repo.indexedAt != null) {
            out.put("indexed_at", rfc3339(repo.indexedAt));
        }
        out.put("created_at", rfc3339(repo.createdAt));
        out.put("updated_at", rfc3339(repo.updatedAt));
        if (job != null) {
            out.put("job_id", job.id.toString());
            out.put("job_status", job.status);
        }
        return out;
    }

    private static Map<String, Object> jobJson(Models.Job job) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("id", job.id.toString());
        out.put("repository_id", job.repositoryId.toString());
        out.put("status", job.status);
        out.put("files_discovered", job.filesDiscovered);
        out.put("files_processed", job.filesProcessed);
        out.put("files_failed", job.filesFailed);
        out.put("passages_created", job.passagesCreated);
        out.put("files_added", job.filesAdded);
        out.put("files_modified", job.filesModified);
        out.put("files_deleted", job.filesDeleted);
        out.put("files_unchanged", job.filesUnchanged);
        if (job.startedAt != null) {
            out.put("started_at", rfc3339(job.startedAt));
        }
        if (job.completedAt != null) {
            out.put("completed_at", rfc3339(job.completedAt));
        }
        if (job.errorMessage != null && !job.errorMessage.isBlank()) {
            out.put("error_message", job.errorMessage);
        }
        out.put("created_at", rfc3339(job.createdAt));
        return out;
    }

    private static String rfc3339(Instant t) {
        if (t == null) {
            return null;
        }
        return DateTimeFormatter.ISO_INSTANT.format(t.atOffset(ZoneOffset.UTC));
    }
}
