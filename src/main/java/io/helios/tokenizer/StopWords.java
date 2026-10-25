package io.helios.tokenizer;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class StopWords {
    private StopWords() {
    }

    public static List<String> defaultStopWords() {
        return List.of(
                "a", "an", "the", "and", "or", "but", "if", "then", "else", "when",
                "at", "by", "for", "with", "about", "against", "between", "into",
                "through", "during", "before", "after", "above", "below", "to", "from",
                "up", "down", "in", "out", "on", "off", "over", "under", "again",
                "further", "once", "here", "there", "all", "any", "both", "each",
                "few", "more", "most", "other", "some", "such", "no", "nor", "not",
                "only", "own", "same", "so", "than", "too", "very", "can", "will",
                "just", "should", "now", "is", "are", "was", "were", "be", "been",
                "being", "have", "has", "had", "do", "does", "did", "of", "as",
                "this", "that", "these", "those", "it", "its", "you", "your", "we",
                "they", "them", "their"
        );
    }

    public static Map<String, Boolean> newStopSet(Iterable<String> words) {
        Map<String, Boolean> set = new LinkedHashMap<>();
        if (words == null) {
            return set;
        }
        for (String word : words) {
            if (word == null) {
                continue;
            }
            String term = word.trim().toLowerCase(Locale.ROOT);
            if (!term.isEmpty()) {
                set.put(term, Boolean.TRUE);
            }
        }
        return set;
    }
}
