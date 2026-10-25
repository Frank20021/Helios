package io.helios.crawler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;

public final class Git {
    public static final String CLONE_PREFIX = "helios-clone-";
    private static final Pattern CREDENTIAL_URL = Pattern.compile("://[^@/\\s]+@");

    private Git() {
    }

    public static Path createCloneDir(String base) throws IOException {
        Path root = base == null || base.isBlank() ? Path.of(System.getProperty("java.io.tmpdir")) : Path.of(base);
        Files.createDirectories(root);
        return Files.createTempDirectory(root, CLONE_PREFIX);
    }

    public static void removeCloneDir(Path dir) throws IOException {
        if (dir == null) {
            return;
        }
        if (!dir.getFileName().toString().contains(CLONE_PREFIX)) {
            throw new IOException("refusing to delete " + dir);
        }
        deleteRecursive(dir);
    }

    public static void cloneRepo(String sourceUrl, Path dest, Duration timeout) {
        runGit(null, timeout, "clone", "--depth", "1", "--single-branch", "--no-recurse-submodules", "--", sourceUrl, dest.toString());
    }

    public static void cloneLocal(String sourceDir, Path dest, Duration timeout) {
        runGit(null, timeout, "clone", "--depth", "1", "--single-branch", "--no-recurse-submodules", "--", sourceDir, dest.toString());
    }

    public static String headCommit(Path repoDir) {
        return runGit(repoDir, Duration.ZERO, "rev-parse", "HEAD");
    }

    public static String headBranch(Path repoDir) {
        String out = runGit(repoDir, Duration.ZERO, "rev-parse", "--abbrev-ref", "HEAD");
        return "HEAD".equals(out) ? "" : out;
    }

    public static void initFixtureRepo(Path srcDir, Path destDir) throws IOException {
        Files.createDirectories(destDir);
        runGit(destDir, Duration.ZERO, "init");
        copyPath(srcDir, destDir);
        runGit(destDir, Duration.ZERO, "add", "-A");
        runGit(destDir, Duration.ZERO, "-c", "user.name=Helios", "-c", "user.email=helios@test.local",
                "commit", "-m", "fixture");
    }

    static String runGit(Path dir, Duration timeout, String... args) {
        List<String> command = new ArrayList<>();
        command.add("git");
        command.addAll(List.of(args));
        ProcessBuilder pb = new ProcessBuilder(command);
        if (dir != null) {
            pb.directory(dir.toFile());
        }
        pb.environment().clear();
        pb.environment().put("PATH", System.getenv("PATH") == null ? "" : System.getenv("PATH"));
        pb.environment().put("GIT_TERMINAL_PROMPT", "0");
        pb.environment().put("GIT_CONFIG_NOSYSTEM", "1");
        pb.environment().put("LC_ALL", "C");
        pb.environment().put("GIT_ASKPASS", "true");
        pb.redirectErrorStream(false);
        try {
            Process p = pb.start();
            long ms = timeout == null || timeout.isZero() || timeout.isNegative() ? 180_000 : timeout.toMillis();
            byte[] out = p.getInputStream().readAllBytes();
            byte[] err = p.getErrorStream().readAllBytes();
            boolean finished = p.waitFor(ms, TimeUnit.MILLISECONDS);
            if (!finished) {
                p.destroyForcibly();
                throw new CrawlerException("git " + args[0] + ": timeout");
            }
            if (p.exitValue() != 0) {
                String msg = sanitize(new String(err, StandardCharsets.UTF_8).trim());
                if (msg.isEmpty()) {
                    msg = "exit " + p.exitValue();
                }
                throw new CrawlerException("git " + args[0] + ": " + msg);
            }
            return new String(out, StandardCharsets.UTF_8).trim();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new CrawlerException("git " + args[0] + ": interrupted", e);
        } catch (IOException e) {
            throw new CrawlerException("git is required to clone repositories: " + e.getMessage(), e);
        }
    }

    static String sanitize(String s) {
        return CREDENTIAL_URL.matcher(s).replaceAll("://***@");
    }

    static void copyPath(Path src, Path dest) throws IOException {
        if (Files.isSymbolicLink(src)) {
            return;
        }
        if (Files.isDirectory(src)) {
            Files.createDirectories(dest);
            try (var stream = Files.list(src)) {
                for (Path child : stream.toList()) {
                    if (".git".equals(child.getFileName().toString()) && Files.isDirectory(dest) && Files.exists(dest.resolve(".git"))) {
                        continue;
                    }
                    copyPath(child, dest.resolve(child.getFileName()));
                }
            }
            return;
        }
        Files.copy(src, dest);
    }

    static void deleteRecursive(Path dir) throws IOException {
        if (!Files.exists(dir)) {
            return;
        }
        try (var walk = Files.walk(dir)) {
            List<Path> paths = walk.sorted((a, b) -> b.getNameCount() - a.getNameCount()).toList();
            for (Path p : paths) {
                Files.deleteIfExists(p);
            }
        }
    }
}
