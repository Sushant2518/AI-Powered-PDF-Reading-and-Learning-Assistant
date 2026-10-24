-- Cached "Understand" output per page, language and style (raw model text; the UI splits the sections).
CREATE TABLE page_ai (
    document_id  UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number  INT         NOT NULL,
    lang         TEXT        NOT NULL,
    style        TEXT        NOT NULL,
    content      TEXT        NOT NULL,
    model        TEXT        NOT NULL,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (document_id, page_number, lang, style)
);

-- Every Claude call, for the monthly budget guard.
CREATE TABLE ai_usage (
    id             BIGSERIAL PRIMARY KEY,
    feature        TEXT          NOT NULL,
    model          TEXT          NOT NULL,
    input_tokens   BIGINT        NOT NULL,
    output_tokens  BIGINT        NOT NULL,
    cost_usd       NUMERIC(10,6) NOT NULL,
    created_at     TIMESTAMPTZ   NOT NULL DEFAULT now()
);
