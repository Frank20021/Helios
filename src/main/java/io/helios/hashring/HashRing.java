package io.helios.hashring;

import io.helios.embed.Embedder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class HashRing {
    public static final int DEFAULT_VNODES = 128;

    public record Node(String id, String addr) {
    }

    private record VNode(long hash, String id) {
    }

    private final List<Node> nodes;
    private final List<VNode> vnodes;
    private final Map<String, Node> byId;

    public HashRing(List<Node> nodes, int vnodeCount) {
        if (nodes == null || nodes.isEmpty()) {
            throw new IllegalArgumentException("hash ring requires at least one node");
        }
        if (vnodeCount < 1) {
            vnodeCount = DEFAULT_VNODES;
        }
        this.byId = new HashMap<>();
        List<VNode> points = new ArrayList<>();
        for (Node node : nodes) {
            String id = node.id() == null ? "" : node.id().trim();
            if (id.isEmpty()) {
                throw new IllegalArgumentException("shard id is required");
            }
            if (byId.containsKey(id)) {
                throw new IllegalArgumentException("duplicate shard id \"" + id + "\"");
            }
            Node cleaned = new Node(id, node.addr() == null ? "" : node.addr().trim());
            byId.put(id, cleaned);
            for (int i = 0; i < vnodeCount; i++) {
                points.add(new VNode(hashKey(id + "/" + i), id));
            }
        }
        points.sort(Comparator.comparingLong(VNode::hash).thenComparing(VNode::id));
        this.vnodes = points;
        this.nodes = new ArrayList<>();
        for (Node node : nodes) {
            this.nodes.add(byId.get(node.id().trim()));
        }
    }

    public Node owner(String key) {
        if (vnodes.isEmpty()) {
            return new Node("", "");
        }
        int i = search(hashKey(key));
        return byId.get(vnodes.get(i).id());
    }

    public Node ownerDocument(UUID documentId) {
        return owner(documentId.toString());
    }

    public List<Node> replicas(String key, int rf) {
        if (vnodes.isEmpty()) {
            return List.of();
        }
        if (rf < 1) {
            rf = 1;
        }
        if (rf > nodes.size()) {
            rf = nodes.size();
        }
        int i = search(hashKey(key));
        LinkedHashSet<String> seen = new LinkedHashSet<>();
        for (int walked = 0; walked < vnodes.size() && seen.size() < rf; walked++) {
            seen.add(vnodes.get(i).id());
            i++;
            if (i == vnodes.size()) {
                i = 0;
            }
        }
        List<Node> out = new ArrayList<>();
        for (String id : seen) {
            out.add(byId.get(id));
        }
        return out;
    }

    public List<Node> replicasDocument(UUID documentId, int rf) {
        return replicas(documentId.toString(), rf);
    }

    public boolean member(String key, int rf, String id) {
        for (Node node : replicas(key, rf)) {
            if (node.id().equals(id)) {
                return true;
            }
        }
        return false;
    }

    public List<Node> nodes() {
        return List.copyOf(nodes);
    }

    public Node node(String id) {
        return byId.get(id);
    }

    private int search(long h) {
        int lo = 0;
        int hi = vnodes.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (Long.compareUnsigned(vnodes.get(mid).hash(), h) < 0) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo == vnodes.size() ? 0 : lo;
    }

    static long hashKey(String key) {
        return Embedder.fnv64a(key.getBytes(StandardCharsets.UTF_8));
    }
}
