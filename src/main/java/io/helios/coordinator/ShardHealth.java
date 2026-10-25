package io.helios.coordinator;

public record ShardHealth(String id, boolean ready, int passageCount, int primaryCount) {
    public ShardHealth(String id) {
        this(id, false, 0, 0);
    }
}
