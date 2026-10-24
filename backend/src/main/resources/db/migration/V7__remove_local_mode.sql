-- Local mode (Ollama) was removed; Claude is the only provider.
ALTER TABLE settings DROP COLUMN provider, DROP COLUMN local_model;
