CREATE TABLE IF NOT EXISTS passage_embeddings (
    passage_id UUID PRIMARY KEY REFERENCES passages (id) ON DELETE CASCADE,
    embedding BYTEA NOT NULL,
    dims INTEGER NOT NULL CHECK (dims > 0),
    model_id TEXT NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS passage_embeddings_model_id_idx ON passage_embeddings (model_id);
