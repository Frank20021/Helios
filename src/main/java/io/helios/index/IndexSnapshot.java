package io.helios.index;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import io.helios.tokenizer.TokenizerOptions;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class IndexSnapshot {
    public static final int VERSION = 1;
    public static final String NOT_FOUND = "index snapshot not found";
    public static final String MISMATCH = "index snapshot does not match";

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .findAndRegisterModules()
            .disable(SerializationFeature.FAIL_ON_EMPTY_BEANS);

    private IndexSnapshot() {
    }

    public static void save(InvertedIndex idx, String path) throws IOException {
        if (path == null || path.isBlank()) {
            return;
        }
        Path dest = Path.of(path);
        Path parent = dest.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        InvertedIndex.Snapshot snap = idx.snapshot();
        Path tmp = Files.createTempFile(parent == null ? Path.of(".") : parent, "helios-index-", ".tmp");
        try {
            MAPPER.writeValue(tmp.toFile(), snap);
            Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            Files.deleteIfExists(tmp);
            throw e;
        }
    }

    public static void load(InvertedIndex idx, String path) throws IOException {
        if (path == null || path.isBlank()) {
            throw new IOException(NOT_FOUND);
        }
        Path dest = Path.of(path);
        if (!Files.exists(dest)) {
            throw new IOException(NOT_FOUND);
        }
        InvertedIndex.Snapshot snap = MAPPER.readValue(dest.toFile(), InvertedIndex.Snapshot.class);
        if (snap.version() != VERSION) {
            throw new IOException(MISMATCH + ": version " + snap.version());
        }
        if (!optionsKey(idx.tokenizerOptions()).equals(snap.optionsKey())) {
            throw new IOException(MISMATCH + ": tokenizer options changed");
        }
        idx.loadSnapshot(snap);
    }

    public static String optionsKey(TokenizerOptions opts) {
        List<String> words = new ArrayList<>(opts.stopWordSet());
        words.sort(Comparator.naturalOrder());
        return opts.minLength() + ":" + String.join(",", words);
    }
}
