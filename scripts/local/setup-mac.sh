#!/usr/bin/env bash
# Provisions the local dependencies used by the Raspberry Pi deployment.
# It is safe to run repeatedly; application source code is never changed.
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
PROJECT_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
WHISPER_DIR="$PROJECT_ROOT/local-whisper-service"
LOCAL_DIR="$PROJECT_ROOT/.local"
ENV_FILE="$PROJECT_ROOT/.env.local"
POSTGRES_USER="voiceassistant"
POSTGRES_PASSWORD="voiceassistant"
POSTGRES_DATABASE="voiceassistant"
OLLAMA_MODEL="${OLLAMA_CHAT_MODEL:-llama3.2:1b}"

info() {
  printf '\n==> %s\n' "$1"
}

fail() {
  printf 'Error: %s\n' "$1" >&2
  exit 1
}

command -v brew >/dev/null 2>&1 || fail "Homebrew is required. Install it from https://brew.sh, then run this script again."

info "Installing local development dependencies"
brew install postgresql@14 ollama python@3.11 ffmpeg openjdk@21 node tesseract tesseract-lang

POSTGRES_PREFIX="$(brew --prefix postgresql@14)"
PSQL="$POSTGRES_PREFIX/bin/psql"
CREATEDB="$POSTGRES_PREFIX/bin/createdb"
PG_ISREADY="$POSTGRES_PREFIX/bin/pg_isready"
PYTHON_BIN="$(brew --prefix python@3.11)/bin/python3.11"

info "Starting PostgreSQL and Ollama"
brew services start postgresql@14
brew services start ollama

info "Waiting for PostgreSQL"
for _ in {1..30}; do
  if "$PG_ISREADY" -h localhost -p 5432 >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
"$PG_ISREADY" -h localhost -p 5432 >/dev/null 2>&1 || fail "PostgreSQL did not become ready on localhost:5432. Check: brew services list"

POSTGRES_SERVER_VERSION="$("$PSQL" -d postgres -tAc 'SHOW server_version' | tr -d '[:space:]')"
if [[ "${POSTGRES_SERVER_VERSION%%.*}" != "14" ]]; then
  fail "PostgreSQL on port 5432 is version $POSTGRES_SERVER_VERSION, not 14. Stop the conflicting service before continuing."
fi

info "Creating local database role and database"
if ! "$PSQL" -d postgres -tAc "SELECT 1 FROM pg_roles WHERE rolname = '$POSTGRES_USER'" | grep -q 1; then
  "$PSQL" -d postgres -v ON_ERROR_STOP=1 -c "CREATE ROLE $POSTGRES_USER LOGIN PASSWORD '$POSTGRES_PASSWORD';"
else
  "$PSQL" -d postgres -v ON_ERROR_STOP=1 -c "ALTER ROLE $POSTGRES_USER WITH LOGIN PASSWORD '$POSTGRES_PASSWORD';"
fi

if ! "$PSQL" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname = '$POSTGRES_DATABASE'" | grep -q 1; then
  "$CREATEDB" -O "$POSTGRES_USER" "$POSTGRES_DATABASE"
fi
"$PSQL" -d postgres -v ON_ERROR_STOP=1 -c "GRANT ALL PRIVILEGES ON DATABASE $POSTGRES_DATABASE TO $POSTGRES_USER;"

info "Creating Whisper virtual environment"
mkdir -p "$LOCAL_DIR"
if [[ ! -x "$LOCAL_DIR/whisper-venv/bin/python" ]]; then
  "$PYTHON_BIN" -m venv "$LOCAL_DIR/whisper-venv"
fi
"$LOCAL_DIR/whisper-venv/bin/python" -m pip install --upgrade pip
"$LOCAL_DIR/whisper-venv/bin/python" -m pip install -r "$WHISPER_DIR/requirements.txt"

info "Waiting for Ollama"
for _ in {1..30}; do
  if curl --fail --silent --show-error http://localhost:11434/api/tags >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
curl --fail --silent --show-error http://localhost:11434/api/tags >/dev/null 2>&1 || fail "Ollama did not become ready on localhost:11434. Check: brew services list"

if [[ "${SKIP_OLLAMA_MODEL_PULL:-0}" != "1" ]]; then
  info "Downloading Ollama model $OLLAMA_MODEL"
  ollama pull "$OLLAMA_MODEL"
else
  printf 'Skipping Ollama model download because SKIP_OLLAMA_MODEL_PULL=1.\n'
fi

mkdir -p "$LOCAL_DIR/google-tokens"
if [[ ! -e "$ENV_FILE" ]]; then
  cp "$SCRIPT_DIR/local.env.example" "$ENV_FILE"
  chmod 600 "$ENV_FILE"
  printf '\nCreated %s. Replace the Google OAuth placeholder values before testing sign-in.\n' "$ENV_FILE"
else
  printf '\nKeeping existing %s.\n' "$ENV_FILE"
fi

printf '\nLocal dependencies are ready. Next steps:\n'
printf '  1. cd %s\n' "$PROJECT_ROOT"
printf '  2. source .env.local\n'
printf '  3. ./scripts/local/start-dependencies.sh\n'
printf '  4. ./mvnw spring-boot:run\n'
printf '  5. In another terminal: cd ../voice-assistent-frontend && npm ci && npm run dev\n'
