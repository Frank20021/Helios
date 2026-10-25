package io.helios.crawler;

import io.helios.parser.Parser;
import io.helios.parser.Passage;
import io.helios.tokenizer.TokenizerOptions;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class ProcessRoot {
    private ProcessRoot() {
    }

    public record Document(String relPath, String language, String contentHash, long sizeBytes, List<Passage> passages) {
    }

    public record FileFailure(String path, String reason) {
    }

    public record Result(List<Document> documents, int filesDiscovered, int filesProcessed, int filesFailed,
                         List<FileFailure> failures) {
    }

    public static Result process(Path root, Filter filter, TokenizerOptions tok) throws IOException {
        List<Discover.DiscoveredFile> files = Discover.walk(root, filter);
        List<Document> documents = new ArrayList<>();
        List<FileFailure> failures = new ArrayList<>();
        int failed = 0;
        int processed = 0;
        for (Discover.DiscoveredFile file : files) {
            try {
                Discover.TextFile text = Discover.readText(file.absPath(), filter.maxFileSizeBytes());
                documents.add(new Document(file.relPath(), file.language(), text.hash(), file.sizeBytes(),
                        Parser.parse(file.relPath(), text.content(), tok)));
                processed++;
            } catch (Exception e) {
                failed++;
                failures.add(new FileFailure(file.relPath(), readReason(e)));
            }
        }
        return new Result(documents, files.size(), processed, failed, failures);
    }

    private static String readReason(Exception err) {
        String msg = err.getMessage() == null ? "" : err.getMessage();
        if (msg.contains(Discover.ERR_BINARY)) {
            return "binary file";
        }
        if (msg.contains(Discover.ERR_UTF8)) {
            return "invalid utf-8";
        }
        return "unreadable file";
    }
}
