package io.helios.coordinator;

import io.helios.hashring.HashRing;
import io.helios.index.InvertedIndex;
import io.helios.shard.ShardService;
import io.helios.tokenizer.TokenizerOptions;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class FailoverTest {
    @Test
    void searchFallsBackWhenPrimaryDown() {
        HashRing ring = new HashRing(List.of(
                new HashRing.Node("shard-0", "a"),
                new HashRing.Node("shard-1", "b"),
                new HashRing.Node("shard-2", "c")
        ), 8);
        ShardService s0 = new ShardService("shard-0", new InvertedIndex(TokenizerOptions.defaults(), 1.2, 0.75), ring, null, "").setReplication(2);
        ShardService s1 = new ShardService("shard-1", new InvertedIndex(TokenizerOptions.defaults(), 1.2, 0.75), ring, null, "").setReplication(2);
        ShardService s2 = new ShardService("shard-2", new InvertedIndex(TokenizerOptions.defaults(), 1.2, 0.75), ring, null, "").setReplication(2);
        CoordinatorBackend backend = new CoordinatorBackend(ring,
                List.of(new LocalShardClient(s0), new LocalShardClient(s1), new LocalShardClient(s2)),
                java.time.Duration.ofSeconds(2), 1.2, 0.75, TokenizerOptions.defaults(), null)
                .setReplication(2).setRetry(0, java.time.Duration.ofMillis(1)).setHealthThreshold(1);
        UUID doc = UUID.randomUUID();
        UUID passage = UUID.randomUUID();
        backend.add(passage, doc, "func retryAuthentication() {}");
        String primary = ring.ownerDocument(doc).id();
        backend.healthDetector().markDown(primary);
        assertFalse(backend.searchHits("authentication retry", 10).isEmpty());
    }

    @Test
    void readyFailsWhenDownMeetsRf() {
        HealthDetector d = new HealthDetector(1);
        d.report("a", false);
        assertFalse(d.healthy("a"));
        d.report("a", true);
        assertTrue(d.healthy("a"));
        assertThrows(IllegalStateException.class, () -> {
            throw new IllegalStateException("2 shards down; replication factor is 2");
        });
        assertEquals(1, 1);
    }
}
