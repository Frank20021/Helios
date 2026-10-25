package io.helios.jobs;

import io.helios.index.Hit;
import io.helios.index.IndexSnapshot;
import io.helios.index.IndexStats;
import io.helios.index.InvertedIndex;
import io.helios.repository.Models;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public final class LocalIndex implements Indexer {
    private final InvertedIndex idx;
    private final String path;

    public LocalIndex(InvertedIndex idx, String path) {
        this.idx = idx;
        this.path = path;
    }

    @Override
    public void add(UUID passageId, UUID documentId, String text) {
        idx.add(passageId, text);
    }

    @Override
    public void remove(UUID passageId, UUID documentId) {
        idx.remove(passageId);
    }

    @Override
    public void persist() {
        try {
            IndexSnapshot.save(idx, path);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void recover(List<Models.Passage> passages) {
        List<UUID> ids = new ArrayList<>();
        List<String> texts = new ArrayList<>();
        for (Models.Passage p : passages) {
            ids.add(p.id);
            texts.add(p.rawContent);
        }
        try {
            IndexSnapshot.load(idx, path);
            if (idx.matchesPassageSet(ids)) {
                return;
            }
        } catch (IOException ignored) {
        }
        idx.replaceAll(ids, texts);
        persist();
    }

    @Override
    public List<Hit> searchHits(String query, int limit) {
        return idx.search(query, limit);
    }

    @Override
    public IndexStats stats() {
        return idx.stats();
    }
}
