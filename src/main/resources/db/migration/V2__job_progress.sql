ALTER TABLE indexing_jobs
    ADD COLUMN IF NOT EXISTS files_added INTEGER NOT NULL DEFAULT 0 CHECK (files_added >= 0);
ALTER TABLE indexing_jobs
    ADD COLUMN IF NOT EXISTS files_modified INTEGER NOT NULL DEFAULT 0 CHECK (files_modified >= 0);
ALTER TABLE indexing_jobs
    ADD COLUMN IF NOT EXISTS files_deleted INTEGER NOT NULL DEFAULT 0 CHECK (files_deleted >= 0);
ALTER TABLE indexing_jobs
    ADD COLUMN IF NOT EXISTS files_unchanged INTEGER NOT NULL DEFAULT 0 CHECK (files_unchanged >= 0);
