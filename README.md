# voice-assistant-backend

Spring Boot backend for a voice assistant that can transcribe audio, create todo items, and book calendar events.

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
