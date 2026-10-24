-- AI provider chosen in Settings: gemini (free tier) or claude (paid).
ALTER TABLE settings ADD COLUMN provider TEXT NOT NULL DEFAULT 'gemini';
