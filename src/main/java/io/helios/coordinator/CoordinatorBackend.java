package io.helios.coordinator;

import io.helios.hashring.HashRing;
import io.helios.index.CandidateSet;
import io.helios.index.Hit;
import io.helios.index.IndexStats;
import io.helios.jobs.Indexer;
import io.helios.repository.Models;
import io.helios.tokenizer.Tokenizer;
import io.helios.tokenizer.TokenizerOptions;
import io.helios.tracing.Recorder;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class CoordinatorBackend implements Indexer {
    private final HashRing ring;
    private final Map<String, ShardClient> clients;
    private final Duration timeout;
    private final double k1;
    private final double b;
    private final TokenizerOptions tok;
    private final Recorder traces;
    private int rf = 1;
    private HealthDetector health = new HealthDetector(2);
    private int retryMax = 2;
    private Duration retryBase = Duration.ofMillis(20);
    private final ExecutorService pool = Executors.newCachedThreadPool();

    public CoordinatorBackend(HashRing ring, List<ShardClient> clients, Duration timeout,
                              double k1, double b, TokenizerOptions tok, Recorder traces) {
        this.ring = ring;
        this.clients = new HashMap<>();
        for (ShardClient c : clients) {
            this.clients.put(c.id(), c);
        }
        this.timeout = timeout == null ? Duration.ofSeconds(2) : timeout;
        this.k1 = k1;
        this.b = b;
        this.tok = tok;
        this.traces = traces;
    }

    public CoordinatorBackend setReplication(int rf) {
        this.rf = rf < 1 ? 1 : rf;
        return this;
    }

    public CoordinatorBackend setRetry(int max, Duration base) {
        this.retryMax = max;
        this.retryBase = base;
        return this;
    }

    public CoordinatorBackend setHealthThreshold(int n) {
        this.health = new HealthDetector(n);
        return this;
    }

    public HealthDetector healthDetector() {
        return health;
    }

    public Recorder traces() {
        return traces;
    }

    @Override
    public void add(UUID passageId, UUID documentId, String text) {
        writeReplicas(documentId, c -> c.apply(passageId, documentId, text));
    }

    @Override
    public void remove(UUID passageId, UUID documentId) {
        writeReplicas(documentId, c -> c.remove(passageId));
    }

    @Override
    public void persist() {
        RuntimeException first = null;
        for (ShardClient c : clients.values()) {
            try {
                c.persist();
            } catch (RuntimeException e) {
                if (first == null) {
                    first = e;
                }
            }
        }
        if (first != null) {
            throw first;
        }
    }

    @Override
    public void recover(List<Models.Passage> passages) {
    }

    @Override
    public List<Hit> searchHits(String query, int limit) {
        var span = traces == null ? null : traces.start("coordinator.search", Map.of("query", query));
        try {
            List<HashRing.Node> primaries = ring.nodes();
            List<CompletableFuture<CandidateSet>> futures = new ArrayList<>();
            for (HashRing.Node primary : primaries) {
                futures.add(CompletableFuture.supplyAsync(() -> {
                    if (traces != null && span != null) {
                        traces.child(span, "shard.search", Map.of("shard", primary.id()));
                    }
                    return searchPartition(query, limit, primary.id());
                }, pool));
            }
            List<CandidateSet> sets = new ArrayList<>();
            RuntimeException first = null;
            for (int i = 0; i < futures.size(); i++) {
                try {
                    sets.add(futures.get(i).orTimeout(timeout.toMillis(), java.util.concurrent.TimeUnit.MILLISECONDS).join());
                } catch (Exception e) {
                    if (first == null) {
                        first = new IllegalStateException("partition " + primaries.get(i).id() + ": " + e.getMessage(), e);
                    }
                }
            }
            if (first != null) {
                throw first;
            }
            List<String> terms = Tokenizer.terms(Tokenizer.tokenize(query, tok));
            return Merge.scores(terms, sets, k1, b);
        } finally {
            if (traces != null && span != null) {
                traces.finish(span);
            }
        }
    }

    @Override
    public IndexStats stats() {
        int n = 0;
        for (ShardClient c : clients.values()) {
            if (!health.healthy(c.id())) {
                continue;
            }
            try {
                n += c.health().primaryCount();
            } catch (RuntimeException ignored) {
            }
        }
        return new IndexStats(n, 0);
    }

    public void ready() {
        int healthy = 0;
        for (ShardClient c : clients.values()) {
            try {
                ShardHealth h = c.health();
                if (!h.ready()) {
                    health.report(c.id(), false);
                    continue;
                }
                health.report(c.id(), true);
                if (health.healthy(c.id())) {
                    healthy++;
                }
            } catch (RuntimeException e) {
                health.report(c.id(), false);
            }
        }
        if (healthy == 0) {
            throw new IllegalStateException("no healthy shards");
        }
        int down = clients.size() - healthy;
        if (down >= rf) {
            throw new IllegalStateException(down + " shards down; replication factor is " + rf);
        }
    }

    public List<ShardHealth> shardHealth() {
        List<ShardHealth> out = new ArrayList<>();
        for (HashRing.Node node : ring.nodes()) {
            ShardClient c = clients.get(node.id());
            if (c == null) {
                out.add(new ShardHealth(node.id()));
                continue;
            }
            try {
                out.add(c.health());
            } catch (RuntimeException e) {
                out.add(new ShardHealth(node.id()));
            }
        }
        return out;
    }

    public void close() {
        pool.shutdownNow();
        for (ShardClient c : clients.values()) {
            c.close();
        }
    }

    private void writeReplicas(UUID documentId, java.util.function.Consumer<ShardClient> fn) {
        List<ShardClient> replicaClients = new ArrayList<>();
        for (HashRing.Node node : ring.replicasDocument(documentId, rf)) {
            ShardClient c = clients.get(node.id());
            if (c != null) {
                replicaClients.add(c);
            }
        }
        if (replicaClients.isEmpty()) {
            throw new IllegalStateException("no replica clients");
        }
        RuntimeException first = null;
        int written = 0;
        for (ShardClient c : replicaClients) {
            try {
                Retries.run(retryMax, retryBase, () -> fn.accept(c));
                health.report(c.id(), true);
                written++;
            } catch (RuntimeException e) {
                health.report(c.id(), false);
                if (first == null) {
                    first = e;
                }
            }
        }
        if (written == 0) {
            throw first == null ? new IllegalStateException("no replica accepted the write") : first;
        }
    }

    CandidateSet searchPartition(String query, int limit, String primaryId) {
        if (health.healthy(primaryId)) {
            ShardClient c = clients.get(primaryId);
            if (c != null) {
                try {
                    return callSearch(c, query, limit, primaryId);
                } catch (RuntimeException e) {
                    health.report(primaryId, false);
                }
            }
        }
        List<CandidateSet> parts = new ArrayList<>();
        for (HashRing.Node node : ring.nodes()) {
            if (node.id().equals(primaryId) || !health.healthy(node.id())) {
                continue;
            }
            ShardClient c = clients.get(node.id());
            if (c == null) {
                continue;
            }
            try {
                parts.add(callSearch(c, query, limit, primaryId));
            } catch (RuntimeException e) {
                health.report(node.id(), false);
            }
        }
        if (parts.isEmpty()) {
            throw new IllegalStateException("no healthy replica for partition " + primaryId);
        }
        return Merge.combinePartition(parts, rf);
    }

    private CandidateSet callSearch(ShardClient c, String query, int limit, String primaryId) {
        CandidateSet set = Retries.call(retryMax, retryBase, () -> c.search(query, limit, primaryId));
        health.report(c.id(), true);
        return set;
    }
}
