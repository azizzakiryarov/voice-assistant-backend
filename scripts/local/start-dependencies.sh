#!/usr/bin/env bash
# Starts PostgreSQL, Ollama and the local Whisper API without starting the apps.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
LOCAL_DIR="$PROJECT_ROOT/.local"
WHISPER_DIR="$PROJECT_ROOT/local-whisper-service"
VENV_PYTHON="$LOCAL_DIR/whisper-venv/bin/python"
PID_FILE="$LOCAL_DIR/whisper.pid"
LOG_FILE="$LOCAL_DIR/whisper.log"

fail() {
  printf 'Error: %s\n' "$1" >&2
  exit 1
}

command -v brew >/dev/null 2>&1 || fail "Homebrew is required. Run ./scripts/local/setup-mac.sh first."
[[ -x "$VENV_PYTHON" ]] || fail "Whisper environment is missing. Run ./scripts/local/setup-mac.sh first."

brew services start postgresql@14
brew services start ollama

if curl --fail --silent http://localhost:9000/health >/dev/null 2>&1; then
  printf 'Whisper is already listening on http://localhost:9000.\n'
  exit 0
fi

if lsof -nP -iTCP:9000 -sTCP:LISTEN >/dev/null 2>&1; then
  fail "Port 9000 is already in use, but it is not the local Whisper API."
fi

mkdir -p "$LOCAL_DIR"
rm -f "$PID_FILE"
nohup "$VENV_PYTHON" -m uvicorn main:app --app-dir "$WHISPER_DIR" --host 127.0.0.1 --port 9000 >"$LOG_FILE" 2>&1 &
echo $! >"$PID_FILE"

printf 'Starting Whisper (the first start downloads the small model and can take several minutes).\n'
for _ in {1..300}; do
  if curl --fail --silent http://localhost:9000/health >/dev/null 2>&1; then
    printf 'Whisper is ready at http://localhost:9000. Logs: %s\n' "$LOG_FILE"
    exit 0
  fi
  if ! kill -0 "$(cat "$PID_FILE")" 2>/dev/null; then
    tail -n 30 "$LOG_FILE" >&2 || true
    fail "Whisper stopped before it became ready."
  fi
  sleep 2
done

tail -n 30 "$LOG_FILE" >&2 || true
fail "Whisper was not ready after 10 minutes."
