CREATE TABLE documents (
    id          UUID PRIMARY KEY,
    title       TEXT        NOT NULL,
    file_name   TEXT        NOT NULL,
    size_bytes  BIGINT      NOT NULL,
    page_count  INT         NOT NULL DEFAULT 0,
    status      TEXT        NOT NULL,
    last_page   INT         NOT NULL DEFAULT 1,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE pages (
    id           BIGSERIAL PRIMARY KEY,
    document_id  UUID    NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number  INT     NOT NULL,
    text         TEXT    NOT NULL,
    needs_ocr    BOOLEAN NOT NULL,
    UNIQUE (document_id, page_number)
);
