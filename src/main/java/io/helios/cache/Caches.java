package io.helios.cache;

import java.time.Duration;

public final class Caches {
    private Caches() {
    }

    public static QueryCache open(String backend, String redisUrl, Duration ttl) {
        String b = backend == null ? "" : backend.trim().toLowerCase();
        return switch (b) {
            case "", "memory" -> new MemoryCache(ttl);
            case "redis" -> new RedisCache(redisUrl, ttl);
            default -> throw new IllegalArgumentException("unknown cache backend \"" + backend + "\"");
        };
    }
}
