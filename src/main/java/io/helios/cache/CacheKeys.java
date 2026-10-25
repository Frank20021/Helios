package io.helios.cache;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

public final class CacheKeys {
    private CacheKeys() {
    }

    public static String key(QueryCache.QueryKey q) {
        String text = q.text() == null ? "" : q.text().trim().toLowerCase(Locale.ROOT);
        String mode = q.mode() == null ? "" : q.mode().trim().toLowerCase(Locale.ROOT);
        if (mode.isEmpty()) {
            mode = "lexical";
        }
        String lang = q.language() == null ? "" : q.language().trim().toLowerCase(Locale.ROOT);
        String ext = q.fileExtension() == null ? "" : q.fileExtension().trim().toLowerCase(Locale.ROOT);
        if (!ext.isEmpty() && !ext.startsWith(".")) {
            ext = "." + ext;
        }
        int page = q.page() < 1 ? 1 : q.page();
        int size = q.pageSize() < 1 ? 10 : q.pageSize();
        String alpha = q.hasAlpha() ? String.format(Locale.US, "%.4f", q.alpha()) : "default";
        String repo = q.repositoryId() == null ? "" : q.repositoryId();
        String canonical = String.join("\n", text, mode, alpha, repo, lang, ext, Integer.toString(page), Integer.toString(size));
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return QueryCache.PREFIX + HexFormat.of().formatHex(sum);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
