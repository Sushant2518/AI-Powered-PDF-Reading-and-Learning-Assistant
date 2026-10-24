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

## Claude API key

The app uses Claude (`claude-haiku-5-5`, about $1 per 1,000 explained pages). Create `backend/.env` (git-ignored) and restart the backend:

```
ANTHROPIC_API_KEY=sk-ant-...
```

Spend is tracked per call and AI stops at the monthly budget (Settings, default $5). Web search for 🌐 Beyond is the main cost ($0.01 per search, cached per concept). For deeper answers set `samajh.ai.model` in `backend/src/main/resources/application.yml` to `claude-sonnet-5-5` or `claude-opus-5-5` and update the prices next to it.

Without a key, reading, notes, highlights and the library still work; the AI features show a "missing API key" message.

**Scanned or old-font Marathi/Hindi PDFs:** pages without readable text show *📷 Read this page from its image*; the *Original* tab has *Re-read from the page image* for gibberish text (Kruti Dev, Shree-Lipi).

## Test

```bash
cd backend && ./mvnw test                                  # unit tests
cd frontend && npm test && npm run lint && npx tsc --noEmit
./e2e/run.sh   # whole app in Chrome against a fake Claude: free, separate database (stop the backend first)
```
