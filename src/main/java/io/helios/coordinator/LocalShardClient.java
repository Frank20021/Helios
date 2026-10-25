package io.helios.coordinator;

import io.helios.index.CandidateSet;
import io.helios.shard.ShardService;

import java.util.UUID;

public final class LocalShardClient implements ShardClient {
    private final ShardService svc;

    public LocalShardClient(ShardService svc) {
        this.svc = svc;
    }

    @Override
    public String id() {
        return svc.id();
    }

    @Override
    public CandidateSet search(String query, int limit, String primaryId) {
        return svc.search(query, limit, primaryId);
    }

    @Override
    public void apply(UUID passageId, UUID documentId, String text) {
        svc.apply(passageId, documentId, text);
    }

    @Override
    public void remove(UUID passageId) {
        svc.remove(passageId);
    }

    @Override
    public void persist() {
        svc.persist();
    }

    @Override
    public ShardHealth health() {
        return new ShardHealth(svc.id(), true, svc.stats().passageCount(), svc.primaryCount());
    }
}
