-- Single-row app settings, editable from the Settings page.
CREATE TABLE settings (
    id                  INT           PRIMARY KEY CHECK (id = 1),
    provider            TEXT          NOT NULL DEFAULT 'claude',     -- claude | local
    local_model         TEXT          NOT NULL DEFAULT 'gemma3:4b',  -- any model pulled into Ollama
    monthly_budget_usd  NUMERIC(8, 2) NOT NULL DEFAULT 5
);
INSERT INTO settings (id) VALUES (1);

-- Pages whose text was read from the page image.
ALTER TABLE pages ADD COLUMN ocr BOOLEAN NOT NULL DEFAULT false;
