DO $$ BEGIN
    CREATE TYPE repository_status AS ENUM (
        'queued',
        'cloning',
        'indexing',
        'indexed',
        'failed'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

DO $$ BEGIN
    CREATE TYPE job_status AS ENUM (
        'queued',
        'running',
        'succeeded',
        'failed'
    );
EXCEPTION
    WHEN duplicate_object THEN NULL;
END $$;

CREATE TABLE IF NOT EXISTS repositories (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    name TEXT NOT NULL,
    owner TEXT NOT NULL,
    source_url TEXT NOT NULL,
    default_branch TEXT NOT NULL DEFAULT 'main',
    latest_commit_hash TEXT,
    status repository_status NOT NULL DEFAULT 'queued',
    document_count INTEGER NOT NULL DEFAULT 0 CHECK (document_count >= 0),
    indexed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT repositories_source_url_unique UNIQUE (source_url)
);

CREATE TABLE IF NOT EXISTS documents (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    file_path TEXT NOT NULL,
    language TEXT NOT NULL,
    content_hash TEXT NOT NULL,
    size_bytes BIGINT NOT NULL CHECK (size_bytes >= 0),
    indexed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT documents_repository_file_unique UNIQUE (repository_id, file_path)
);

CREATE TABLE IF NOT EXISTS passages (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    symbol_name TEXT,
    symbol_type TEXT,
    starting_line INTEGER NOT NULL CHECK (starting_line > 0),
    ending_line INTEGER NOT NULL,
    raw_content TEXT NOT NULL,
    token_count INTEGER NOT NULL CHECK (token_count >= 0),
    CONSTRAINT passages_line_range_valid CHECK (ending_line >= starting_line)
);

CREATE TABLE IF NOT EXISTS indexing_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    repository_id UUID NOT NULL REFERENCES repositories (id) ON DELETE CASCADE,
    status job_status NOT NULL DEFAULT 'queued',
    files_discovered INTEGER NOT NULL DEFAULT 0 CHECK (files_discovered >= 0),
    files_processed INTEGER NOT NULL DEFAULT 0 CHECK (files_processed >= 0),
    files_failed INTEGER NOT NULL DEFAULT 0 CHECK (files_failed >= 0),
    passages_created INTEGER NOT NULL DEFAULT 0 CHECK (passages_created >= 0),
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    error_message TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS documents_repository_id_idx ON documents (repository_id);
CREATE INDEX IF NOT EXISTS passages_document_id_idx ON passages (document_id);
CREATE INDEX IF NOT EXISTS indexing_jobs_repository_id_idx ON indexing_jobs (repository_id);
CREATE INDEX IF NOT EXISTS indexing_jobs_status_idx ON indexing_jobs (status);

CREATE OR REPLACE FUNCTION set_updated_at()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

DROP TRIGGER IF EXISTS repositories_set_updated_at ON repositories;
CREATE TRIGGER repositories_set_updated_at
    BEFORE UPDATE ON repositories
    FOR EACH ROW
    EXECUTE FUNCTION set_updated_at();
