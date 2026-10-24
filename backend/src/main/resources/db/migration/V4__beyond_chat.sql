-- Cached "Beyond the PDF" per concept. sources = [{"n":1,"url":...,"title":...}]; created_at doubles as "accessed on".
CREATE TABLE beyond (
    concept_id  BIGINT PRIMARY KEY REFERENCES concepts (id) ON DELETE CASCADE,
    content     TEXT        NOT NULL,
    sources     JSONB       NOT NULL,
    hidden      INT         NOT NULL,   -- unsourced statements that were dropped
    model       TEXT        NOT NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One running "Ask" conversation per document.
CREATE TABLE chat_messages (
    id           BIGSERIAL PRIMARY KEY,
    document_id  UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    role         TEXT        NOT NULL,   -- USER | ASSISTANT
    content      TEXT        NOT NULL,
    page_number  INT         NOT NULL,
    quoted_text  TEXT,
    follow_ups   JSONB,
    beyond       TEXT,
    sources      JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX chat_messages_document ON chat_messages (document_id, id);
