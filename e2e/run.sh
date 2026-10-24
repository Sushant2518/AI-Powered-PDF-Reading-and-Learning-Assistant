#!/usr/bin/env bash
# End-to-end test against fake Gemini and Claude servers: no API keys, no cost, separate database.
# Needs: Docker (Postgres from docker-compose), Java 21, Node 20+, Google Chrome.
# Stop your normal backend first (this one uses port 8080 too). Usage: ./e2e/run.sh
set -euo pipefail
cd "$(dirname "$0")/.."
ROOT=$(pwd)
TMP=$(mktemp -d)
PIDS=()
cleanup() {
  # mvnw starts Java as a child process, so stop the backend by its port (we checked it was free before starting).
  kill $(lsof -t -iTCP:8080 -sTCP:LISTEN 2>/dev/null) 2>/dev/null || true
  for p in "${PIDS[@]:-}"; do kill "$p" 2>/dev/null || true; done
  pkill -f "remote-debugging-port=9333" 2>/dev/null || true
  wait 2>/dev/null || true
}
trap cleanup EXIT

if lsof -iTCP:8080 -sTCP:LISTEN >/dev/null 2>&1; then
  echo "Port 8080 is in use. Stop the backend first, then re-run."; exit 1
fi

echo "› fresh database samajh_e2e"
docker compose up -d postgres >/dev/null
docker compose exec -T postgres psql -U samajh -d samajh -qc "DROP DATABASE IF EXISTS samajh_e2e" -c "CREATE DATABASE samajh_e2e" >/dev/null

echo "› fake Gemini on :9098, fake Claude on :9099"
node e2e/fake-gemini.mjs "$TMP/gemini-requests.jsonl" & PIDS+=($!); disown
node e2e/fake-claude.mjs "$TMP/claude-requests.jsonl" & PIDS+=($!); disown

echo "› backend on :8080 (fakes, e2e database)"
(cd backend && SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5433/samajh_e2e SAMAJH_STORAGE_DIR="$TMP/pdfs" \
  GEMINI_API_KEY=fake-key SAMAJH_AI_GEMINI_BASE_URL=http://127.0.0.1:9098 SAMAJH_AI_GEMINI_SEARCH=true \
  ANTHROPIC_BASE_URL=http://127.0.0.1:9099 ANTHROPIC_API_KEY=fake-key ./mvnw -q spring-boot:run > "$TMP/backend.log" 2>&1) & PIDS+=($!); disown

if ! curl -sf -o /dev/null localhost:3000; then
  echo "› frontend on :3000"
  (cd frontend && npm run dev > "$TMP/frontend.log" 2>&1) & PIDS+=($!); disown
fi
for i in $(seq 1 90); do curl -sf -o /dev/null localhost:8080/api/usage && curl -sf -o /dev/null localhost:3000 && break; sleep 2; done

CHROME=${CHROME:-"/Applications/Google Chrome.app/Contents/MacOS/Google Chrome"}
"$CHROME" --headless --remote-debugging-port=9333 --user-data-dir="$TMP/chrome" about:blank >/dev/null 2>&1 & PIDS+=($!); disown
sleep 2

for provider in gemini claude; do
  echo "› running the flow with $provider"
  if ! node e2e/run.mjs "$ROOT/e2e/sample.pdf" "$TMP" "$provider"; then
    echo "❌ E2E failed ($provider). Logs and screenshots in $TMP"; exit 1
  fi
done
echo "✅ E2E passed for Gemini and Claude"
