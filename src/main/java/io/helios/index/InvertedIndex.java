package io.helios.index;

import io.helios.bm25.Bm25;
import io.helios.tokenizer.Token;
import io.helios.tokenizer.Tokenizer;
import io.helios.tokenizer.TokenizerOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantReadWriteLock;

public final class InvertedIndex {
    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();
    private final TokenizerOptions tok;
    private final double k1;
    private final double b;

    private Map<String, List<Posting>> postings = new HashMap<>();
    private Map<String, Integer> df = new HashMap<>();
    private Map<UUID, Integer> lengths = new HashMap<>();
    private Map<UUID, List<String>> terms = new HashMap<>();
    private int sumLengths;

    public InvertedIndex(TokenizerOptions tok, double k1, double b) {
        if (k1 <= 0) {
            k1 = Bm25.DEFAULT_K1;
        }
        if (b < 0 || b > 1) {
            b = Bm25.DEFAULT_B;
        }
        if (tok == null || (tok.minLength() <= 0 && tok.stopWords().isEmpty())) {
            tok = TokenizerOptions.defaults();
        }
        this.tok = tok;
        this.k1 = k1;
        this.b = b;
    }

    public TokenizerOptions tokenizerOptions() {
        return tok;
    }

    public double k1() {
        return k1;
    }

    public double b() {
        return b;
    }

    public void add(UUID passageId, String text) {
        List<Token> tokens = Tokenizer.tokenize(text, tok);
        lock.writeLock().lock();
        try {
            removeLocked(passageId);
            addLocked(passageId, tokens);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public void remove(UUID passageId) {
        lock.writeLock().lock();
        try {
            removeLocked(passageId);
        } finally {
            lock.writeLock().unlock();
        }
    }

    public List<Posting> postings(String term) {
        lock.readLock().lock();
        try {
            List<Posting> src = postings.getOrDefault(term, List.of());
            List<Posting> out = new ArrayList<>(src.size());
            for (Posting p : src) {
                out.add(p.copy());
            }
            return out;
        } finally {
            lock.readLock().unlock();
        }
    }

    public IndexStats stats() {
        lock.readLock().lock();
        try {
            return statsLocked();
        } finally {
            lock.readLock().unlock();
        }
    }

    public int documentFrequency(String term) {
        lock.readLock().lock();
        try {
            return df.getOrDefault(term, 0);
        } finally {
            lock.readLock().unlock();
        }
    }

    public int length(UUID passageId) {
        lock.readLock().lock();
        try {
            return lengths.getOrDefault(passageId, 0);
        } finally {
            lock.readLock().unlock();
        }
    }

    public List<Hit> search(String query, int limit) {
        List<String> qTerms = Tokenizer.terms(Tokenizer.tokenize(query, tok));
        if (qTerms.isEmpty()) {
            return List.of();
        }
        lock.readLock().lock();
        try {
            IndexStats stats = statsLocked();
            if (stats.passageCount() == 0) {
                return List.of();
            }
            Map<UUID, Acc> candidates = new LinkedHashMap<>();
            for (String term : qTerms) {
                for (Posting posting : postings.getOrDefault(term, List.of())) {
                    Acc item = candidates.computeIfAbsent(posting.passageId(), id -> new Acc());
                    item.tf.put(term, posting.termFrequency());
                    item.matched.add(term);
                }
            }
            List<Hit> hits = new ArrayList<>(candidates.size());
            for (Map.Entry<UUID, Acc> e : candidates.entrySet()) {
                UUID id = e.getKey();
                Acc item = e.getValue();
                double score = Bm25.score(qTerms, item.tf, lengths.getOrDefault(id, 0),
                        stats.averageLength(), stats.passageCount(), df, k1, b);
                hits.add(new Hit(id, score, item.matched));
            }
            hits.sort(Comparator
                    .comparingDouble(Hit::score).reversed()
                    .thenComparing(h -> h.passageId().toString()));
            if (limit > 0 && hits.size() > limit) {
                return new ArrayList<>(hits.subList(0, limit));
            }
            return hits;
        } finally {
            lock.readLock().unlock();
        }
    }

    public CandidateSet candidates(String query, int limit) {
        List<String> qTerms = Tokenizer.terms(Tokenizer.tokenize(query, tok));
        lock.readLock().lock();
        try {
            CandidateSet out = new CandidateSet(lengths.size(), sumLengths, new LinkedHashMap<>(), new ArrayList<>());
            if (qTerms.isEmpty() || out.passageCount() == 0) {
                return out;
            }
            for (String term : qTerms) {
                out.df().put(term, df.getOrDefault(term, 0));
            }
            Map<UUID, Acc> candidates = new LinkedHashMap<>();
            for (String term : qTerms) {
                for (Posting posting : postings.getOrDefault(term, List.of())) {
                    Acc item = candidates.computeIfAbsent(posting.passageId(), id -> new Acc());
                    item.tf.put(term, posting.termFrequency());
                    item.matched.add(term);
                }
            }
            List<Candidate> list = new ArrayList<>(candidates.size());
            for (Map.Entry<UUID, Acc> e : candidates.entrySet()) {
                list.add(new Candidate(e.getKey(), lengths.getOrDefault(e.getKey(), 0), e.getValue().tf, e.getValue().matched));
            }
            list.sort(Comparator.comparing(c -> c.passageId().toString()));
            if (limit > 0 && list.size() > limit) {
                list = new ArrayList<>(list.subList(0, limit));
            }
            return new CandidateSet(out.passageCount(), out.sumLengths(), out.df(), list);
        } finally {
            lock.readLock().unlock();
        }
    }

    public SubsetStats subsetStats(List<UUID> ids, List<String> terms) {
        lock.readLock().lock();
        try {
            Set<UUID> allowed = new HashSet<>();
            int n = 0;
            int sum = 0;
            for (UUID id : ids) {
                Integer len = lengths.get(id);
                if (len != null) {
                    allowed.add(id);
                    n++;
                    sum += len;
                }
            }
            Map<String, Integer> outDf = new LinkedHashMap<>();
            for (String term : terms) {
                int count = 0;
                for (Posting posting : postings.getOrDefault(term, List.of())) {
                    if (allowed.contains(posting.passageId())) {
                        count++;
                    }
                }
                outDf.put(term, count);
            }
            return new SubsetStats(n, sum, outDf);
        } finally {
            lock.readLock().unlock();
        }
    }

    public static CandidateSet filterCandidates(CandidateSet set, Set<UUID> allowed, int n, int sumLengths,
                                                Map<String, Integer> df) {
        List<Candidate> out = new ArrayList<>();
        for (Candidate cand : set.candidates()) {
            if (allowed.contains(cand.passageId())) {
                out.add(cand);
            }
        }
        return new CandidateSet(n, sumLengths, df == null ? new LinkedHashMap<>() : df, out);
    }

    public List<UUID> passageIds() {
        lock.readLock().lock();
        try {
            return new ArrayList<>(lengths.keySet());
        } finally {
            lock.readLock().unlock();
        }
    }

    public boolean matchesPassageSet(List<UUID> ids) {
        lock.readLock().lock();
        try {
            if (ids.size() != lengths.size()) {
                return false;
            }
            for (UUID id : ids) {
                if (!lengths.containsKey(id)) {
                    return false;
                }
            }
            return true;
        } finally {
            lock.readLock().unlock();
        }
    }

    public void replaceAll(List<UUID> ids, List<String> texts) {
        if (ids.size() != texts.size()) {
            return;
        }
        lock.writeLock().lock();
        try {
            postings = new HashMap<>();
            df = new HashMap<>();
            lengths = new HashMap<>();
            terms = new HashMap<>();
            sumLengths = 0;
            for (int i = 0; i < ids.size(); i++) {
                addLocked(ids.get(i), Tokenizer.tokenize(texts.get(i), tok));
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    Snapshot snapshot() {
        lock.readLock().lock();
        try {
            return new Snapshot(IndexSnapshot.VERSION, IndexSnapshot.optionsKey(tok),
                    clonePostings(postings), new HashMap<>(df), new HashMap<>(lengths),
                    cloneTerms(terms), sumLengths, lengths.size());
        } finally {
            lock.readLock().unlock();
        }
    }

    void loadSnapshot(Snapshot snap) {
        lock.writeLock().lock();
        try {
            postings = snap.postings() == null ? new HashMap<>() : snap.postings();
            df = snap.df() == null ? new HashMap<>() : snap.df();
            lengths = snap.lengths() == null ? new HashMap<>() : snap.lengths();
            terms = snap.terms() == null ? new HashMap<>() : snap.terms();
            sumLengths = snap.sumLengths();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private IndexStats statsLocked() {
        int n = lengths.size();
        double avg = n > 0 ? (double) sumLengths / n : 0;
        return new IndexStats(n, avg);
    }

    private void addLocked(UUID passageId, List<Token> tokens) {
        Map<String, List<Integer>> grouped = new LinkedHashMap<>();
        Set<Integer> positions = new HashSet<>();
        List<String> termOrder = new ArrayList<>();
        for (Token tok : tokens) {
            if (!grouped.containsKey(tok.term())) {
                termOrder.add(tok.term());
            }
            grouped.computeIfAbsent(tok.term(), k -> new ArrayList<>()).add(tok.position());
            positions.add(tok.position());
        }
        int length = positions.size();
        lengths.put(passageId, length);
        sumLengths += length;
        terms.put(passageId, termOrder);
        for (String term : termOrder) {
            List<Integer> pos = uniqueSorted(grouped.get(term));
            postings.computeIfAbsent(term, k -> new ArrayList<>())
                    .add(new Posting(passageId, pos.size(), pos));
            df.merge(term, 1, Integer::sum);
        }
    }

    private void removeLocked(UUID passageId) {
        List<String> owned = terms.get(passageId);
        if (owned == null) {
            return;
        }
        for (String term : owned) {
            List<Posting> list = postings.get(term);
            if (list != null) {
                list.removeIf(p -> p.passageId().equals(passageId));
                if (list.isEmpty()) {
                    postings.remove(term);
                }
            }
            int next = df.getOrDefault(term, 0) - 1;
            if (next <= 0) {
                df.remove(term);
            } else {
                df.put(term, next);
            }
        }
        sumLengths -= lengths.getOrDefault(passageId, 0);
        lengths.remove(passageId);
        terms.remove(passageId);
    }

    private static List<Integer> uniqueSorted(List<Integer> in) {
        if (in == null || in.isEmpty()) {
            return List.of();
        }
        return new ArrayList<>(new TreeSet<>(in));
    }

    private static Map<String, List<Posting>> clonePostings(Map<String, List<Posting>> in) {
        Map<String, List<Posting>> out = new HashMap<>();
        in.forEach((term, list) -> {
            List<Posting> copied = new ArrayList<>(list.size());
            for (Posting p : list) {
                copied.add(p.copy());
            }
            out.put(term, copied);
        });
        return out;
    }

    private static Map<UUID, List<String>> cloneTerms(Map<UUID, List<String>> in) {
        Map<UUID, List<String>> out = new HashMap<>();
        in.forEach((k, v) -> out.put(k, new ArrayList<>(v)));
        return out;
    }

    private static final class Acc {
        private final Map<String, Integer> tf = new LinkedHashMap<>();
        private final List<String> matched = new ArrayList<>();
    }

    public record Snapshot(int version, String optionsKey, Map<String, List<Posting>> postings,
                           Map<String, Integer> df, Map<UUID, Integer> lengths,
                           Map<UUID, List<String>> terms, int sumLengths, int passageCount) {
    }

    public record SubsetStats(int n, int sumLengths, Map<String, Integer> df) {
    }
}
