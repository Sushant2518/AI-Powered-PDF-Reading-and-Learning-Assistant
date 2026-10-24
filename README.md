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

Uses `gemini-2.5-flash` (its free tier includes Google Search for 🌐 Beyond the PDF). Free-tier limits are per minute and per day; when hit, the app says so. Turning *Auto-explain* off saves requests. On the free tier Google may use what you send to improve its products, so don't upload confidential PDFs. Gemini searches the whole web, and the app keeps only statements sourced from the trusted sites in `application.yml`.

**Claude, paid.** About $1 per 1,000 explained pages with `claude-haiku-5-5`. Add `ANTHROPIC_API_KEY=sk-ant-...` to `backend/.env`, choose Claude in Settings, and set a monthly budget (default $5; AI stops when reached). For deeper answers set `samajh.ai.model` to `claude-sonnet-5-5` or `claude-opus-5-5` and update the prices next to it.

Without a key, reading, notes, highlights and the library still work; the AI features show a "missing API key" message.

**Scanned or old-font Marathi/Hindi PDFs:** pages without readable text show *📷 Read this page from its image*; the *Original* tab has *Re-read from the page image* for gibberish text (Kruti Dev, Shree-Lipi).

## Test

```bash
cd backend && ./mvnw test                                  # unit tests
cd frontend && npm test && npm run lint && npx tsc --noEmit
./e2e/run.sh   # whole app in Chrome against fake Gemini and Claude: free, separate database (stop the backend first)
```
