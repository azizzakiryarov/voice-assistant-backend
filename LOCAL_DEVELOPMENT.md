# Local macOS development

The Raspberry Pi deployment contains PostgreSQL 14, Ollama with `llama3.2:1b`,
and the local Whisper API. On a Mac the same dependencies run directly on the
host; Kubernetes and Docker are not needed for development.

## One-time setup

Homebrew must already be installed. Provision everything else from the backend
directory:

```bash
./scripts/local/setup-mac.sh
```

The script installs PostgreSQL 14, Ollama, Python 3.11, FFmpeg, Java 21 and
Node.js; starts PostgreSQL and Ollama as user services; creates the local-only
`voiceassistant` role and database; creates the Whisper virtual environment;
installs Tesseract OCR with Swedish/English language data and downloads `llama3.2:1b`.

The model download requires disk space. To provision the rest first, run:

```bash
SKIP_OLLAMA_MODEL_PULL=1 ./scripts/local/setup-mac.sh
```

## Run locally

First configure Google OAuth in `.env.local`, which is created by the setup
script. The Google OAuth client must allow this redirect URI:

```text
http://localhost:8081/login/oauth2/code/google
```

Then start dependencies and the backend:

```bash
cd voice-assistant-backend
source .env.local
./scripts/local/start-dependencies.sh
./mvnw spring-boot:run
```

In a second terminal, start the frontend:

```bash
cd voice-assistent-frontend
npm ci
npm run dev
```

Open <http://localhost:5173>. Vite proxies API and OAuth requests to the local
backend on port 8081. The local Spring profile uses PostgreSQL on 5432, Ollama
on 11434, and Whisper on 9000.

Whisper stays running in the background. Its PID and logs are stored in
`voice-assistant-backend/.local/`; terminate it with `kill "$(cat
.local/whisper.pid)"` when needed. PostgreSQL and Ollama are managed with
`brew services`.

## Form scanning

Form scanning runs Tesseract OCR in the backend container, then sends only the
extracted text to the existing local Ollama chat model for interpretation. The
image is deleted immediately after OCR; only the extracted text and review draft
are saved in PostgreSQL. No separate vision model is kept in Ollama memory.

Use a well-lit, straight-on JPEG, PNG or WebP image. The scanner accepts up to
10 MB and always requires user approval before it creates local records, Google
Tasks, or Google Calendar events.
