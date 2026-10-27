# SamajhPDF — Personal Tech Mastery Companion

Upload a tech PDF and master it: **Understand** (simple English + मराठी/हिन्दी) → **Deepen** (code, diagrams, trade-offs) → **🌐 Beyond the PDF** (sourced web knowledge) → **Ask** → **Practice** → **Mastery**.

Docs: [PRD](docs/PRD.md) · [TRD](docs/TRD.md) · [UI design](docs/UI-DESIGN.md) · [mockup](docs/ui-mockup.html)

## Run locally

Needs Java 21, Node 20+, Docker.

```bash
docker compose up -d                        # Postgres on 127.0.0.1:5433
cd backend && ./mvnw spring-boot:run        # API on 127.0.0.1:8080 (localhost only, no login)
cd frontend && npm install && npm run dev   # UI on http://localhost:3000
```

Uploaded PDFs are stored in `backend/data/pdfs/` (git-ignored).

## AI provider (Settings page)

**Gemini, free (default).** No card needed.

1. Open [aistudio.google.com/apikey](https://aistudio.google.com/apikey), sign in with a Google account, click *Create API key*.
2. Put it in `backend/.env` (git-ignored) and restart the backend:

   ```
   GEMINI_API_KEY=your-key
   ```

Uses `gemini-3.8-flash` (Google closed the 2.5 models to new keys). Everything works except **🌐 Beyond the PDF**: Google Search is not in the free tier for new keys, so Beyond shows a message (set `samajh.ai.gemini.search: true` only if you enable billing on your Google project). The free tier can be slow at busy times (sometimes a minute or more) and has per-minute and per-day limits; the app tells you when it hits them. Turning *Auto-explain* off saves requests. On the free tier Google may use what you send to improve its products, so don't upload confidential PDFs.

**Local, free and offline** (Ollama, for testing or when you're out of free Gemini requests). No key, no limits, nothing leaves your Mac:

```bash
brew install ollama
ollama serve &              # keep running while you use the app
ollama pull qwen3.5:4b      # then choose "Local" in Settings
```

Models that fit an 8 GB Mac: `qwen3.5:4b` (best balance, reads page images for OCR) or `qwen3.5:2b` (lighter, faster). There's no 🌐 Beyond the PDF.

Measured with `qwen3.5:4b` on an 8 GB M-series Mac (thinking off, about 15 tokens/s): page explanation ~40 s, Ask ~25 s, Deepen ~40 s, concepts ~10 s, grading ~7 s, practice questions ~3–4 min, page-image OCR ~3 min. English answers are good; **Marathi/Hindi output is poor** at this size, so use Gemini for those.

**Claude, paid.** About $1 per 1,000 explained pages with `claude-haiku-5-5`. Add `ANTHROPIC_API_KEY=sk-ant-...` to `backend/.env`, choose Claude in Settings, and set a monthly budget (default $5; AI stops when reached). For deeper answers set `samajh.ai.model` to `claude-sonnet-5-5` or `claude-opus-5-5` and update the prices next to it.

Without a key, reading, notes, highlights and the library still work; the AI features show a "missing API key" message.

**Scanned or old-font Marathi/Hindi PDFs:** pages without readable text show *📷 Read this page from its image*; the *Original* tab has *Re-read from the page image* for gibberish text (Kruti Dev, Shree-Lipi).

## Test

```bash
cd backend && ./mvnw test                                  # unit tests
cd frontend && npm test && npm run lint && npx tsc --noEmit
./e2e/run.sh   # whole app in Chrome against fake Gemini, Claude and Ollama: free, separate database (stop the backend and real Ollama first)
```
