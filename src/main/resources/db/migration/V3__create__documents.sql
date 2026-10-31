-- Uploaded documents (PRD section 9).
-- The file itself lives on disk; storage_path says where.
CREATE TABLE documents (
                           id             UUID         PRIMARY KEY,
                           user_id        UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                           file_name      VARCHAR(255) NOT NULL,
                           content_type   VARCHAR(100) NOT NULL,
                           size_bytes     BIGINT       NOT NULL,
                           page_count     INTEGER,
                           status         VARCHAR(20)  NOT NULL DEFAULT 'PROCESSING',
                           failure_reason VARCHAR(500),
                           storage_path   VARCHAR(500) NOT NULL,
                           created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),

                           CONSTRAINT uk_documents_storage_path UNIQUE (storage_path),
                           CONSTRAINT ck_documents_status       CHECK (status IN ('PROCESSING', 'READY', 'FAILED')),
                           CONSTRAINT ck_documents_size         CHECK (size_bytes > 0)
);

-- "My documents, newest first" is the most common query.
CREATE INDEX ix_documents_user_created ON documents (user_id, created_at DESC);