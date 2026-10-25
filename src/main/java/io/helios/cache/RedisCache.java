package io.helios.cache;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.ScanArgs;
import io.lettuce.core.ScanCursor;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.api.sync.RedisCommands;

import java.time.Duration;
import java.util.List;

public final class RedisCache implements QueryCache {
    private final Duration ttl;
    private final RedisClient client;
    private final StatefulRedisConnection<String, byte[]> connection;
    private long hits;
    private long misses;

    public RedisCache(String redisUrl, Duration ttl) {
        if (redisUrl == null || redisUrl.isBlank()) {
            throw new IllegalArgumentException("cache.redis_url is required for the redis backend");
        }
        this.ttl = ttl == null || ttl.isZero() || ttl.isNegative() ? Duration.ofSeconds(60) : ttl;
        this.client = RedisClient.create(RedisURI.create(redisUrl));
        this.connection = client.connect(new ByteArrayCodec());
        connection.sync().ping();
    }

    @Override
    public synchronized byte[] get(String key) {
        byte[] val = connection.sync().get(key);
        if (val == null) {
            misses++;
            return null;
        }
        hits++;
        return val;
    }

    @Override
    public void set(String key, byte[] value, Duration itemTtl) {
        Duration use = itemTtl == null || itemTtl.isZero() || itemTtl.isNegative() ? ttl : itemTtl;
        connection.sync().setex(key, use.toSeconds(), value);
    }

    @Override
    public void deletePrefix(String prefix) {
        RedisCommands<String, byte[]> cmd = connection.sync();
        ScanCursor cursor = ScanCursor.INITIAL;
        ScanArgs args = ScanArgs.Builder.matches(prefix + "*").limit(64);
        do {
            var scan = cmd.scan(cursor, args);
            List<String> keys = scan.getKeys();
            if (!keys.isEmpty()) {
                cmd.del(keys.toArray(String[]::new));
            }
            cursor = scan;
        } while (!cursor.isFinished());
    }

    @Override
    public void ping() {
        connection.sync().ping();
    }

    @Override
    public synchronized Stats stats() {
        int n = 0;
        try {
            n = connection.sync().dbsize().intValue();
        } catch (RuntimeException ignored) {
        }
        return new Stats(hits, misses, n, "redis");
    }

    @Override
    public void close() {
        connection.close();
        client.shutdown();
    }

    private static final class ByteArrayCodec implements io.lettuce.core.codec.RedisCodec<String, byte[]> {
        @Override
        public String decodeKey(java.nio.ByteBuffer bytes) {
            byte[] b = new byte[bytes.remaining()];
            bytes.get(b);
            return new String(b, java.nio.charset.StandardCharsets.UTF_8);
        }

        @Override
        public byte[] decodeValue(java.nio.ByteBuffer bytes) {
            byte[] b = new byte[bytes.remaining()];
            bytes.get(b);
            return b;
        }

        @Override
        public java.nio.ByteBuffer encodeKey(String key) {
            return java.nio.ByteBuffer.wrap(key.getBytes(java.nio.charset.StandardCharsets.UTF_8));
        }

        @Override
        public java.nio.ByteBuffer encodeValue(byte[] value) {
            return java.nio.ByteBuffer.wrap(value);
        }
    }
}
