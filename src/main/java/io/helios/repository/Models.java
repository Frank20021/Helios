package io.helios.repository;

import java.time.Instant;
import java.util.UUID;

public final class Models {
    private Models() {
    }

    public static final String REPO_QUEUED = "queued";
    public static final String REPO_CLONING = "cloning";
    public static final String REPO_INDEXING = "indexing";
    public static final String REPO_INDEXED = "indexed";
    public static final String REPO_FAILED = "failed";

    public static final String JOB_QUEUED = "queued";
    public static final String JOB_RUNNING = "running";
    public static final String JOB_SUCCEEDED = "succeeded";
    public static final String JOB_FAILED = "failed";

    public static class Repository {
        public UUID id;
        public String name = "";
        public String owner = "";
        public String sourceUrl = "";
        public String defaultBranch = "main";
        public String latestCommitHash = "";
        public String status = REPO_QUEUED;
        public int documentCount;
        public Instant indexedAt;
        public Instant createdAt;
        public Instant updatedAt;
    }

    public static class Document {
        public UUID id;
        public UUID repositoryId;
        public String filePath = "";
        public String language = "";
        public String contentHash = "";
        public long sizeBytes;
        public Instant indexedAt;
    }

    public static class Passage {
        public UUID id;
        public UUID documentId;
        public String symbolName = "";
        public String symbolType = "";
        public int startingLine;
        public int endingLine;
        public String rawContent = "";
        public int tokenCount;
    }

    public static class Embedding {
        public UUID passageId;
        public byte[] vector = new byte[0];
        public int dims;
        public String modelId = "";
        public Instant updatedAt;
    }

    public static class Job {
        public UUID id;
        public UUID repositoryId;
        public String status = JOB_QUEUED;
        public int filesDiscovered;
        public int filesProcessed;
        public int filesFailed;
        public int passagesCreated;
        public int filesAdded;
        public int filesModified;
        public int filesDeleted;
        public int filesUnchanged;
        public Instant startedAt;
        public Instant completedAt;
        public String errorMessage = "";
        public Instant createdAt;
    }

    public static class PassageView {
        public Passage passage = new Passage();
        public String filePath = "";
        public String language = "";
        public UUID repositoryId;
        public String repositoryName = "";
        public String sourceUrl = "";
    }

    public static class PassageRef {
        public UUID id;
        public UUID documentId;
    }
}
