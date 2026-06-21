# voice-assistant-backend

Spring Boot backend for a voice assistant that can transcribe audio, create todo items, and book calendar events.

## Text Analysis

The backend also supports local LLM analysis of pasted text such as school, authority, work, or association emails.

- `POST /api/text-analysis` analyzes text with the local Ollama model and returns structured suggestions only. It does not persist anything.
- `POST /api/text-analysis/approve` persists the user-approved and possibly edited suggestions as local `Meeting` and `TodoItem` records for the current authenticated user.
- Calendar suggestions are synced to Google Calendar when the current user has a Calendar token.
- Todo suggestions are synced to Google Tasks when the current user has a Tasks token.

The analysis response separates calendar events, todos, informational items, and warnings. Dates are represented as ISO-8601 strings, default timezone is `Europe/Stockholm`, and exact deadlines are not invented for phrases such as "så snart som möjligt".

## Build and Test

```bash
./mvnw test
./mvnw package
```

## Runtime Requirements

The default configuration expects these services and settings:

- PostgreSQL at `postgres:5432`
- Ollama at `http://ollama-svc:11434`
- Whisper transcription service at `http://whisper:9000`
- `GOOGLE_CALENDAR_CLIENT_ID`
- `GOOGLE_CALENDAR_CLIENT_SECRET`

For local development outside Docker/Kubernetes, override the service URLs with environment variables or Spring properties.

For the complete macOS dependency setup and local run commands, see
[LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md).
