package io.helios.hashring;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashRingTest {
    @Test
    void replicasAreDistinctAndStable() {
        HashRing ring = new HashRing(List.of(
                new HashRing.Node("shard-0", "s0:9000"),
                new HashRing.Node("shard-1", "s1:9000"),
                new HashRing.Node("shard-2", "s2:9000")
        ), 128);
        UUID doc = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
        List<HashRing.Node> rf2 = ring.replicasDocument(doc, 2);
        assertEquals(2, rf2.size());
        assertEquals(rf2.getFirst(), ring.ownerDocument(doc));
        assertTrue(ring.member(doc.toString(), 2, rf2.get(1).id()));
        assertEquals(rf2, ring.replicasDocument(doc, 2));
    }
}
