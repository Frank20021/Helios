package io.helios.tokenizer;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public final class Tokenizer {
    private Tokenizer() {
    }

    public static List<Token> tokenize(String text, TokenizerOptions opts) {
        if (opts == null) {
            opts = TokenizerOptions.defaults();
        }
        int minLength = opts.minLength();
        if (text == null || text.isEmpty()) {
            return List.of();
        }
        List<Token> out = new ArrayList<>();
        int pos = 0;
        int i = 0;
        while (i < text.length()) {
            int cp = text.codePointAt(i);
            int size = Character.charCount(cp);
            if (!isIdentStart(cp)) {
                i += size;
                continue;
            }
            int j = i + size;
            while (j < text.length()) {
                int next = text.codePointAt(j);
                if (!isIdentPart(next)) {
                    break;
                }
                j += Character.charCount(next);
            }
            String surface = text.substring(i, j);
            i = j;
            if (!hasLetter(surface)) {
                continue;
            }
            for (String term : expand(surface)) {
                if (term.codePointCount(0, term.length()) < minLength) {
                    continue;
                }
                if (opts.isStop(term)) {
                    continue;
                }
                out.add(new Token(term, pos));
            }
            pos++;
        }
        return out;
    }

    public static int surfaceCount(List<Token> tokens) {
        Set<Integer> seen = new LinkedHashSet<>();
        for (Token tok : tokens) {
            seen.add(tok.position());
        }
        return seen.size();
    }

    public static List<String> terms(List<Token> tokens) {
        Set<String> seen = new LinkedHashSet<>();
        List<String> out = new ArrayList<>();
        for (Token tok : tokens) {
            if (seen.add(tok.term())) {
                out.add(tok.term());
            }
        }
        return out;
    }

    static List<String> expand(String surface) {
        Map<String, Boolean> seen = new LinkedHashMap<>();
        List<String> terms = new ArrayList<>();
        for (String part : splitSeparators(surface)) {
            for (String piece : splitCamel(part)) {
                add(seen, terms, piece);
            }
        }
        add(seen, terms, stripSeparators(surface));
        return terms;
    }

    private static void add(Map<String, Boolean> seen, List<String> terms, String raw) {
        String term = normalizeTerm(raw);
        if (term.isEmpty() || seen.containsKey(term)) {
            return;
        }
        seen.put(term, Boolean.TRUE);
        terms.add(term);
    }

    static List<String> splitSeparators(String s) {
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 0; i < s.length(); ) {
            int cp = s.codePointAt(i);
            int size = Character.charCount(cp);
            if (cp == '_' || cp == '-') {
                if (i > start) {
                    parts.add(s.substring(start, i));
                }
                start = i + size;
            }
            i += size;
        }
        if (start < s.length()) {
            parts.add(s.substring(start));
        }
        return parts;
    }

    static List<String> splitCamel(String s) {
        int[] runes = s.codePoints().toArray();
        if (runes.length == 0) {
            return List.of();
        }
        List<String> parts = new ArrayList<>();
        int start = 0;
        for (int i = 1; i < runes.length; i++) {
            int prev = runes[i - 1];
            int curr = runes[i];
            boolean nextLower = i + 1 < runes.length && Character.isLowerCase(runes[i + 1]);
            boolean lowerOrDigitToUpper = (Character.isLowerCase(prev) || Character.isDigit(prev))
                    && Character.isUpperCase(curr);
            boolean acronymToWord = Character.isUpperCase(prev) && Character.isUpperCase(curr) && nextLower;
            if (lowerOrDigitToUpper || acronymToWord) {
                parts.add(new String(runes, start, i - start));
                start = i;
            }
        }
        parts.add(new String(runes, start, runes.length - start));
        return parts;
    }

    static String stripSeparators(String s) {
        StringBuilder b = new StringBuilder(s.length());
        s.codePoints().forEach(cp -> {
            if (cp != '_' && cp != '-') {
                b.appendCodePoint(cp);
            }
        });
        return b.toString();
    }

    static String normalizeTerm(String s) {
        return s == null ? "" : s.trim().toLowerCase(Locale.ROOT);
    }

    private static boolean isIdentStart(int cp) {
        return Character.isLetter(cp) || Character.isDigit(cp);
    }

    private static boolean isIdentPart(int cp) {
        return Character.isLetter(cp) || Character.isDigit(cp) || cp == '_' || cp == '-';
    }

    private static boolean hasLetter(String s) {
        return s.codePoints().anyMatch(Character::isLetter);
    }
}
