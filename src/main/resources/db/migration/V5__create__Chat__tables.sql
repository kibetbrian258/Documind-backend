CREATE TABLE conversations (
                               id         UUID         PRIMARY KEY,
                               user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                               title      VARCHAR(200),                       -- set from the first question
                               created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
                               updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Sidebar: "my conversations, most recently used first".
CREATE INDEX ix_conversations_user_updated ON conversations (user_id, updated_at DESC);


CREATE TABLE messages (
                          id              UUID        PRIMARY KEY,
                          conversation_id UUID        NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
                          role            VARCHAR(20) NOT NULL,
                          content         TEXT        NOT NULL,
                          created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),

                          CONSTRAINT ck_messages_role CHECK (role IN ('USER', 'ASSISTANT'))
);
CREATE INDEX ix_messages_conversation_created ON messages (conversation_id, created_at);


-- The sources behind each answer.
-- Deleting a document deletes its citations automatically (FR-13).
CREATE TABLE message_citations (
                                   id               UUID             PRIMARY KEY,
                                   message_id       UUID             NOT NULL REFERENCES messages (id) ON DELETE CASCADE,
                                   document_id      UUID             NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
                                   citation_number  INTEGER          NOT NULL,  -- the [n] used in the answer text
                                   page             INTEGER,                    -- null for DOCX and TXT
                                   chunk_id         UUID             NOT NULL,  -- row in vector_store (no foreign key: Spring AI manages that table)
                                   similarity_score DOUBLE PRECISION NOT NULL,
                                   snippet          TEXT             NOT NULL
);
CREATE INDEX ix_message_citations_message  ON message_citations (message_id);
CREATE INDEX ix_message_citations_document ON message_citations (document_id);


-- One row per AI chat call (FR-14). Feeds daily limits and the admin dashboard.
CREATE TABLE usage_records (
                               id                 UUID          PRIMARY KEY,
                               user_id            UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                               message_id         UUID          REFERENCES messages (id) ON DELETE SET NULL,
                               model              VARCHAR(100)  NOT NULL,
                               input_tokens       INTEGER       NOT NULL,
                               output_tokens      INTEGER       NOT NULL,
                               estimated_cost_usd NUMERIC(12, 6) NOT NULL,
                               latency_ms         INTEGER       NOT NULL,  -- time until the first word appeared
                               duration_ms        INTEGER       NOT NULL,  -- time until the answer was complete
                               created_at         TIMESTAMPTZ   NOT NULL DEFAULT now()
);
CREATE INDEX ix_usage_records_user_created ON usage_records (user_id, created_at);