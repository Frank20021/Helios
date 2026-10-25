package io.helios.crawler;

import java.net.URI;
import java.util.List;
import java.util.Locale;

public final class RepoUrls {
    public static final String ERR_INVALID = "invalid repository url";
    public static final String ERR_INSECURE = "repository url must use https";
    public static final String ERR_HOST = "unsupported repository host";
    public static final String ERR_CREDENTIALS = "repository url must not contain credentials";

    private RepoUrls() {
    }

    public static RepoRef parseUrl(String raw, List<String> allowedHosts) {
        raw = raw == null ? "" : raw.trim();
        if (raw.isEmpty()) {
            throw new CrawlerException(ERR_INVALID + ": empty");
        }
        String lower = raw.toLowerCase(Locale.ROOT);
        if (raw.startsWith("git@") || lower.startsWith("ssh://")) {
            throw new CrawlerException(ERR_INSECURE);
        }
        URI parsed;
        try {
            parsed = URI.create(raw);
        } catch (IllegalArgumentException e) {
            throw new CrawlerException(ERR_INVALID + ": " + e.getMessage(), e);
        }
        if (!"https".equalsIgnoreCase(parsed.getScheme())) {
            throw new CrawlerException(ERR_INSECURE);
        }
        if (parsed.getUserInfo() != null && !parsed.getUserInfo().isEmpty()) {
            throw new CrawlerException(ERR_CREDENTIALS);
        }
        if ((parsed.getQuery() != null && !parsed.getQuery().isEmpty())
                || (parsed.getFragment() != null && !parsed.getFragment().isEmpty())) {
            throw new CrawlerException(ERR_INVALID + ": query and fragment are not allowed");
        }
        String host = parsed.getHost() == null ? "" : parsed.getHost().toLowerCase(Locale.ROOT);
        if (!allowedHost(host, allowedHosts)) {
            throw new CrawlerException(ERR_HOST + ": " + host);
        }
        String path = parsed.getPath() == null ? "" : parsed.getPath();
        String clean = java.nio.file.Path.of("/" + trimSlashes(path)).normalize().toString().replace('\\', '/');
        String[] parts = trimSlashes(clean).split("/");
        if (parts.length != 2 || parts[0].isEmpty() || parts[1].isEmpty() || "..".equals(parts[0]) || "..".equals(parts[1])) {
            throw new CrawlerException(ERR_INVALID + ": expected /owner/repository");
        }
        String name = parts[1].endsWith(".git") ? parts[1].substring(0, parts[1].length() - 4) : parts[1];
        if (!safeName(parts[0]) || !safeName(name)) {
            throw new CrawlerException(ERR_INVALID + ": owner or repository name is invalid");
        }
        String normalized = "https://" + host + "/" + parts[0] + "/" + name;
        return new RepoRef(parts[0], name, host, normalized);
    }

    public static boolean isValidation(Throwable err) {
        if (!(err instanceof CrawlerException)) {
            return false;
        }
        String msg = err.getMessage();
        return msg != null && (msg.contains(ERR_INVALID) || msg.contains(ERR_INSECURE)
                || msg.contains(ERR_HOST) || msg.contains(ERR_CREDENTIALS));
    }

    private static boolean allowedHost(String host, List<String> allowed) {
        if (allowed == null) {
            return false;
        }
        for (String candidate : allowed) {
            if (candidate != null && host.equalsIgnoreCase(candidate.trim())) {
                return true;
            }
        }
        return false;
    }

    private static boolean safeName(String s) {
        if (s == null || s.isEmpty() || ".".equals(s) || "..".equals(s)) {
            return false;
        }
        for (int i = 0; i < s.length(); i++) {
            char r = s.charAt(i);
            if ((r >= 'a' && r <= 'z') || (r >= 'A' && r <= 'Z') || (r >= '0' && r <= '9')
                    || r == '.' || r == '_' || r == '-') {
                continue;
            }
            return false;
        }
        return true;
    }

    private static String trimSlashes(String s) {
        int a = 0;
        int b = s.length();
        while (a < b && s.charAt(a) == '/') {
            a++;
        }
        while (b > a && s.charAt(b - 1) == '/') {
            b--;
        }
        return s.substring(a, b);
    }
}
