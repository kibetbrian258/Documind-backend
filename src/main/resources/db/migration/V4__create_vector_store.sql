-- Searchable chunks of every document (PRD section 9).
-- Spring AI's PgVectorStore reads and writes this table; Flyway creates it so
-- the schema is versioned like everything else (initialize-schema is off).
--
-- embedding size must match the embedding model: nomic-embed-text gives 768 numbers.
-- Changing model to one with a different size needs a new migration and re-uploading documents.
CREATE TABLE vector_store (
                              id        UUID        PRIMARY KEY,
                              content   TEXT        NOT NULL,
                              metadata  JSONB       NOT NULL,   -- user_id, document_id, page, chunk_index
                              embedding VECTOR(768) NOT NULL
);

-- Fast "nearest meaning" search (HNSW index, cosine distance).
CREATE INDEX ix_vector_store_embedding ON vector_store USING hnsw (embedding vector_cosine_ops);

-- Fast filtering on metadata, e.g. "only this user's chunks" or "this document's chunks".
CREATE INDEX ix_vector_store_metadata ON vector_store USING gin (metadata jsonb_path_ops);