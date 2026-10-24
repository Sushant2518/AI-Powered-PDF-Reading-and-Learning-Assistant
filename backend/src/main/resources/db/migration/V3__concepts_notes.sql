ALTER TABLE pages ADD COLUMN concepts_extracted BOOLEAN NOT NULL DEFAULT false;

-- A concept is shared across all PDFs (e.g. "Idempotence" from DDIA and from RFC 9110).
CREATE TABLE concepts (
    id            BIGSERIAL PRIMARY KEY,
    slug          TEXT        NOT NULL UNIQUE,
    name          TEXT        NOT NULL,
    domain        TEXT        NOT NULL,
    definition    TEXT        NOT NULL,
    deepen        TEXT,                 -- cached Deepen markdown
    deepen_model  TEXT,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE concept_mentions (
    concept_id   BIGINT NOT NULL REFERENCES concepts (id) ON DELETE CASCADE,
    document_id  UUID   NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number  INT    NOT NULL,
    PRIMARY KEY (concept_id, document_id, page_number)
);

-- anchor = {"rects":[{"x","y","w","h"}]} normalised 0..1 to the page, so it works at any zoom.
CREATE TABLE notes (
    id             BIGSERIAL PRIMARY KEY,
    document_id    UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number    INT         NOT NULL,
    selected_text  TEXT,
    anchor         JSONB,
    content        TEXT        NOT NULL DEFAULT '',
    source         TEXT        NOT NULL DEFAULT 'ME',   -- ME | AI
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE highlights (
    id             BIGSERIAL PRIMARY KEY,
    document_id    UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number    INT         NOT NULL,
    selected_text  TEXT        NOT NULL,
    anchor         JSONB       NOT NULL,
    color          TEXT        NOT NULL,
    created_at     TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX notes_document ON notes (document_id, page_number);
CREATE INDEX highlights_document ON highlights (document_id, page_number);
