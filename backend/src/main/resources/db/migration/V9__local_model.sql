-- Model used when the provider is "local" (Ollama on this machine).
ALTER TABLE settings ADD COLUMN local_model TEXT NOT NULL DEFAULT 'qwen3.5:4b';
