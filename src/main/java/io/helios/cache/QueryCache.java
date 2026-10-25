package io.helios.cache;

import java.time.Duration;

public interface QueryCache {
    String PREFIX = "helios:search:v1:";

    record Stats(long hits, long misses, int entries, String backend) {
    }

    record QueryKey(String text, String mode, double alpha, boolean hasAlpha, String repositoryId,
                    String language, String fileExtension, int page, int pageSize) {
    }

    byte[] get(String key);

    void set(String key, byte[] value, Duration ttl);

    void deletePrefix(String prefix);

    void ping();

    Stats stats();

    default void close() {
    }
}
