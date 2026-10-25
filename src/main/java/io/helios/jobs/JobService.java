package io.helios.jobs;

import io.helios.cache.QueryCache;
import io.helios.config.HeliosConfig;
import io.helios.coordinator.CoordinatorBackend;
import io.helios.coordinator.ShardHealth;
import io.helios.crawler.CrawlerException;
import io.helios.crawler.Filter;
import io.helios.crawler.Git;
import io.helios.crawler.ProcessRoot;
import io.helios.crawler.RepoRef;
import io.helios.crawler.RepoUrls;
import io.helios.embed.Embedder;
import io.helios.index.IndexStats;
import io.helios.index.InvertedIndex;
import io.helios.repository.Models;
import io.helios.repository.Store;
import io.helios.repository.StoreException;
import io.helios.search.SearchQuery;
import io.helios.search.SearchResponse;
import io.helios.search.SearchService;
import io.helios.telemetry.Metrics;
import io.helios.tokenizer.TokenizerOptions;
import io.helios.tracing.Otlp;
import io.helios.tracing.Recorder;
import io.helios.tracing.Span;
import io.helios.vector.VectorIndex;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public final class JobService {
    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final HeliosConfig cfg;
    private final Store store;
    private Indexer indexer;
    private final SearchService search;
    private final Filter filter;
    private final TokenizerOptions tok;
    private final Embedder embed;
    private final VectorIndex vectors;
    private QueryCache cache;
    private final Metrics metrics;
    private final Recorder traces;
    private final LinkedBlockingQueue<UUID> queue = new LinkedBlockingQueue<>(256);
    private final ExecutorService workers;
    private volatile boolean running = true;

    public JobService(HeliosConfig cfg, Store store, InvertedIndex idx) {
        this.cfg = cfg;
        this.store = store;
        this.tok = cfg.tokenizerOptions();
        this.embed = new Embedder(tok, cfg.search().embedDim());
        this.vectors = new VectorIndex(cfg.search().embedDim());
        this.metrics = new Metrics();
        this.traces = new Recorder(32);
        this.cache = cfg.cache().enabled() ? new io.helios.cache.MemoryCache(cfg.cache().ttl().value()) : null;
        this.indexer = new LocalIndex(idx, cfg.index().snapshotPath());
        this.search = new SearchService(store, indexer, embed, vectors, tok,
                cfg.search().defaultMode(), cfg.search().alpha(), cfg.search().semanticCandidates(),
                cache, cfg.cache().ttl().value(), metrics, traces);
        this.filter = Filter.fromConfig(cfg.crawler());
        this.workers = Executors.newFixedThreadPool(Math.max(1, cfg.jobs().workerCount()));
    }

    public void setIndexer(Indexer indexer) {
        if (indexer == null) {
            return;
        }
        this.indexer = indexer;
        this.search.setHits(indexer);
    }

    public void setCache(QueryCache cache) {
        this.cache = cache;
        search.setCache(cache);
    }

    public void setExporter(Otlp.Exporter exporter) {
        search.setExporter(exporter);
    }

    public Metrics metrics() {
        return metrics;
    }

    public Recorder traces() {
        return traces;
    }

    public QueryCache.Stats cacheStats() {
        return cache == null ? new QueryCache.Stats(0, 0, 0, "") : cache.stats();
    }

    public void pingCache() {
        if (cache != null) {
            cache.ping();
        }
    }

    public void start() {
        try {
            recoverIndex();
        } catch (Exception e) {
            log.error("recover index", e);
        }
        try {
            for (Models.Job job : store.listJobsByStatus(List.of(Models.JOB_QUEUED, Models.JOB_RUNNING))) {
                enqueue(job.id);
            }
        } catch (Exception e) {
            log.error("list interrupted jobs", e);
        }
        int n = Math.max(1, cfg.jobs().workerCount());
        for (int i = 0; i < n; i++) {
            workers.submit(this::worker);
        }
    }

    public void stop() {
        running = false;
        workers.shutdownNow();
        persist();
    }

    public Models.Repository[] submit(String rawUrl) {
        RepoRef ref = RepoUrls.parseUrl(rawUrl, cfg.crawler().allowedHosts());
        try {
            Models.Repository existing = store.getRepositoryBySourceUrl(ref.sourceUrl());
            Models.Job job = new Models.Job();
            job.repositoryId = existing.id;
            job.status = Models.JOB_QUEUED;
            job = store.createJob(job);
            enqueue(job.id);
            return new Models.Repository[]{existing, toRepoHolder(existing, job)};
        } catch (StoreException e) {
            if (!e.isNotFound()) {
                throw e;
            }
        }
        Models.Repository repo = new Models.Repository();
        Models.Job job = createQueued(ref.owner(), ref.name(), ref.sourceUrl());
        enqueue(job.id);
        repo = store.getRepository(job.repositoryId);
        return new Models.Repository[]{repo, toRepoHolder(repo, job)};
    }

    public record SubmitResult(Models.Repository repo, Models.Job job) {
    }

    public SubmitResult submitRepo(String rawUrl) {
        RepoRef ref = RepoUrls.parseUrl(rawUrl, cfg.crawler().allowedHosts());
        try {
            Models.Repository existing = store.getRepositoryBySourceUrl(ref.sourceUrl());
            Models.Job job = new Models.Job();
            job.repositoryId = existing.id;
            job.status = Models.JOB_QUEUED;
            job = store.createJob(job);
            enqueue(job.id);
            return new SubmitResult(existing, job);
        } catch (StoreException e) {
            if (!e.isNotFound()) {
                throw e;
            }
        }
        Models.Job created = createQueued(ref.owner(), ref.name(), ref.sourceUrl());
        Models.Repository repo = store.getRepository(created.repositoryId);
        enqueue(created.id);
        return new SubmitResult(repo, created);
    }

    public Models.Job reindex(UUID repositoryId) {
        store.getRepository(repositoryId);
        Models.Job job = new Models.Job();
        job.repositoryId = repositoryId;
        job.status = Models.JOB_QUEUED;
        job = store.createJob(job);
        enqueue(job.id);
        return job;
    }

    public SubmitResult ingestDirectory(String owner, String name, String sourceUrl, Path root) {
        return ingestDirectoryAt(owner, name, sourceUrl, root, "", "main");
    }

    public SubmitResult ingestDirectoryAt(String owner, String name, String sourceUrl, Path root, String commit, String branch) {
        Models.Repository repo;
        Models.Job job;
        try {
            repo = store.getRepositoryBySourceUrl(sourceUrl);
            job = new Models.Job();
            job.repositoryId = repo.id;
            job.status = Models.JOB_QUEUED;
            job = store.createJob(job);
        } catch (StoreException e) {
            if (!e.isNotFound()) {
                throw e;
            }
            job = createQueued(owner, name, sourceUrl);
            repo = store.getRepository(job.repositoryId);
        }
        indexRoot(repo, job, root, commit, branch);
        return new SubmitResult(store.getRepository(repo.id), store.getJob(job.id));
    }

    public List<Models.Repository> listRepositories() {
        return store.listRepositories();
    }

    public Models.Repository getRepository(UUID id) {
        return store.getRepository(id);
    }

    public Models.Job latestJob(UUID repositoryId) {
        return store.latestJob(repositoryId);
    }

    public Models.Job getJob(UUID id) {
        return store.getJob(id);
    }

    public void deleteRepository(UUID id) {
        dropIndex(id);
        store.deleteRepository(id);
        persist();
    }

    public void recoverIndex() {
        List<Models.Passage> passages = store.listAllPassages();
        indexer.recover(passages);
        recoverVectors(passages);
        log.info("index ready passages={} vectors={}", indexer.stats().passageCount(), vectors.len());
    }

    public SearchResponse search(SearchQuery q) {
        return search.search(q);
    }

    public IndexStats indexStats() {
        return indexer.stats();
    }

    public List<ShardHealth> shardHealth() {
        if (indexer instanceof CoordinatorBackend backend) {
            return backend.shardHealth();
        }
        return List.of();
    }

    public void readyCluster() {
        if (indexer instanceof CoordinatorBackend backend) {
            backend.ready();
        }
    }

    public Span lastTrace() {
        Span span = traces.last();
        if (span != null) {
            return span;
        }
        if (indexer instanceof CoordinatorBackend backend && backend.traces() != null) {
            return backend.traces().last();
        }
        return null;
    }

    private Models.Job createQueued(String owner, String name, String sourceUrl) {
        try {
            store.getRepositoryBySourceUrl(sourceUrl);
            throw StoreException.conflict(null);
        } catch (StoreException e) {
            if (!e.isNotFound()) {
                throw e;
            }
        }
        Models.Repository repo = new Models.Repository();
        repo.name = name;
        repo.owner = owner;
        repo.sourceUrl = sourceUrl;
        repo.status = Models.REPO_QUEUED;
        repo = store.createRepository(repo);
        try {
            Models.Job job = new Models.Job();
            job.repositoryId = repo.id;
            job.status = Models.JOB_QUEUED;
            return store.createJob(job);
        } catch (RuntimeException e) {
            store.deleteRepository(repo.id);
            throw e;
        }
    }

    private void enqueue(UUID id) {
        queue.offer(id);
    }

    private void dropIndex(UUID repositoryId) {
        for (Models.PassageRef ref : store.listPassageRefsByRepository(repositoryId)) {
            indexer.remove(ref.id, ref.documentId);
            vectors.remove(ref.id);
        }
        store.deleteDocumentsByRepository(repositoryId);
    }

    private void persist() {
        try {
            indexer.persist();
        } catch (Exception e) {
            log.error("save index snapshot", e);
        }
        if (cache != null) {
            cache.deletePrefix(QueryCache.PREFIX);
        }
    }

    private void fail(Models.Repository repo, Models.Job job, Exception err) {
        Instant now = Instant.now();
        job.status = Models.JOB_FAILED;
        job.completedAt = now;
        job.errorMessage = err.getMessage() == null ? err.toString() : err.getMessage();
        try {
            store.updateJob(job);
        } catch (Exception e) {
            log.error("update failed job", e);
        }
        List<Models.Document> docs = store.listDocumentsByRepository(repo.id);
        if (!docs.isEmpty()) {
            repo.status = Models.REPO_INDEXED;
            repo.documentCount = docs.size();
        } else {
            repo.status = Models.REPO_FAILED;
        }
        try {
            store.updateRepository(repo);
        } catch (Exception e) {
            log.error("update failed repository", e);
        }
        metrics.observeIndex("error", 0, 0);
        persist();
    }

    void indexRoot(Models.Repository repo, Models.Job job, Path root, String commit, String branch) {
        long started = System.nanoTime();
        Instant now = Instant.now();
        job.status = Models.JOB_RUNNING;
        if (job.startedAt == null) {
            job.startedAt = now;
        }
        job = store.updateJob(job);
        repo.status = Models.REPO_INDEXING;
        try {
            repo = store.updateRepository(repo);
        } catch (Exception e) {
            fail(repo, job, e);
            throw e;
        }
        List<Models.Document> existing;
        try {
            existing = store.listDocumentsByRepository(repo.id);
        } catch (Exception e) {
            fail(repo, job, e);
            throw e;
        }
        if (commit != null && !commit.isBlank() && commit.equals(repo.latestCommitHash) && !existing.isEmpty()) {
            Instant done = Instant.now();
            job.status = Models.JOB_SUCCEEDED;
            job.filesDiscovered = existing.size();
            job.filesUnchanged = existing.size();
            job.completedAt = done;
            store.updateJob(job);
            repo.status = Models.REPO_INDEXED;
            store.updateRepository(repo);
            log.info("reindex skipped; commit unchanged repository_id={} commit={}", repo.id, commit);
            return;
        }
        ProcessRoot.Result parsed;
        try {
            parsed = ProcessRoot.process(root, filter, tok);
        } catch (Exception e) {
            fail(repo, job, e);
            throw new IllegalStateException(e);
        }
        Map<String, Models.Document> byPath = new HashMap<>();
        for (Models.Document doc : existing) {
            byPath.put(doc.filePath, doc);
        }
        job.filesDiscovered = parsed.filesDiscovered();
        job.filesFailed = parsed.filesFailed();
        job = store.updateJob(job);
        Map<String, Boolean> seen = new HashMap<>();
        int passages = 0;
        for (ProcessRoot.Document doc : parsed.documents()) {
            seen.put(doc.relPath(), true);
            Models.Document prev = byPath.get(doc.relPath());
            try {
                if (prev == null) {
                    passages += addDocument(repo.id, doc);
                    job.filesAdded++;
                    job.filesProcessed++;
                } else if (prev.contentHash.equals(doc.contentHash())) {
                    job.filesUnchanged++;
                } else {
                    removeDocument(prev);
                    passages += addDocument(repo.id, doc);
                    job.filesModified++;
                    job.filesProcessed++;
                }
                job.passagesCreated = passages;
                job = store.updateJob(job);
            } catch (Exception e) {
                fail(repo, job, e);
                throw e;
            }
        }
        for (Map.Entry<String, Models.Document> e : byPath.entrySet()) {
            if (seen.containsKey(e.getKey())) {
                continue;
            }
            try {
                removeDocument(e.getValue());
                job.filesDeleted++;
                job = store.updateJob(job);
            } catch (Exception ex) {
                fail(repo, job, ex);
                throw ex;
            }
        }
        Instant done = Instant.now();
        job.status = Models.JOB_SUCCEEDED;
        job.completedAt = done;
        store.updateJob(job);
        List<Models.Document> docs = store.listDocumentsByRepository(repo.id);
        if (branch != null && !branch.isBlank()) {
            repo.defaultBranch = branch;
        }
        repo.latestCommitHash = commit == null ? "" : commit;
        repo.status = Models.REPO_INDEXED;
        repo.documentCount = docs.size();
        repo.indexedAt = done;
        store.updateRepository(repo);
        persist();
        metrics.observeIndex("ok", passages, (System.nanoTime() - started) / 1_000_000_000.0);
        metrics.setPassages(indexStats().passageCount());
        log.info("indexing completed repository_id={} job_id={} added={} modified={} deleted={} unchanged={} passages={}",
                repo.id, job.id, job.filesAdded, job.filesModified, job.filesDeleted, job.filesUnchanged, passages);
    }

    private void indexVector(UUID passageId, String text) {
        if (embed == null || vectors == null) {
            return;
        }
        float[] vec = embed.embed(text);
        vectors.upsert(passageId, vec);
        Models.Embedding row = new Models.Embedding();
        row.passageId = passageId;
        row.vector = Embedder.encode(vec);
        row.dims = vec.length;
        row.modelId = embed.modelId();
        store.upsertEmbedding(row);
    }

    private void recoverVectors(List<Models.Passage> passages) {
        if (embed == null || vectors == null) {
            return;
        }
        List<Models.Embedding> stored = store.listEmbeddings(embed.modelId());
        Map<UUID, float[]> byId = new HashMap<>();
        for (Models.Embedding row : stored) {
            if (row.dims != embed.dim()) {
                continue;
            }
            try {
                float[] vec = Embedder.decode(row.vector);
                if (vec.length == embed.dim()) {
                    byId.put(row.passageId, vec);
                }
            } catch (Exception ignored) {
            }
        }
        boolean complete = byId.size() == passages.size();
        List<UUID> ids = new ArrayList<>();
        List<float[]> vecs = new ArrayList<>();
        for (Models.Passage p : passages) {
            float[] vec = byId.get(p.id);
            if (vec == null) {
                complete = false;
                break;
            }
            ids.add(p.id);
            vecs.add(vec);
        }
        if (complete) {
            vectors.replaceAll(ids, vecs);
            return;
        }
        for (Models.Passage p : passages) {
            indexVector(p.id, p.rawContent);
        }
    }

    private int addDocument(UUID repoId, ProcessRoot.Document doc) {
        Models.Document stored = new Models.Document();
        stored.repositoryId = repoId;
        stored.filePath = doc.relPath();
        stored.language = doc.language();
        stored.contentHash = doc.contentHash();
        stored.sizeBytes = doc.sizeBytes();
        stored = store.createDocument(stored);
        int count = 0;
        for (io.helios.parser.Passage p : doc.passages()) {
            Models.Passage row = new Models.Passage();
            row.documentId = stored.id;
            row.symbolName = p.symbolName();
            row.symbolType = p.symbolType();
            row.startingLine = p.startingLine();
            row.endingLine = p.endingLine();
            row.rawContent = p.content();
            row.tokenCount = p.tokenCount();
            row = store.createPassage(row);
            indexer.add(row.id, stored.id, p.content());
            indexVector(row.id, p.content());
            count++;
        }
        return count;
    }

    private void removeDocument(Models.Document doc) {
        for (Models.Passage p : store.listPassagesByDocument(doc.id)) {
            indexer.remove(p.id, doc.id);
            vectors.remove(p.id);
        }
        store.deleteDocument(doc.id);
    }

    private void worker() {
        while (running) {
            try {
                UUID id = queue.poll(200, TimeUnit.MILLISECONDS);
                if (id != null) {
                    runRemoteJob(id);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void runRemoteJob(UUID jobId) {
        Models.Job job;
        Models.Repository repo;
        try {
            job = store.getJob(jobId);
            repo = store.getRepository(job.repositoryId);
        } catch (Exception e) {
            log.error("load job {}", jobId, e);
            return;
        }
        Duration timeout = cfg.crawler().cloneTimeout().value().plus(Duration.ofMinutes(2));
        Instant now = Instant.now();
        job.status = Models.JOB_RUNNING;
        job.startedAt = now;
        try {
            job = store.updateJob(job);
            repo.status = Models.REPO_CLONING;
            repo = store.updateRepository(repo);
            Path dir = Git.createCloneDir(cfg.crawler().tempDir());
            try {
                Git.cloneRepo(repo.sourceUrl, dir, cfg.crawler().cloneTimeout().value());
                String commit = Git.headCommit(dir);
                String branch = Git.headBranch(dir);
                indexRoot(repo, job, dir, commit, branch);
            } finally {
                try {
                    Git.removeCloneDir(dir);
                } catch (Exception e) {
                    log.warn("cleanup clone dir", e);
                }
            }
        } catch (Exception e) {
            fail(repo, job, e instanceof RuntimeException re ? re : new IllegalStateException(e));
            log.error("index repository {}", repo.id, e);
        }
        if (timeout.isNegative()) {
            log.debug("timeout unused {}", timeout);
        }
    }

    public static boolean isValidation(Throwable err) {
        return RepoUrls.isValidation(err) || err instanceof CrawlerException && RepoUrls.isValidation(err);
    }

    private static Models.Repository toRepoHolder(Models.Repository repo, Models.Job job) {
        return repo;
    }
}
