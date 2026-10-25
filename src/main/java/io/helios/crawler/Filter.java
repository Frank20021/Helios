package io.helios.crawler;

import io.helios.config.CrawlerSettings;
import io.helios.parser.Languages;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public record Filter(
        Set<String> ignoredDirectories,
        Set<String> supportedExtensions,
        Set<String> lockFiles,
        long maxFileSizeBytes,
        long maxRepoSizeBytes,
        int maxFiles
) {
    public static List<String> defaultLockFiles() {
        return List.of(
                "package-lock.json", "yarn.lock", "pnpm-lock.yaml", "bun.lock", "bun.lockb",
                "go.sum", "Cargo.lock", "Gemfile.lock", "composer.lock", "poetry.lock", "Pipfile.lock"
        );
    }

    public static Filter fromConfig(CrawlerSettings cfg) {
        return new Filter(
                toLowerSet(cfg.ignoredDirectories()),
                extSet(cfg.supportedExtensions()),
                toLowerSet(defaultLockFiles()),
                cfg.maxFileSizeBytes(),
                cfg.maxRepoSizeBytes(),
                cfg.maxFiles()
        );
    }

    public boolean ignoreDir(String name) {
        return ignoredDirectories.contains(name.toLowerCase(Locale.ROOT));
    }

    public boolean supportedExt(String name) {
        int dot = name.lastIndexOf('.');
        String ext = dot < 0 ? "" : name.substring(dot).toLowerCase(Locale.ROOT);
        return supportedExtensions.contains(ext);
    }

    public boolean lockFile(String name) {
        return lockFiles.contains(name.toLowerCase(Locale.ROOT));
    }

    public String language(String relPath) {
        return Languages.fromPath(relPath);
    }

    private static Set<String> toLowerSet(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values == null) {
            return out;
        }
        for (String v : values) {
            if (v == null) {
                continue;
            }
            v = v.trim().toLowerCase(Locale.ROOT);
            if (!v.isEmpty()) {
                out.add(v);
            }
        }
        return out;
    }

    private static Set<String> extSet(List<String> values) {
        Set<String> out = new HashSet<>();
        if (values == null) {
            return out;
        }
        for (String v : values) {
            if (v == null) {
                continue;
            }
            v = v.trim().toLowerCase(Locale.ROOT);
            if (v.isEmpty()) {
                continue;
            }
            if (!v.startsWith(".")) {
                v = "." + v;
            }
            out.add(v);
        }
        return out;
    }
}
