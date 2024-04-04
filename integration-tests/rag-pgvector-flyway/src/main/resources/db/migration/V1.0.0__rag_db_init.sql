CREATE EXTENSION IF NOT EXISTS "vector";

CREATE TABLE IF NOT EXISTS embeddings (
    embedding_id UUID PRIMARY KEY,
    embedding vector(384),
    text TEXT NULL,
    metadata JSON NULL
);

