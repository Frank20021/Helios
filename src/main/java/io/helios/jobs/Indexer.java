package io.helios.jobs;

import io.helios.index.Hit;
import io.helios.index.IndexStats;
import io.helios.repository.Models;

import java.util.List;
import java.util.UUID;

public interface Indexer {
    void add(UUID passageId, UUID documentId, String text);

    void remove(UUID passageId, UUID documentId);

    void persist();

    void recover(List<Models.Passage> passages);

    List<Hit> searchHits(String query, int limit);

    IndexStats stats();
}
