package io.helios.snippet;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public final class Snippet {
    public static final int DEFAULT_WINDOW = 240;

    private Snippet() {
    }

    public static String highlight(String content, List<String> terms, int window) {
        if (window <= 0) {
            window = DEFAULT_WINDOW;
        }
        if (content == null) {
            return "";
        }
        content = content.replace("\r\n", "\n");
        if (content.isBlank()) {
            return "";
        }
        List<int[]> ranges = matchRanges(content, terms);
        int[] bounds = windowBounds(content, ranges, window);
        int start = bounds[0];
        int end = bounds[1];
        String piece = content.substring(start, end);
        String prefix = start > 0 ? "…" : "";
        String suffix = end < content.length() ? "…" : "";
        return prefix + wrapMarks(piece, shiftRanges(ranges, start, end)) + suffix;
    }

    private static List<int[]> matchRanges(String content, List<String> terms) {
        String lower = content.toLowerCase(Locale.ROOT);
        List<int[]> ranges = new ArrayList<>();
        if (terms == null) {
            return ranges;
        }
        for (String term : terms) {
            if (term == null) {
                continue;
            }
            String needle = term.trim().toLowerCase(Locale.ROOT);
            if (needle.isEmpty()) {
                continue;
            }
            int from = 0;
            while (true) {
                int idx = lower.indexOf(needle, from);
                if (idx < 0) {
                    break;
                }
                ranges.add(new int[]{idx, idx + needle.length()});
                from = idx + needle.length();
            }
        }
        return mergeRanges(ranges);
    }

    private static List<int[]> mergeRanges(List<int[]> in) {
        if (in.isEmpty()) {
            return List.of();
        }
        in.sort(Comparator.comparingInt(a -> a[0]));
        List<int[]> out = new ArrayList<>();
        out.add(in.getFirst());
        for (int i = 1; i < in.size(); i++) {
            int[] r = in.get(i);
            int[] last = out.getLast();
            if (r[0] < last[1]) {
                if (r[1] > last[1]) {
                    last[1] = r[1];
                }
            } else {
                out.add(r);
            }
        }
        return out;
    }

    private static int[] windowBounds(String content, List<int[]> ranges, int window) {
        int n = content.length();
        if (n <= window) {
            return new int[]{0, n};
        }
        int center = ranges.isEmpty() ? 0 : ranges.getFirst()[0];
        int start = center - window / 4;
        if (start < 0) {
            start = 0;
        }
        start = runeStart(content, start);
        int end = start + window;
        if (end > n) {
            end = n;
            start = runeStart(content, n - window);
        }
        end = runeEnd(content, end);
        return new int[]{start, end};
    }

    private static int runeStart(String s, int i) {
        if (i <= 0) {
            return 0;
        }
        if (i >= s.length()) {
            return s.length();
        }
        while (i > 0 && !isCharStart(s, i)) {
            i--;
        }
        return i;
    }

    private static int runeEnd(String s, int i) {
        if (i >= s.length()) {
            return s.length();
        }
        if (i <= 0) {
            return 0;
        }
        while (i < s.length() && !isCharStart(s, i)) {
            i++;
        }
        return i;
    }

    private static boolean isCharStart(String s, int i) {
        return i == 0 || !Character.isLowSurrogate(s.charAt(i));
    }

    private static List<int[]> shiftRanges(List<int[]> ranges, int start, int end) {
        List<int[]> out = new ArrayList<>();
        for (int[] r : ranges) {
            int a = r[0];
            int b = r[1];
            if (b <= start || a >= end) {
                continue;
            }
            if (a < start) {
                a = start;
            }
            if (b > end) {
                b = end;
            }
            out.add(new int[]{a - start, b - start});
        }
        return out;
    }

    private static String wrapMarks(String s, List<int[]> ranges) {
        if (ranges.isEmpty()) {
            return s;
        }
        StringBuilder b = new StringBuilder();
        int cursor = 0;
        for (int[] r : ranges) {
            if (r[0] < cursor || r[1] > s.length() || r[0] >= r[1]) {
                continue;
            }
            b.append(s, cursor, r[0]);
            b.append("<mark>");
            b.append(s, r[0], r[1]);
            b.append("</mark>");
            cursor = r[1];
        }
        b.append(s.substring(cursor));
        return b.toString();
    }

    @SuppressWarnings("unused")
    private static final byte[] UTF8 = StandardCharsets.UTF_8.name().getBytes(StandardCharsets.UTF_8);
}
