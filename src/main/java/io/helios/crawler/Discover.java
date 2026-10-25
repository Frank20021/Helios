package io.helios.crawler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public final class Discover {
    public static final String ERR_REPO_TOO_LARGE = "repository exceeds the configured size limit";
    public static final String ERR_TOO_MANY_FILES = "repository exceeds the configured file limit";
    public static final String ERR_BINARY = "binary file";
    public static final String ERR_UTF8 = "invalid utf-8";
    public static final String ERR_PATH_ESCAPE = "path escapes repository root";

    private Discover() {
    }

    public static List<DiscoveredFile> walk(Path root, Filter filter) throws IOException {
        Path absRoot = root.toAbsolutePath().normalize();
        if (Files.isSymbolicLink(absRoot)) {
            throw new CrawlerException("repository root must not be a symlink");
        }
        List<DiscoveredFile> out = new ArrayList<>();
        long[] total = {0};
        Files.walkFileTree(absRoot, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
                stayInside(absRoot, dir);
                if (Files.isSymbolicLink(dir)) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                if (!dir.equals(absRoot) && filter.ignoreDir(dir.getFileName().toString())) {
                    return FileVisitResult.SKIP_SUBTREE;
                }
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
                stayInside(absRoot, file);
                if (Files.isSymbolicLink(file)) {
                    return FileVisitResult.CONTINUE;
                }
                String name = file.getFileName().toString();
                if (filter.lockFile(name) || !filter.supportedExt(name)) {
                    return FileVisitResult.CONTINUE;
                }
                long size = attrs.size();
                if (size > filter.maxFileSizeBytes()) {
                    return FileVisitResult.CONTINUE;
                }
                Path rel = absRoot.relativize(file);
                if (rel.startsWith("..") || rel.isAbsolute()) {
                    throw new CrawlerException(ERR_PATH_ESCAPE + ": " + rel);
                }
                String relPath = rel.toString().replace('\\', '/');
                if (filter.language(relPath).isEmpty()) {
                    return FileVisitResult.CONTINUE;
                }
                if (out.size() + 1 > filter.maxFiles()) {
                    throw new CrawlerException(ERR_TOO_MANY_FILES);
                }
                if (total[0] + size > filter.maxRepoSizeBytes()) {
                    throw new CrawlerException(ERR_REPO_TOO_LARGE);
                }
                total[0] += size;
                out.add(new DiscoveredFile(relPath, file, filter.language(relPath), size));
                return FileVisitResult.CONTINUE;
            }
        });
        return out;
    }

    public static TextFile readText(Path path, long maxSize) throws IOException {
        byte[] data = Files.readAllBytes(path);
        if (data.length > maxSize) {
            throw new CrawlerException("file exceeds max size");
        }
        for (byte b : data) {
            if (b == 0) {
                throw new CrawlerException(ERR_BINARY);
            }
        }
        String content = new String(data, StandardCharsets.UTF_8);
        if (!StandardCharsets.UTF_8.newEncoder().canEncode(content) || !isValidUtf8(data)) {
            throw new CrawlerException(ERR_UTF8);
        }
        try {
            byte[] sum = MessageDigest.getInstance("SHA-256").digest(data);
            return new TextFile(content, HexFormat.of().formatHex(sum));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static boolean isValidUtf8(byte[] data) {
        try {
            var decoder = StandardCharsets.UTF_8.newDecoder();
            decoder.onMalformedInput(java.nio.charset.CodingErrorAction.REPORT);
            decoder.decode(java.nio.ByteBuffer.wrap(data));
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private static void stayInside(Path root, Path path) {
        Path rel = root.relativize(path.toAbsolutePath().normalize());
        if (rel.toString().isEmpty() || ".".equals(rel.toString())) {
            return;
        }
        if (rel.startsWith("..") || rel.isAbsolute()) {
            throw new CrawlerException(ERR_PATH_ESCAPE + ": " + rel);
        }
    }

    public record DiscoveredFile(String relPath, Path absPath, String language, long sizeBytes) {
    }

    public record TextFile(String content, String hash) {
    }
}
