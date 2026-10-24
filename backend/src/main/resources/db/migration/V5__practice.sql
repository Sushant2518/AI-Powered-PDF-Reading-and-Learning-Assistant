-- Practice questions, generated once per concept and reused.
CREATE TABLE questions (
    id            BIGSERIAL PRIMARY KEY,
    concept_id    BIGINT      NOT NULL REFERENCES concepts (id) ON DELETE CASCADE,
    document_id   UUID        NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    page_number   INT         NOT NULL,
    type          TEXT        NOT NULL,   -- MCQ | EXPLAIN | INTERVIEW
    prompt        TEXT        NOT NULL,
    options       JSONB       NOT NULL DEFAULT '[]',
    answer_index  INT         NOT NULL DEFAULT -1,   -- MCQ only
    model_answer  TEXT        NOT NULL,
    explanation   TEXT        NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE attempts (
    id           BIGSERIAL PRIMARY KEY,
    question_id  BIGINT      NOT NULL REFERENCES questions (id) ON DELETE CASCADE,
    concept_id   BIGINT      NOT NULL REFERENCES concepts (id) ON DELETE CASCADE,
    type         TEXT        NOT NULL,
    answer       TEXT        NOT NULL,
    score        INT         NOT NULL,   -- 0..100
    feedback     JSONB,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One flashcard per concept, scheduled with a simple SM-2 style interval.
CREATE TABLE flashcards (
    id             BIGSERIAL PRIMARY KEY,
    concept_id     BIGINT           NOT NULL UNIQUE REFERENCES concepts (id) ON DELETE CASCADE,
    front          TEXT             NOT NULL,
    back           TEXT             NOT NULL,
    due_at         TIMESTAMPTZ      NOT NULL DEFAULT now(),
    interval_days  DOUBLE PRECISION NOT NULL DEFAULT 0,
    ease           DOUBLE PRECISION NOT NULL DEFAULT 2.5
);

CREATE TABLE flashcard_reviews (
    id            BIGSERIAL PRIMARY KEY,
    flashcard_id  BIGINT      NOT NULL REFERENCES flashcards (id) ON DELETE CASCADE,
    concept_id    BIGINT      NOT NULL REFERENCES concepts (id) ON DELETE CASCADE,
    got_it        BOOLEAN     NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX attempts_concept ON attempts (concept_id, created_at);
CREATE INDEX reviews_concept ON flashcard_reviews (concept_id, created_at);
CREATE INDEX questions_concept ON questions (concept_id);
