# Röstassistent

[![CI](https://github.com/azizzakiryarov/voice-assistant-backend/actions/workflows/ci.yml/badge.svg)](https://github.com/azizzakiryarov/voice-assistant-backend/actions/workflows/ci.yml)

Gör om röstkommandon, långa mejl och fotade pappersblanketter till uppgifter och möten i Google
Tasks och Google Calendar, efter att du har granskat och godkänt dem.

Live: <https://voice-assistant.duckdns.org> (logga in med valfritt Google-konto)

## Vad det gör

Du loggar in med Google och spelar in ett röstkommando ("möte i morgon klockan 10"), klistrar in ett
mejl från skolan eller fotar en blankett. Appen föreslår uppgifter och kalenderhändelser med datum,
som du kan ändra innan du godkänner. Godkända uppgifter speglas mot Google Tasks och möten skapas i
Google Calendar. Tal, text och bilder tolkas lokalt på servern och skickas aldrig till en extern
AI-tjänst.

## Använda appen

1. **Logga in.** Öppna <https://voice-assistant.duckdns.org> och klicka på *Logga in med Google*.
   Godkänn att appen får använda Google Tasks och Google Calendar. Eftersom appen inte är
   verifierad av Google visas först en varning: klicka på *Avancerat* och sedan *Fortsätt*.
2. **Lägg till något på ett av tre sätt:**
   - **Röst:** välj språk (SV, RU, EN eller Auto), klicka på mikrofonen, säg till exempel "boka ett
     möte med Anna i morgon klockan 10" och klicka igen för att sluta. Efter en stund visas *Granska
     röstkommando* med transkriberingen och förslaget.
   - **Text:** klistra in ett mejl eller en text under *Analysera text* och klicka på *Analysera text*.
   - **Foto:** välj en bild av en blankett eller lapp under *Skanna formulär* och klicka på
     *Analysera bild*. Under *Visa OCR-text* ser du vad som lästes från bilden.
3. **Granska.** Ändra rubrik, datum, tid, kategori och beskrivning. Bocka ur förslag du inte vill ha
   och kryssa i *Heldag* för händelser utan klockslag. Läs varningarna, till exempel om ett årtal har
   gissats eller ett datum redan har passerat.
4. **Godkänn.** Först nu skapas något. Händelser hamnar i Google Calendar och uppgifter i appens
   lista och i Google Tasks. *Avbryt* skapar ingenting.
5. **Hantera uppgifter.** Lägg till en uppgift direkt i listan, bocka av den som klar eller radera
   den. Ändringen skickas till Google Tasks direkt. Klicka på *Synka Google Tasks* för att hämta det
   du har ändrat i Google.

Ett röstkommando tar upp till en minut. Text och foton tolkas av en liten modell på servern och tar
oftast 2–10 minuter. Låt sidan vara öppen under tiden.

## Kom igång

Instruktionen gäller macOS. Produktion körs på en Raspberry Pi med Kubernetes, se
[voice-assistant-backend-gitops](https://github.com/azizzakiryarov/voice-assistant-backend-gitops).

### Krav

- Java 21, Node.js 22, PostgreSQL 14, Python 3.11 och FFmpeg (för Whisper)
- [Ollama](https://ollama.com) med modellen `llama3.2:1b`
- Tesseract OCR med svenska och engelska språkdata
- Ett OAuth-klient-id i Google Cloud med Calendar API och Tasks API aktiverade, och redirect-URI
  `http://localhost:8081/login/oauth2/code/google`

### Installation

```bash
git clone https://github.com/azizzakiryarov/voice-assistant-backend
git clone https://github.com/azizzakiryarov/voice-assistent-frontend
cd voice-assistant-backend
./scripts/local/setup-mac.sh
```

Skriptet installerar beroendena med Homebrew, skapar den lokala databasen och Whisper-miljön och
laddar ner Ollama-modellen. Med `SKIP_OLLAMA_MODEL_PULL=1` hoppar det över modellen, som är stor.

### Konfiguration

Skriptet skapar `.env.local` från [`scripts/local/local.env.example`](scripts/local/local.env.example).
Fyll i dina egna Google-värden:

```bash
GOOGLE_CALENDAR_CLIENT_ID=ditt-klient-id
GOOGLE_CALENDAR_CLIENT_SECRET=din-klienthemlighet
```

Övriga variabler har lokala standardvärden. I produktion kommer `POSTGRES_USER`,
`POSTGRES_PASSWORD` och Google-värdena från Kubernetes Secrets, och appen startar inte om de saknas.

| Variabel | Standard | Används till |
|---|---|---|
| `OLLAMA_CHAT_MODEL` | `llama3.2:1b` | Modell för att tolka text |
| `WHISPER_BASE_URL` | `http://localhost:9000` | Tal till text |
| `APP_FRONTEND_URL` | `/` | Vart användaren skickas efter inloggning |
| `HTTP_CONNECT_TIMEOUT_SECONDS` | `10` | Timeout för anrop till Whisper och Google Tasks |
| `HTTP_READ_TIMEOUT_SECONDS` | `180` | Väntetid på svar från Whisper och Google Tasks |

### Kör projektet

```bash
# Terminal 1: backend på port 8081
cd voice-assistant-backend
source .env.local
./scripts/local/start-dependencies.sh
./mvnw spring-boot:run

# Terminal 2: frontend på port 5173
cd voice-assistent-frontend
npm ci
npm run dev
```

Öppna <http://localhost:5173>. Enhetstester körs med `./mvnw test`. End-to-end-tester med Playwright
finns i [frontend-repot](https://github.com/azizzakiryarov/voice-assistent-frontend#end-to-end-tester). Mer om lokal drift finns i
[LOCAL_DEVELOPMENT.md](LOCAL_DEVELOPMENT.md).

## Arkitektur

Webbläsaren pratar bara med React-appen och backend. Backend är en Spring Boot-tjänst där tunna
controllers tar emot anropen och logiken ligger i services. All kontakt med Google, Ollama, Whisper
och Tesseract går via egna tjänsteklasser. Analys-endpoints returnerar bara förslag, och inget
sparas eller skickas till Google förrän användaren har godkänt. Allt i databasen tillhör en
inloggad användare. Diagram över driften finns i
[docs/architecture.md](https://github.com/azizzakiryarov/voice-assistant-backend-gitops/blob/main/docs/architecture.md).

```text
voice-assistant-backend/
├── src/main/java/com/voiceassistant/
│   ├── controller/      # REST-endpoints, validering och HTTP-mappning
│   ├── service/         # Tolkning av röst, text och blanketter, godkännande
│   ├── integration/     # Google Calendar och Google Tasks
│   ├── model/           # JPA-entiteter (användare, uppgift, möte, blankett)
│   └── config/          # Säkerhet (OAuth2, CSRF, CORS), HTTP-klient, tidszon
├── local-whisper-service/  # FastAPI + faster-whisper för tal till text
└── scripts/local/       # Lokal installation och start på macOS
```

## Teknisk stack

| Komponent | Val | Anledning |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security OAuth2, Spring Data JPA | Mogen OAuth2-klient för Google |
| Databas | PostgreSQL 14 | Relationsdata per användare |
| Textförståelse | Ollama (`llama3.2:1b`) via Spring AI | Texten lämnar inte servern och kostar inget per anrop |
| Tal till text | Whisper (FastAPI + faster-whisper) | Bra på svenska, körs lokalt |
| OCR | Tesseract | Bilden skickas aldrig vidare och raderas efter OCR |
| Frontend | React 18, Vite, Tailwind CSS, Zustand | Lätt och snabb att bygga |
| Drift | Raspberry Pi, MicroK8s, Helm, Argo CD, cert-manager, NGINX Ingress | Billig egen hosting med deklarativ driftsättning |
| CI/CD | Tekton på Pi:n (lint, tester, bygge, image-scan, driftsättning), GitHub Actions för PR | Kontroller före merge, driftsättning vid push till main |

## Begränsningar

- **Overifierad Google-app:** appen är publicerad, så alla med ett Google-konto kan logga in. Google
  har ännu inte verifierat den, så inloggningen visar en varning (klicka *Avancerat → Fortsätt*), och
  högst 100 konton kan använda den tills verifieringen är klar.
- **Synk från Google sker manuellt:** ändringar i Google Tasks syns i appen först när du klickar på
  *Synka Google Tasks*. Appen skickar sina ändringar direkt.
- **Utgången Google-token:** access-token förnyas inte, så synken kan sluta fungera efter ungefär en
  timme. Logga ut och in igen.
- **Långsam tolkning:** en liten lokal modell (`llama3.2:1b`) på en Raspberry Pi skriver ungefär fyra
  tokens per sekund. Ett röstkommando tar upp till en minut och en text eller ett foto oftast 2–10
  minuter. Modellen hittar ibland på eller upprepar förslag, och därför granskar du alltid innan
  något sparas.
- **Skanning kräver att sidan är öppen:** formulärskanningen är ett enda långt anrop. Laddar du om
  sidan eller tappar anslutningen under tiden försvinner resultatet, och du får skanna igen.
- **Språk:** röstkommandon är testade på svenska och ryska.
- **En server:** allt körs på en Raspberry Pi utan redundans. Databasen säkerhetskopieras med restic.

## Utvecklat med

Claude Code (agentdriven utveckling) som del av kursen Next-Generation Software Development with AI.
Granskningar och felsökning från kursen finns i [review.md](review.md), [felsokning.md](felsokning.md)
och [polering.md](polering.md).
