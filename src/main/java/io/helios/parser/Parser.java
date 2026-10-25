package io.helios.parser;

import io.helios.tokenizer.Tokenizer;
import io.helios.tokenizer.TokenizerOptions;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class Parser {
    static final int CHUNK_LINES = 40;
    static final int CHUNK_OVERLAP = 8;

    private Parser() {
    }

    public static List<Passage> parse(String relPath, String content, TokenizerOptions tok) {
        String language = Languages.fromPath(relPath);
        if (content == null) {
            return List.of();
        }
        content = content.replace("\r\n", "\n");
        if (content.isBlank()) {
            return List.of();
        }
        List<Passage> passages = switch (language) {
            case Languages.MARKDOWN -> parseMarkdown(content, tok);
            case Languages.TEXT -> parseText(content, tok);
            default -> parseCode(language, content, tok);
        };
        if (passages.isEmpty()) {
            passages = chunkLinesWindow(language, content, tok);
        }
        return passages;
    }

    static Passage newPassage(String language, String name, String kind, String content, int start, int end,
                              TokenizerOptions tok) {
        if (start < 1) {
            start = 1;
        }
        if (end < start) {
            end = start;
        }
        return new Passage(name, kind, start, end, content, language,
                Tokenizer.surfaceCount(Tokenizer.tokenize(content, tok)));
    }

    static List<Passage> parseMarkdown(String content, TokenizerOptions tok) {
        Matcher m = HEADING.matcher(content);
        List<int[]> locs = new ArrayList<>();
        List<String> names = new ArrayList<>();
        while (m.find()) {
            locs.add(new int[]{m.start(), m.end(), m.start(2), m.end(2)});
            names.add(content.substring(m.start(2), m.end(2)).trim());
        }
        if (locs.isEmpty()) {
            return parseText(content, tok);
        }
        List<Passage> passages = new ArrayList<>();
        for (int i = 0; i < locs.size(); i++) {
            int start = locs.get(i)[0];
            int end = i + 1 < locs.size() ? locs.get(i + 1)[0] : content.length();
            if (end > 0 && content.charAt(end - 1) == '\n') {
                end--;
            }
            int startLine = lineAt(content, start);
            int endLine = lineAt(content, Math.max(end - 1, start));
            String body = content.substring(start, end);
            if (body.endsWith("\n")) {
                body = body.substring(0, body.length() - 1);
            }
            passages.add(newPassage(Languages.MARKDOWN, names.get(i), Passage.TYPE_HEADING, body, startLine, endLine, tok));
        }
        return passages;
    }

    static List<Passage> parseText(String content, TokenizerOptions tok) {
        String[] parts = PARA.split(content);
        List<Passage> passages = new ArrayList<>();
        int cursor = 0;
        for (String part : parts) {
            int idx = content.indexOf(part, cursor);
            if (idx < 0) {
                continue;
            }
            int start = idx;
            int end = start + part.length();
            cursor = end;
            if (part.isBlank()) {
                continue;
            }
            int startLine = lineAt(content, start);
            int endLine = lineAt(content, Math.max(end - 1, start));
            passages.add(newPassage(Languages.TEXT, "", Passage.TYPE_CHUNK, part.trim(), startLine, endLine, tok));
        }
        if (passages.isEmpty()) {
            return chunkLinesWindow(Languages.TEXT, content, tok);
        }
        return passages;
    }

    static List<Passage> chunkLinesWindow(String language, String content, TokenizerOptions tok) {
        String[] lines = content.split("\n", -1);
        if (lines.length == 0) {
            return List.of();
        }
        int step = CHUNK_LINES - CHUNK_OVERLAP;
        if (step < 1) {
            step = CHUNK_LINES;
        }
        List<Passage> passages = new ArrayList<>();
        for (int start = 0; start < lines.length; start += step) {
            int end = Math.min(start + CHUNK_LINES, lines.length);
            String block = String.join("\n", java.util.Arrays.copyOfRange(lines, start, end));
            if (block.isBlank()) {
                if (end == lines.length) {
                    break;
                }
                continue;
            }
            passages.add(newPassage(language, "", Passage.TYPE_CHUNK, block, start + 1, end, tok));
            if (end == lines.length) {
                break;
            }
        }
        return passages;
    }

    static List<Passage> parseCode(String language, String content, TokenizerOptions tok) {
        List<SymbolRule> rules = languageRules(language);
        if (rules.isEmpty()) {
            return List.of();
        }
        List<Candidate> found = Languages.PYTHON.equals(language)
                ? pythonCandidates(content, rules)
                : regexCandidates(content, rules);
        if (found.isEmpty()) {
            return List.of();
        }
        found.sort(Comparator.comparingInt(Candidate::start).thenComparing(Comparator.comparingInt(Candidate::end).reversed()));
        List<Passage> passages = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Candidate c : found) {
            int startLine = lineAt(content, c.start());
            int endLine = lineAt(content, c.end());
            String key = c.kind() + "\0" + c.name() + "\0" + startLine;
            if (!seen.add(key)) {
                continue;
            }
            passages.add(newPassage(language, c.name(), c.kind(), snippet(content, startLine, endLine), startLine, endLine, tok));
        }
        return passages;
    }

    private static List<Candidate> regexCandidates(String content, List<SymbolRule> rules) {
        List<Candidate> found = new ArrayList<>();
        for (SymbolRule rule : rules) {
            Matcher m = rule.expr().matcher(content);
            while (m.find()) {
                String name = m.group(rule.name());
                int start = m.start();
                int end = m.end();
                if (rule.brace()) {
                    int open = firstOpenBrace(content, m.end() - 1);
                    if (open >= 0) {
                        int close = matchBrace(content, open);
                        if (close >= 0) {
                            end = close;
                        }
                    }
                } else {
                    int open = firstOpenBrace(content, m.end());
                    if (open >= 0 && open - m.end() < 80) {
                        int close = matchBrace(content, open);
                        if (close >= 0) {
                            end = close;
                        }
                    }
                }
                found.add(new Candidate(name, rule.kind(), start, end));
            }
        }
        return found;
    }

    private static List<Candidate> pythonCandidates(String content, List<SymbolRule> rules) {
        String[] lines = content.split("\n", -1);
        List<Candidate> found = new ArrayList<>();
        int offset = 0;
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            for (SymbolRule rule : rules) {
                Matcher m = rule.expr().matcher(line);
                if (!m.find()) {
                    continue;
                }
                String name = m.group(rule.name());
                int endLine = pythonBlockEndLine(lines, i);
                int end = lineEnd(content, endLine + 1);
                if (end < offset + line.length()) {
                    end = offset + line.length();
                }
                String kind = rule.kind();
                if (Passage.TYPE_FUNCTION.equals(kind) && leadingIndent(line) > 0) {
                    kind = Passage.TYPE_METHOD;
                }
                found.add(new Candidate(name, kind, offset, end));
            }
            offset += line.length() + 1;
        }
        return found;
    }

    static int lineAt(String content, int offset) {
        if (offset <= 0) {
            return 1;
        }
        if (offset > content.length()) {
            offset = content.length();
        }
        int n = 1;
        for (int i = 0; i < offset; i++) {
            if (content.charAt(i) == '\n') {
                n++;
            }
        }
        return n;
    }

    static int lineStart(String content, int line) {
        if (line <= 1) {
            return 0;
        }
        int seen = 1;
        for (int i = 0; i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                seen++;
                if (seen == line) {
                    return i + 1;
                }
            }
        }
        return content.length();
    }

    static int lineEnd(String content, int line) {
        int start = lineStart(content, line);
        int idx = content.indexOf('\n', start);
        return idx >= 0 ? idx : content.length();
    }

    static String snippet(String content, int startLine, int endLine) {
        int start = lineStart(content, startLine);
        int end = lineEnd(content, endLine);
        if (end < start) {
            return "";
        }
        return content.substring(start, end);
    }

    static int matchBrace(String content, int openAt) {
        if (openAt < 0 || openAt >= content.length() || content.charAt(openAt) != '{') {
            return -1;
        }
        int depth = 0;
        char str = 0;
        boolean raw = false;
        boolean lineComment = false;
        boolean blockComment = false;
        boolean escape = false;
        for (int i = openAt; i < content.length(); i++) {
            char c = content.charAt(i);
            char next = i + 1 < content.length() ? content.charAt(i + 1) : 0;
            if (lineComment) {
                if (c == '\n') {
                    lineComment = false;
                }
                continue;
            }
            if (blockComment) {
                if (c == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (str != 0) {
                if (raw) {
                    if (str == '`' && c == '`') {
                        str = 0;
                        raw = false;
                    }
                    continue;
                }
                if (escape) {
                    escape = false;
                    continue;
                }
                if (c == '\\' && str != '`') {
                    escape = true;
                    continue;
                }
                if (c == str) {
                    str = 0;
                }
                continue;
            }
            if (c == '/' && next == '/') {
                lineComment = true;
                i++;
                continue;
            }
            if (c == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (c == '"' || c == '\'') {
                str = c;
                continue;
            }
            if (c == '`') {
                str = c;
                raw = true;
                continue;
            }
            if (c == '{') {
                depth++;
                continue;
            }
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    static int firstOpenBrace(String content, int from) {
        for (int i = from; i < content.length(); i++) {
            if (content.charAt(i) == '{') {
                return i;
            }
            if (content.charAt(i) == '\n') {
                if (i > from && !content.substring(from, i).isBlank()
                        && i + 1 < content.length() && content.charAt(i + 1) == '\n') {
                    return -1;
                }
            }
        }
        return -1;
    }

    static int pythonBlockEndLine(String[] lines, int start) {
        int headerIndent = leadingIndent(lines[start]);
        int end = start;
        for (int i = start + 1; i < lines.length; i++) {
            if (lines[i].isBlank()) {
                end = i;
                continue;
            }
            if (leadingIndent(lines[i]) <= headerIndent) {
                return end;
            }
            end = i;
        }
        return end;
    }

    static int leadingIndent(String line) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            char r = line.charAt(i);
            if (r == ' ') {
                n++;
            } else if (r == '\t') {
                n += 4;
            } else {
                return n;
            }
        }
        return n;
    }

    private static List<SymbolRule> languageRules(String language) {
        return switch (language) {
            case Languages.GO -> List.of(
                    new SymbolRule(Passage.TYPE_FUNCTION, Pattern.compile("(?m)^func[ \\t]+(?:\\([^)]*\\)[ \\t]+)?([A-Za-z_][A-Za-z0-9_]*)[ \\t]*\\("), 1, true),
                    new SymbolRule(Passage.TYPE_STRUCT, Pattern.compile("(?m)^type[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]+struct\\b"), 1, true),
                    new SymbolRule(Passage.TYPE_INTERFACE, Pattern.compile("(?m)^type[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]+interface\\b"), 1, true)
            );
            case Languages.PYTHON -> List.of(
                    new SymbolRule(Passage.TYPE_CLASS, Pattern.compile("(?m)^([ \\t]*)class[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 2, false),
                    new SymbolRule(Passage.TYPE_FUNCTION, Pattern.compile("(?m)^([ \\t]*)(?:async[ \\t]+)?def[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 2, false)
            );
            case Languages.JAVA -> List.of(
                    new SymbolRule(Passage.TYPE_CLASS, Pattern.compile("(?m)^[ \\t]*(?:public|private|protected)?[ \\t]*(?:abstract[ \\t]+)?(?:final[ \\t]+)?class[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                    new SymbolRule(Passage.TYPE_INTERFACE, Pattern.compile("(?m)^[ \\t]*(?:public|private|protected)?[ \\t]*interface[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                    new SymbolRule(Passage.TYPE_ENUM, Pattern.compile("(?m)^[ \\t]*(?:public|private|protected)?[ \\t]*enum[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                    new SymbolRule(Passage.TYPE_METHOD, Pattern.compile("(?m)^[ \\t]*(?:public|private|protected)[ \\t][^{;]*\\b([A-Za-z_][A-Za-z0-9_]*)[ \\t]*\\([^;]*\\)[ \\t]*\\{"), 1, true)
            );
            case Languages.JAVASCRIPT, Languages.TYPESCRIPT, Languages.TSX -> jsRules();
            case Languages.CPP -> List.of(
                    new SymbolRule(Passage.TYPE_CLASS, Pattern.compile("(?m)^[ \\t]*class[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                    new SymbolRule(Passage.TYPE_STRUCT, Pattern.compile("(?m)^[ \\t]*struct[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                    new SymbolRule(Passage.TYPE_FUNCTION, Pattern.compile("(?m)^[\\w:<>\\*& ]+\\s+([A-Za-z_][A-Za-z0-9_]*)\\s*\\([^;]*\\)\\s*\\{"), 1, true)
            );
            case Languages.C -> List.of(
                    new SymbolRule(Passage.TYPE_CLASS, Pattern.compile("(?m)^[ \\t]*class[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                    new SymbolRule(Passage.TYPE_STRUCT, Pattern.compile("(?m)^[ \\t]*struct[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true)
            );
            default -> List.of();
        };
    }

    private static List<SymbolRule> jsRules() {
        return List.of(
                new SymbolRule(Passage.TYPE_CLASS, Pattern.compile("(?m)^[ \\t]*(?:export[ \\t]+)?class[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                new SymbolRule(Passage.TYPE_INTERFACE, Pattern.compile("(?m)^[ \\t]*(?:export[ \\t]+)?interface[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                new SymbolRule(Passage.TYPE_FUNCTION, Pattern.compile("(?m)^[ \\t]*(?:export[ \\t]+)?(?:async[ \\t]+)?function[ \\t]+([A-Za-z_][A-Za-z0-9_]*)"), 1, true),
                new SymbolRule(Passage.TYPE_FUNCTION, Pattern.compile("(?m)^[ \\t]*(?:export[ \\t]+)?(?:const|let|var)[ \\t]+([A-Za-z_][A-Za-z0-9_]*)[ \\t]*=[ \\t]*(?:async[ \\t]*)?\\("), 1, false)
        );
    }

    private static final Pattern HEADING = Pattern.compile("(?m)^(#{1,6})[ \\t]+(.+?)\\s*$");
    private static final Pattern PARA = Pattern.compile("\\n\\s*\\n");

    private record SymbolRule(String kind, Pattern expr, int name, boolean brace) {
    }

    private record Candidate(String name, String kind, int start, int end) {
    }
}
