package io.helios.tokenizer;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public record TokenizerOptions(int minLength, Map<String, Boolean> stopWords) {

    public static final int DEFAULT_MIN_LENGTH = 2;

    public TokenizerOptions {
        if (minLength <= 0) {
            minLength = DEFAULT_MIN_LENGTH;
        }
        stopWords = stopWords == null ? Map.of() : Map.copyOf(stopWords);
    }

    public static TokenizerOptions defaults() {
        return new TokenizerOptions(DEFAULT_MIN_LENGTH, StopWords.newStopSet(StopWords.defaultStopWords()));
    }

    public static TokenizerOptions of(int minLength, Iterable<String> words) {
        return new TokenizerOptions(minLength, StopWords.newStopSet(words));
    }

    public boolean isStop(String term) {
        return stopWords.containsKey(term);
    }

    public Set<String> stopWordSet() {
        return new LinkedHashSet<>(stopWords.keySet());
    }

    public static Map<String, Boolean> toStopMap(Set<String> words) {
        Map<String, Boolean> out = new ConcurrentHashMap<>();
        for (String word : words) {
            out.put(word.toLowerCase(Locale.ROOT), Boolean.TRUE);
        }
        return out;
    }
}
