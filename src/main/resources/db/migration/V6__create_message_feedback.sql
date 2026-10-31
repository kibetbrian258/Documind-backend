-- Thumbs up / down on answers (FR-20). One rating per answer; rating again replaces it.
-- message_id is kept nullable with ON DELETE SET NULL, like usage_records: deleting a
-- conversation keeps its ratings, so the satisfaction figure doesn't change after the fact.
CREATE TABLE message_feedback (
                                  id         UUID          PRIMARY KEY,
                                  message_id UUID          REFERENCES messages (id) ON DELETE SET NULL,
                                  user_id    UUID          NOT NULL REFERENCES users (id) ON DELETE CASCADE,
                                  rating     VARCHAR(10)   NOT NULL,
                                  comment    VARCHAR(1000),
                                  created_at TIMESTAMPTZ   NOT NULL DEFAULT now(),
                                  updated_at TIMESTAMPTZ   NOT NULL DEFAULT now(),

                                  CONSTRAINT uk_message_feedback_message UNIQUE (message_id),
                                  CONSTRAINT ck_message_feedback_rating  CHECK (rating IN ('UP', 'DOWN'))
);
CREATE INDEX ix_message_feedback_updated ON message_feedback (updated_at);