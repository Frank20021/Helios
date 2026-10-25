package io.helios.vector;

import io.helios.embed.Embedder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class VectorIndex {
    public record Hit(UUID passageId, double score) {
    }

    private final int dim;
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final Map<UUID, Integer> by = new HashMap<>();
    private final List<Row> rows = new ArrayList<>();

    public VectorIndex(int dim) {
        this.dim = dim < 1 ? Embedder.DEFAULT_DIM : dim;
    }

    public int dim() {
        return dim;
    }

    public int len() {
        lock.readLock().lock();
        try {
            return rows.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    public void upsert(UUID id, float[] vec) {
        float[] cp = vec.clone();
        lock.writeLock().lock();
        try {
            Integer i = by.get(id);
            if (i != null) {
                rows.get(i).vec = cp;
                return;
            }
            by.put(id, rows.size());
            rows.add(new Row(id, cp));
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(UUID id) {
        lock.writeLock().lock();
        try {
            Integer i = by.get(id);
            if (i == null) {
                return;
            }
            int last = rows.size() - 1;
            if (i != last) {
                rows.set(i, rows.get(last));
                by.put(rows.get(i).id, i);
            }
            rows.remove(last);
            by.remove(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void replaceAll(List<UUID> ids, List<float[]> vecs) {
        lock.writeLock().lock();
        try {
            by.clear();
            rows.clear();
            for (int i = 0; i < ids.size(); i++) {
                float[] v = i < vecs.size() && vecs.get(i) != null ? vecs.get(i).clone() : new float[0];
                by.put(ids.get(i), rows.size());
                rows.add(new Row(ids.get(i), v));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Hit> search(float[] query, int limit) {
        if (limit < 1) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            List<Hit> hits = new ArrayList<>(rows.size());
            for (Row r : rows) {
                hits.add(new Hit(r.id, Embedder.dot(query, r.vec)));
            }
            hits.sort(Comparator.comparingDouble(Hit::score).reversed().thenComparing(h -> h.passageId().toString()));
            if (hits.size() > limit) {
                return new ArrayList<>(hits.subList(0, limit));
            }
            return hits;
        } finally {
            lock.readLock().unlock();
        }
    }

    private static final class Row {
        private final UUID id;
        private float[] vec;

        private Row(UUID id, float[] vec) {
            this.id = id;
            this.vec = vec;
        }
    }
}
