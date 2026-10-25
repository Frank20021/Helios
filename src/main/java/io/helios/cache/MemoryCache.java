package io.helios.cache;

import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class MemoryCache implements QueryCache {
    private record Item(byte[] value, Instant exp) {
    }

    private final Duration ttl;
    private final Map<String, Item> items = new ConcurrentHashMap<>();
    private long hits;
    private long misses;

    public MemoryCache(Duration ttl) {
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(60) : ttl;
    }

    @Override
    public synchronized byte[] get(String key) {
        Item item = items.get(key);
        if (item == null || Instant.now().isAfter(item.exp())) {
            if (item != null) {
                items.remove(key);
            }
            misses++;
            return null;
        }
        hits++;
        return item.value().clone();
    }

    @Override
    public synchronized void set(String key, byte[] value, Duration itemTtl) {
        Duration use = itemTtl == null || itemTtl.isZero() || itemTtl.isNegative() ? ttl : itemTtl;
        items.put(key, new Item(value.clone(), Instant.now().plus(use)));
    }

    @Override
    public synchronized void deletePrefix(String prefix) {
        Iterator<String> it = items.keySet().iterator();
        while (it.hasNext()) {
            if (it.next().startsWith(prefix)) {
                it.remove();
            }
        }
    }

    @Override
    public void ping() {
    }

    @Override
    public synchronized Stats stats() {
        Instant now = Instant.now();
        int n = 0;
        for (Item item : items.values()) {
            if (now.isBefore(item.exp())) {
                n++;
            }
        }
        return new Stats(hits, misses, n, "memory");
    }
}
