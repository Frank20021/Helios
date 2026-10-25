package io.helios.coordinator;

import io.helios.index.CandidateSet;

import java.util.UUID;

public interface ShardClient {
    String id();

    CandidateSet search(String query, int limit, String primaryId);

    void apply(UUID passageId, UUID documentId, String text);

    void remove(UUID passageId);

    void persist();

    ShardHealth health();

    default void close() {
    }
}
