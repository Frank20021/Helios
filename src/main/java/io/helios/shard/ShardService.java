package io.helios.shard;

import io.helios.hashring.HashRing;
import io.helios.index.CandidateSet;
import io.helios.index.IndexSnapshot;
import io.helios.index.IndexStats;
import io.helios.index.InvertedIndex;
import io.helios.repository.Models;
import io.helios.repository.Store;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class ShardService {
    private static final Logger log = LoggerFactory.getLogger(ShardService.class);

    private final String id;
    private final InvertedIndex idx;
    private final HashRing ring;
    private final Store store;
    private final String snap;
    private int rf = 1;
    private final Map<UUID, UUID> docs = new HashMap<>();

    public ShardService(String id, InvertedIndex idx, HashRing ring, Store store, String snapshotPath) {
        this.id = id;
        this.idx = idx;
        this.ring = ring;
        this.store = store;
        this.snap = snapshotPath;
    }

    public String id() {
        return id;
    }

    public ShardService setReplication(int rf) {
        this.rf = rf < 1 ? 1 : rf;
        return this;
    }

    public boolean owns(UUID documentId) {
        return ring.member(documentId.toString(), rf, id);
    }

    public CandidateSet search(String query, int limit, String primaryId) {
        if (primaryId == null || primaryId.isBlank()) {
            primaryId = id;
        }
        CandidateSet raw = idx.candidates(query, limit);
        List<UUID> ids = new ArrayList<>();
        Set<UUID> allowed = new HashSet<>();
        synchronized (docs) {
            for (Map.Entry<UUID, UUID> e : docs.entrySet()) {
                if (!ring.ownerDocument(e.getValue()).id().equals(primaryId)) {
                    continue;
                }
                ids.add(e.getKey());
                allowed.add(e.getKey());
            }
        }
        List<String> terms = new ArrayList<>(raw.df().keySet());
        InvertedIndex.SubsetStats stats = idx.subsetStats(ids, terms);
        return InvertedIndex.filterCandidates(raw, allowed, stats.n(), stats.sumLengths(), stats.df());
    }

    public void apply(UUID passageId, UUID documentId, String text) {
        if (!owns(documentId)) {
            throw new IllegalArgumentException("shard " + id + " does not own document " + documentId);
        }
        idx.add(passageId, text);
        synchronized (docs) {
            docs.put(passageId, documentId);
        }
    }

    public void remove(UUID passageId) {
        idx.remove(passageId);
        synchronized (docs) {
            docs.remove(passageId);
        }
    }

    public void persist() {
        try {
            IndexSnapshot.save(idx, snap);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public IndexStats stats() {
        return idx.stats();
    }

    public int primaryCount() {
        synchronized (docs) {
            int n = 0;
            for (UUID documentId : docs.values()) {
                if (ring.ownerDocument(documentId).id().equals(id)) {
                    n++;
                }
            }
            return n;
        }
    }

    public void recover() {
        List<Models.Passage> passages = store.listAllPassages();
        List<UUID> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        Map<UUID, UUID> ownedDocs = new HashMap<>();
        for (Models.Passage p : passages) {
            if (!owns(p.documentId)) {
                continue;
            }
            ids.add(p.id);
            texts.add(p.rawContent);
            ownedDocs.put(p.id, p.documentId);
        }
        try {
            IndexSnapshot.load(idx, snap);
            if (idx.matchesPassageSet(ids)) {
                synchronized (docs) {
                    docs.clear();
                    docs.putAll(ownedDocs);
                }
                log.info("loaded shard snapshot shard={} passages={}", id, ids.size());
                return;
            }
        } catch (IOException ignored) {
        }
        idx.replaceAll(ids, texts);
        synchronized (docs) {
            docs.clear();
            docs.putAll(ownedDocs);
        }
        log.info("rebuilt shard from postgres shard={} passages={}", id, ownedDocs.size());
        persist();
    }
}
