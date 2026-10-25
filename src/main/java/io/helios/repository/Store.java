package io.helios.repository;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class Store {
    private final NamedParameterJdbcTemplate jdbc;

    public Store(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Models.Repository createRepository(Models.Repository repo) {
        validateRepository(repo);
        if (repo.id == null) {
            repo.id = UUID.randomUUID();
        }
        if (repo.defaultBranch == null || repo.defaultBranch.isBlank()) {
            repo.defaultBranch = "main";
        }
        if (repo.status == null || repo.status.isBlank()) {
            repo.status = Models.REPO_QUEUED;
        }
        String q = """
                INSERT INTO repositories (id, name, owner, source_url, default_branch, latest_commit_hash, status)
                VALUES (:id, :name, :owner, :sourceUrl, :defaultBranch, NULLIF(:commit, ''), CAST(:status AS repository_status))
                RETURNING id, name, owner, source_url, default_branch,
                    COALESCE(latest_commit_hash, ''), status, document_count, indexed_at, created_at, updated_at
                """;
        try {
            return jdbc.queryForObject(q, repoParams(repo), Store::mapRepository);
        } catch (DataIntegrityViolationException e) {
            throw wrap(e);
        }
    }

    public Models.Repository getRepository(UUID id) {
        String q = """
                SELECT id, name, owner, source_url, default_branch,
                    COALESCE(latest_commit_hash, ''), status, document_count, indexed_at, created_at, updated_at
                FROM repositories WHERE id = :id
                """;
        try {
            return jdbc.queryForObject(q, Map.of("id", id), Store::mapRepository);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        }
    }

    public Models.Repository getRepositoryBySourceUrl(String sourceUrl) {
        String q = """
                SELECT id, name, owner, source_url, default_branch,
                    COALESCE(latest_commit_hash, ''), status, document_count, indexed_at, created_at, updated_at
                FROM repositories WHERE source_url = :url
                """;
        try {
            return jdbc.queryForObject(q, Map.of("url", sourceUrl), Store::mapRepository);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        }
    }

    public List<Models.Repository> listRepositories() {
        String q = """
                SELECT id, name, owner, source_url, default_branch,
                    COALESCE(latest_commit_hash, ''), status, document_count, indexed_at, created_at, updated_at
                FROM repositories ORDER BY created_at DESC, id DESC
                """;
        return jdbc.query(q, Store::mapRepository);
    }

    public Models.Repository updateRepository(Models.Repository repo) {
        if (repo.id == null) {
            throw StoreException.invalid("id is required");
        }
        validateRepository(repo);
        String q = """
                UPDATE repositories
                SET name = :name, owner = :owner, source_url = :sourceUrl, default_branch = :defaultBranch,
                    latest_commit_hash = NULLIF(:commit, ''), status = CAST(:status AS repository_status),
                    document_count = :documentCount, indexed_at = :indexedAt
                WHERE id = :id
                RETURNING id, name, owner, source_url, default_branch,
                    COALESCE(latest_commit_hash, ''), status, document_count, indexed_at, created_at, updated_at
                """;
        try {
            return jdbc.queryForObject(q, repoParams(repo), Store::mapRepository);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        } catch (DataIntegrityViolationException e) {
            throw wrap(e);
        }
    }

    public void deleteRepository(UUID id) {
        int n = jdbc.update("DELETE FROM repositories WHERE id = :id", Map.of("id", id));
        if (n == 0) {
            throw StoreException.notFound();
        }
    }

    public Models.Document createDocument(Models.Document doc) {
        validateDocument(doc);
        if (doc.id == null) {
            doc.id = UUID.randomUUID();
        }
        String q = """
                INSERT INTO documents (id, repository_id, file_path, language, content_hash, size_bytes)
                VALUES (:id, :repositoryId, :filePath, :language, :contentHash, :sizeBytes)
                RETURNING id, repository_id, file_path, language, content_hash, size_bytes, indexed_at
                """;
        try {
            return jdbc.queryForObject(q, docParams(doc), Store::mapDocument);
        } catch (DataIntegrityViolationException e) {
            throw wrap(e);
        }
    }

    public Models.Document getDocument(UUID id) {
        String q = """
                SELECT id, repository_id, file_path, language, content_hash, size_bytes, indexed_at
                FROM documents WHERE id = :id
                """;
        try {
            return jdbc.queryForObject(q, Map.of("id", id), Store::mapDocument);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        }
    }

    public List<Models.Document> listDocumentsByRepository(UUID repositoryId) {
        String q = """
                SELECT id, repository_id, file_path, language, content_hash, size_bytes, indexed_at
                FROM documents WHERE repository_id = :id ORDER BY file_path
                """;
        return jdbc.query(q, Map.of("id", repositoryId), Store::mapDocument);
    }

    public void deleteDocumentsByRepository(UUID repositoryId) {
        jdbc.update("DELETE FROM documents WHERE repository_id = :id", Map.of("id", repositoryId));
    }

    public Models.Passage createPassage(Models.Passage passage) {
        validatePassage(passage);
        if (passage.id == null) {
            passage.id = UUID.randomUUID();
        }
        String q = """
                INSERT INTO passages (id, document_id, symbol_name, symbol_type, starting_line, ending_line, raw_content, token_count)
                VALUES (:id, :documentId, NULLIF(:symbolName, ''), NULLIF(:symbolType, ''), :start, :end, :content, :tokens)
                RETURNING id, document_id, COALESCE(symbol_name, ''), COALESCE(symbol_type, ''),
                    starting_line, ending_line, raw_content, token_count
                """;
        try {
            return jdbc.queryForObject(q, passageParams(passage), Store::mapPassage);
        } catch (DataIntegrityViolationException e) {
            throw wrap(e);
        }
    }

    public Models.Passage getPassage(UUID id) {
        String q = """
                SELECT id, document_id, COALESCE(symbol_name, ''), COALESCE(symbol_type, ''),
                    starting_line, ending_line, raw_content, token_count
                FROM passages WHERE id = :id
                """;
        try {
            return jdbc.queryForObject(q, Map.of("id", id), Store::mapPassage);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        }
    }

    public List<Models.Passage> getPassages(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String q = """
                SELECT id, document_id, COALESCE(symbol_name, ''), COALESCE(symbol_type, ''),
                    starting_line, ending_line, raw_content, token_count
                FROM passages WHERE id IN (:ids)
                """;
        return jdbc.query(q, Map.of("ids", ids), Store::mapPassage);
    }

    public List<Models.PassageView> getPassageViews(List<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        String q = """
                SELECT p.id, p.document_id, COALESCE(p.symbol_name, ''), COALESCE(p.symbol_type, ''),
                    p.starting_line, p.ending_line, p.raw_content, p.token_count,
                    d.file_path, d.language, d.repository_id, r.name, r.source_url
                FROM passages p
                JOIN documents d ON d.id = p.document_id
                JOIN repositories r ON r.id = d.repository_id
                WHERE p.id IN (:ids)
                """;
        return jdbc.query(q, Map.of("ids", ids), (rs, i) -> {
            Models.PassageView view = new Models.PassageView();
            view.passage = mapPassage(rs, i);
            view.filePath = rs.getString("file_path");
            view.language = rs.getString("language");
            view.repositoryId = rs.getObject("repository_id", UUID.class);
            view.repositoryName = rs.getString("name");
            view.sourceUrl = rs.getString("source_url");
            return view;
        });
    }

    public List<UUID> listPassageIdsByRepository(UUID repositoryId) {
        String q = """
                SELECT p.id FROM passages p
                JOIN documents d ON d.id = p.document_id
                WHERE d.repository_id = :id
                """;
        return jdbc.query(q, Map.of("id", repositoryId), (rs, i) -> rs.getObject(1, UUID.class));
    }

    public List<Models.PassageRef> listPassageRefsByRepository(UUID repositoryId) {
        String q = """
                SELECT p.id, p.document_id FROM passages p
                JOIN documents d ON d.id = p.document_id
                WHERE d.repository_id = :id
                """;
        return jdbc.query(q, Map.of("id", repositoryId), (rs, i) -> {
            Models.PassageRef ref = new Models.PassageRef();
            ref.id = rs.getObject(1, UUID.class);
            ref.documentId = rs.getObject(2, UUID.class);
            return ref;
        });
    }

    public List<Models.Passage> listPassagesByDocument(UUID documentId) {
        String q = """
                SELECT id, document_id, COALESCE(symbol_name, ''), COALESCE(symbol_type, ''),
                    starting_line, ending_line, raw_content, token_count
                FROM passages WHERE document_id = :id
                """;
        return jdbc.query(q, Map.of("id", documentId), Store::mapPassage);
    }

    public void deleteDocument(UUID id) {
        int n = jdbc.update("DELETE FROM documents WHERE id = :id", Map.of("id", id));
        if (n == 0) {
            throw StoreException.notFound();
        }
    }

    public List<Models.Passage> listAllPassages() {
        String q = """
                SELECT id, document_id, COALESCE(symbol_name, ''), COALESCE(symbol_type, ''),
                    starting_line, ending_line, raw_content, token_count
                FROM passages
                """;
        return jdbc.query(q, Store::mapPassage);
    }

    public List<Models.Job> listJobsByStatus(List<String> statuses) {
        if (statuses == null || statuses.isEmpty()) {
            return List.of();
        }
        String q = """
                SELECT id, repository_id, status, files_discovered, files_processed, files_failed, passages_created,
                    files_added, files_modified, files_deleted, files_unchanged, started_at, completed_at,
                    COALESCE(error_message, ''), created_at
                FROM indexing_jobs WHERE status::text IN (:statuses) ORDER BY created_at
                """;
        return jdbc.query(q, Map.of("statuses", statuses), Store::mapJob);
    }

    public Models.Job createJob(Models.Job job) {
        if (job.repositoryId == null) {
            throw StoreException.invalid("repository_id is required");
        }
        if (job.id == null) {
            job.id = UUID.randomUUID();
        }
        if (job.status == null || job.status.isBlank()) {
            job.status = Models.JOB_QUEUED;
        }
        String q = """
                INSERT INTO indexing_jobs (id, repository_id, status)
                VALUES (:id, :repositoryId, CAST(:status AS job_status))
                RETURNING id, repository_id, status, files_discovered, files_processed, files_failed, passages_created,
                    files_added, files_modified, files_deleted, files_unchanged, started_at, completed_at,
                    COALESCE(error_message, ''), created_at
                """;
        try {
            return jdbc.queryForObject(q, Map.of(
                    "id", job.id, "repositoryId", job.repositoryId, "status", job.status
            ), Store::mapJob);
        } catch (DataIntegrityViolationException e) {
            throw wrap(e);
        }
    }

    public Models.Job getJob(UUID id) {
        String q = """
                SELECT id, repository_id, status, files_discovered, files_processed, files_failed, passages_created,
                    files_added, files_modified, files_deleted, files_unchanged, started_at, completed_at,
                    COALESCE(error_message, ''), created_at
                FROM indexing_jobs WHERE id = :id
                """;
        try {
            return jdbc.queryForObject(q, Map.of("id", id), Store::mapJob);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        }
    }

    public Models.Job updateJob(Models.Job job) {
        if (job.id == null) {
            throw StoreException.invalid("id is required");
        }
        String q = """
                UPDATE indexing_jobs
                SET status = CAST(:status AS job_status), files_discovered = :discovered, files_processed = :processed,
                    files_failed = :failed, passages_created = :passages, files_added = :added, files_modified = :modified,
                    files_deleted = :deleted, files_unchanged = :unchanged, started_at = :started, completed_at = :completed,
                    error_message = NULLIF(:error, '')
                WHERE id = :id
                RETURNING id, repository_id, status, files_discovered, files_processed, files_failed, passages_created,
                    files_added, files_modified, files_deleted, files_unchanged, started_at, completed_at,
                    COALESCE(error_message, ''), created_at
                """;
        MapSqlParameterSource p = new MapSqlParameterSource()
                .addValue("id", job.id)
                .addValue("status", job.status)
                .addValue("discovered", job.filesDiscovered)
                .addValue("processed", job.filesProcessed)
                .addValue("failed", job.filesFailed)
                .addValue("passages", job.passagesCreated)
                .addValue("added", job.filesAdded)
                .addValue("modified", job.filesModified)
                .addValue("deleted", job.filesDeleted)
                .addValue("unchanged", job.filesUnchanged)
                .addValue("started", ts(job.startedAt))
                .addValue("completed", ts(job.completedAt))
                .addValue("error", job.errorMessage == null ? "" : job.errorMessage);
        try {
            return jdbc.queryForObject(q, p, Store::mapJob);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        }
    }

    public Models.Job latestJob(UUID repositoryId) {
        String q = """
                SELECT id, repository_id, status, files_discovered, files_processed, files_failed, passages_created,
                    files_added, files_modified, files_deleted, files_unchanged, started_at, completed_at,
                    COALESCE(error_message, ''), created_at
                FROM indexing_jobs WHERE repository_id = :id ORDER BY created_at DESC, id DESC LIMIT 1
                """;
        try {
            return jdbc.queryForObject(q, Map.of("id", repositoryId), Store::mapJob);
        } catch (EmptyResultDataAccessException e) {
            throw StoreException.notFound();
        }
    }

    public void upsertEmbedding(Models.Embedding row) {
        if (row.passageId == null) {
            throw StoreException.invalid("passage_id is required");
        }
        if (row.dims < 1 || row.vector == null || row.vector.length == 0) {
            throw StoreException.invalid("embedding is required");
        }
        if (row.modelId == null || row.modelId.isBlank()) {
            throw StoreException.invalid("model_id is required");
        }
        String q = """
                INSERT INTO passage_embeddings (passage_id, embedding, dims, model_id)
                VALUES (:id, :embedding, :dims, :model)
                ON CONFLICT (passage_id) DO UPDATE
                SET embedding = EXCLUDED.embedding, dims = EXCLUDED.dims, model_id = EXCLUDED.model_id, updated_at = now()
                """;
        jdbc.update(q, Map.of("id", row.passageId, "embedding", row.vector, "dims", row.dims, "model", row.modelId));
    }

    public List<Models.Embedding> listEmbeddings(String modelId) {
        String q = """
                SELECT passage_id, embedding, dims, model_id, updated_at
                FROM passage_embeddings WHERE model_id = :model
                """;
        return jdbc.query(q, Map.of("model", modelId), (rs, i) -> {
            Models.Embedding row = new Models.Embedding();
            row.passageId = rs.getObject("passage_id", UUID.class);
            row.vector = rs.getBytes("embedding");
            row.dims = rs.getInt("dims");
            row.modelId = rs.getString("model_id");
            row.updatedAt = instant(rs.getTimestamp("updated_at"));
            return row;
        });
    }

    private static Models.Repository mapRepository(ResultSet rs, int i) throws SQLException {
        Models.Repository repo = new Models.Repository();
        repo.id = rs.getObject(1, UUID.class);
        repo.name = rs.getString(2);
        repo.owner = rs.getString(3);
        repo.sourceUrl = rs.getString(4);
        repo.defaultBranch = rs.getString(5);
        repo.latestCommitHash = rs.getString(6);
        repo.status = rs.getString(7);
        repo.documentCount = rs.getInt(8);
        repo.indexedAt = instant(rs.getTimestamp(9));
        repo.createdAt = instant(rs.getTimestamp(10));
        repo.updatedAt = instant(rs.getTimestamp(11));
        return repo;
    }

    private static Models.Document mapDocument(ResultSet rs, int i) throws SQLException {
        Models.Document doc = new Models.Document();
        doc.id = rs.getObject(1, UUID.class);
        doc.repositoryId = rs.getObject(2, UUID.class);
        doc.filePath = rs.getString(3);
        doc.language = rs.getString(4);
        doc.contentHash = rs.getString(5);
        doc.sizeBytes = rs.getLong(6);
        doc.indexedAt = instant(rs.getTimestamp(7));
        return doc;
    }

    private static Models.Passage mapPassage(ResultSet rs, int i) throws SQLException {
        Models.Passage p = new Models.Passage();
        p.id = rs.getObject(1, UUID.class);
        p.documentId = rs.getObject(2, UUID.class);
        p.symbolName = rs.getString(3);
        p.symbolType = rs.getString(4);
        p.startingLine = rs.getInt(5);
        p.endingLine = rs.getInt(6);
        p.rawContent = rs.getString(7);
        p.tokenCount = rs.getInt(8);
        return p;
    }

    private static Models.Job mapJob(ResultSet rs, int i) throws SQLException {
        Models.Job job = new Models.Job();
        job.id = rs.getObject(1, UUID.class);
        job.repositoryId = rs.getObject(2, UUID.class);
        job.status = rs.getString(3);
        job.filesDiscovered = rs.getInt(4);
        job.filesProcessed = rs.getInt(5);
        job.filesFailed = rs.getInt(6);
        job.passagesCreated = rs.getInt(7);
        job.filesAdded = rs.getInt(8);
        job.filesModified = rs.getInt(9);
        job.filesDeleted = rs.getInt(10);
        job.filesUnchanged = rs.getInt(11);
        job.startedAt = instant(rs.getTimestamp(12));
        job.completedAt = instant(rs.getTimestamp(13));
        job.errorMessage = rs.getString(14);
        job.createdAt = instant(rs.getTimestamp(15));
        return job;
    }

    private static MapSqlParameterSource repoParams(Models.Repository repo) {
        return new MapSqlParameterSource()
                .addValue("id", repo.id)
                .addValue("name", repo.name)
                .addValue("owner", repo.owner)
                .addValue("sourceUrl", repo.sourceUrl)
                .addValue("defaultBranch", repo.defaultBranch)
                .addValue("commit", repo.latestCommitHash == null ? "" : repo.latestCommitHash)
                .addValue("status", repo.status)
                .addValue("documentCount", repo.documentCount)
                .addValue("indexedAt", ts(repo.indexedAt));
    }

    private static MapSqlParameterSource docParams(Models.Document doc) {
        return new MapSqlParameterSource()
                .addValue("id", doc.id)
                .addValue("repositoryId", doc.repositoryId)
                .addValue("filePath", doc.filePath)
                .addValue("language", doc.language)
                .addValue("contentHash", doc.contentHash)
                .addValue("sizeBytes", doc.sizeBytes);
    }

    private static MapSqlParameterSource passageParams(Models.Passage p) {
        return new MapSqlParameterSource()
                .addValue("id", p.id)
                .addValue("documentId", p.documentId)
                .addValue("symbolName", p.symbolName == null ? "" : p.symbolName)
                .addValue("symbolType", p.symbolType == null ? "" : p.symbolType)
                .addValue("start", p.startingLine)
                .addValue("end", p.endingLine)
                .addValue("content", p.rawContent)
                .addValue("tokens", p.tokenCount);
    }

    private static void validateRepository(Models.Repository repo) {
        if (repo.name == null || repo.name.isBlank()) {
            throw StoreException.invalid("name is required");
        }
        if (repo.owner == null || repo.owner.isBlank()) {
            throw StoreException.invalid("owner is required");
        }
        if (repo.sourceUrl == null || repo.sourceUrl.isBlank()) {
            throw StoreException.invalid("source_url is required");
        }
    }

    private static void validateDocument(Models.Document doc) {
        if (doc.repositoryId == null) {
            throw StoreException.invalid("repository_id is required");
        }
        if (doc.filePath == null || doc.filePath.isBlank()) {
            throw StoreException.invalid("file_path is required");
        }
        if (doc.language == null || doc.language.isBlank()) {
            throw StoreException.invalid("language is required");
        }
        if (doc.contentHash == null || doc.contentHash.isBlank()) {
            throw StoreException.invalid("content_hash is required");
        }
        if (doc.sizeBytes < 0) {
            throw StoreException.invalid("size_bytes cannot be negative");
        }
    }

    private static void validatePassage(Models.Passage passage) {
        if (passage.documentId == null) {
            throw StoreException.invalid("document_id is required");
        }
        if (passage.startingLine <= 0) {
            throw StoreException.invalid("starting_line must be positive");
        }
        if (passage.endingLine < passage.startingLine) {
            throw StoreException.invalid("ending_line must be at or after starting_line");
        }
        if (passage.tokenCount < 0) {
            throw StoreException.invalid("token_count cannot be negative");
        }
    }

    private static StoreException wrap(DataIntegrityViolationException e) {
        String msg = e.getMostSpecificCause() == null ? e.getMessage() : e.getMostSpecificCause().getMessage();
        if (msg != null && msg.contains("23505")) {
            return StoreException.conflict(e);
        }
        if (msg != null && msg.contains("duplicate key")) {
            return StoreException.conflict(e);
        }
        if (msg != null && (msg.contains("23503") || msg.contains("foreign key"))) {
            return new StoreException(StoreException.INVALID_REFERENCE, StoreException.INVALID_REFERENCE, e);
        }
        return new StoreException("sql", msg == null ? "sql error" : msg, e);
    }

    private static Instant instant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }

    private static Timestamp ts(Instant instant) {
        return instant == null ? null : Timestamp.from(instant);
    }
}
