# CLAUDE.md — Röstassistent

> Todos, röstkommandon och Google-synk för ditt konto.

## Vad projektet gör

Röstassistent är en webbapp där vem som helst med ett Google-konto kan fånga
uppgifter och möten med **röst, inklistrad text eller ett foto av ett pappersformulär**,
granska vad assistenten föreslår och sedan synka det godkända till **Google Tasks** och
**Google Calendar**.

### Problemet

Information om saker man måste göra kommer i ostrukturerad form: långa mejl från skola,
myndigheter och föreningar, lappar och blanketter, eller en tanke man har i bilen. Att
manuellt plocka ut datum, deadlines och uppgifter och föra in dem i kalendern tar tid och
leder till missade saker. Appen gör den överföringen åt användaren — men skapar aldrig
något utan att användaren först har godkänt det.

### Målbild (kursens sex veckor)

Projektet startade som en personlig röstassistent. Målet är att göra om den till en
produkt som andra kan använda:

1. Onboarding för nya användare: logga in med Google, koppla Tasks/Calendar, tydlig
   integritetstext.
2. Planeringsvy: dagens/veckans uppgifter och möten på ett ställe, prioritering och
   förslag på när uppgifter kan göras (första steget mot en produktivitetsplanerare).
3. Stabil drift på publik URL med HTTPS, backup och enkel övervakning.
4. README med skärmdumpar, arkitekturdiagram och instruktion för att köra lokalt.

Live: https://voice-assistant.duckdns.org

### Repon

| Repo | Innehåll |
|---|---|
| `voice-assistant-backend` (detta) | Spring Boot-API, OAuth, LLM/OCR/Whisper-integration, `local-whisper-service/` |
| `voice-assistent-frontend` | React-appen |
| `voice-assistant-backend-gitops` | Helm-chart, miljöer (dev/staging/production), Tekton CI, backup |

Ändringar i drift och Kubernetes görs i gitops-repot, inte här.

## Teknikstack och varför

| Del | Val | Varför |
|---|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Security OAuth2, Spring Data JPA | Min huvudkompetens; mogen OAuth2-klient för Google |
| Databas | PostgreSQL | Relationsdata per användare, redan i drift |
| LLM | Ollama (lokalt, via Spring AI) | Användarnas text lämnar inte servern; ingen kostnad per anrop |
| Tal till text | Whisper (FastAPI + faster-whisper), egen tjänst | Bra på svenska, körs lokalt |
| OCR | Tesseract (svenska + engelska) i backend-containern | Bilden skickas aldrig till någon extern tjänst |
| Frontend | React 18, Vite, Tailwind, Zustand | Lätt, snabb build, enkel state |
| Integrationer | Google Tasks API, Google Calendar API | Användarna har redan sina listor och kalendrar där |
| Drift | Raspberry Pi, MicroK8s, Helm, Argo CD (GitOps), cert-manager, NGINX Ingress | Billig egen hosting, deklarativ deploy |

## Arkitekturprinciper

- **Granska före skapa.** Inget skrivs till databasen eller Google förrän användaren har
  godkänt ett förslag. Analys-endpoints returnerar bara förslag.
- **All extern I/O i egna tjänsteklasser.** Google, Ollama, Whisper och Tesseract nås bara
  via sina service-klasser bakom ett interface — aldrig direkt från controllers.
- **Controllers är tunna.** Validering och HTTP-mappning i controllern, logik i services.
- **DTO:er vid API-gränsen.** JPA-entiteter returneras aldrig direkt från ett endpoint.
- **All data tillhör en användare.** Varje fråga mot `TodoItem`, `Meeting`, `FormScan` och
  tokens filtreras på inloggad användare. Ingen endpoint får läsa en annan användares data.
- **LLM-svar är opålitliga.** Allt från modellen valideras och efterbehandlas
  (datum, brådska, deadlines) innan det visas. Hitta aldrig på exakta deadlines för fraser
  som "så snart som möjligt".
- **Tid och datum:** ISO-8601, standardtidszon `Europe/Stockholm`.
- **Minsta möjliga lagring.** Originalbilder raderas efter OCR; ljud sparas inte.
- **Funktioner gör en sak.** Hellre en ny liten klass än en växande service.
- **Hemligheter bara i miljön.** Kubernetes Secrets/miljövariabler — aldrig i kod,
  values-filer, git-remotes eller loggar.
- **Tester för ny logik.** Ny parsing/efterbehandling får enhetstester (`./mvnw test`).

## Saker agenten INTE ska göra

- Byt inte databas, LLM-leverantör eller frontend-ramverk utan att fråga.
- Skicka inte användardata (text, ljud, bilder) till externa AI-tjänster.
- Lägg inte till nya beroenden (Maven eller npm) utan att fråga först.
- Ändra inte Google OAuth-scopes utan att fråga — nya scopes påverkar Googles
  verifieringskrav och vad testanvändare måste godkänna.
- Skapa inte automatiska skrivningar till Google Tasks/Calendar som hoppar över
  godkännandesteget.
- Committa aldrig hemligheter, tokens eller `.env`-filer. Lägg aldrig token i en git-URL.
- Ändra inte produktionsvärden i gitops-repot (Helm values, Ingress, certifikat) utan att fråga.
- Ändra inte databasschema destruktivt (droppa kolumner/tabeller) utan migreringsplan.
- Pusha inte, force-pusha inte och skriv inte om git-historik utan uttrycklig begäran.

## Känd begränsning

Google-appen kör i OAuth-läget **Testing**: bara användare som lagts till som testanvändare
i Google Cloud Console (max 100) kan logga in, och Tasks/Calendar-scopes kräver Googles
verifiering innan appen kan öppnas för alla. Den som vill testa ber om att bli tillagd.
