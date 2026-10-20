# Hemuppgift 2, del B: Kodgranskning och säkerhet

Granskning av `voice-assistant-backend` med Claude Code i tre separata prompter: arkitektur,
säkerhet och kodkvalitet. Varje fynd har kontrollerats mot koden innan det rapporterades,
för att minska risken för hallucinationer. Radnumren gäller `main` vid granskningen (`d4c5faf`).

Gren: `review/del-b`. Minst ett fynd per kategori är åtgärdat, en commit per kategori.

## 1. Arkitektur

Prompt: *Granska kodbasen mot de arkitekturprinciper jag definierat i CLAUDE.md. Var bryter koden
mot principerna? Var är den i linje med dem?*

### Var koden bryter mot principerna

| Princip | Fynd |
|---|---|
| Granska före skapa | `POST /text` och `POST /confirm-email` (`TranscriptionController.java:30`, `:70`) sparar direkt i databasen och Google Calendar via `CommandProcessorService.processCommand` (`:132`, `:434`, `:450-454`). |
| DTO:er vid API-gränsen | `VoiceCommandApprovalResponseDTO.saved` var `Object` och innehöll JPA-entiteten (`CommandProcessorService.java:383`, `:422`). `processTodoItem`/`processMeeting` returnerar entiteter (`:435`, `:456`). `MeetingRequestDTO`/`MeetingResponseDTO` innehåller entiteten `Participants`. |
| Extern I/O bakom interface | Bara `ImageOcrService` har ett interface. `TodosController.java:52-55` anropar `GoogleTasksService` direkt. |
| Tunna controllers | Många `try/catch` i controllers (TextAnalysis 7, FormScan 5, Transcription 4). `CommandProcessorService` returnerar `ResponseEntity`. |
| Funktioner gör en sak | `CommandProcessorService` (469 rader), `TextAnalysisService` (10 beroenden). Heuristik som är skräddarsydd för ett enda skolmejl (`TextAnalysisService.java:193-202`). |
| LLM-svar är opålitliga | `isMeetingInvalid` (`CommandProcessorService.java:463`) kontrollerar inte att sluttiden kommer efter starttiden. |
| Minsta lagring | `FormScanService.java:89-92` sparar barnets namn och OCR-text utan gallring. |
| Tester för ny logik | Tester saknas bland annat för `TextAnalysisHeuristicExtractor`, `FormScanInterpreter`, `GoogleCalendarService` och `MeetingServiceImpl`. |

### Var koden är i linje med principerna
Ägarfiltrering i alla repositories, tidszonen på ett ställe (`AppTimeZone`, från del A),
hemligheter via `${…}` i konfigurationen och bildvalidering före OCR.

### Åtgärdat: `b214b84`
Röstgodkännandet returnerar nu `TodoItemResponseDTO`/`MeetingResponseDTO` i stället för entiteter.
Mappningen som `TextAnalysisService` hade privat flyttades till `Mapper` och återanvänds.
JSON-formatet är oförändrat, och frontend läser bara `type` och `googleSynced`.
Verifiering: det uppdaterade testet misslyckades före ändringen, och ett nytt test täcker TODO-vägen.

## 2. Säkerhet

Prompt: *Leta efter hårdkodade hemligheter, saknad inputvalidering, injection-risker,
känsliga data i loggar och osäkra standardvärden.*

| # | Fil:rad | Fynd | Allvar |
|---|---|---|---|
| S1 | `application.yaml:6` | Reservvärdet `password` för databaslösenordet. Appen startar i stället för att stoppa när variabeln saknas. | Medel |
| S2 | `application.yaml:5` | Mitt användarnamn som reservvärde för databasanvändaren. | Låg |
| S3 | `config`, `HEAD`, `index` … i roten | Git-interna filer är committade sedan första commiten. Ingen token finns i dem (kontrollerat). | Låg |
| S4 | `src/test/resources/text-analysis/…txt` | Ett riktigt skolmejl används som testdata och behöver granskas manuellt. | Låg–medel |
| V1 | `TextAnalysisController.java:67`, `FormScanController.java:42` | Godkännandet saknar `@Valid` och har ingen gräns för antal poster, så en request kan skapa obegränsat många Google-event. | Medel |
| V2 | `TextAnalysisEventDTO`, `TextAnalysisTodoDTO` | Inga `@Size` på textfälten. | Låg–medel |
| V3 | `TextAnalysisService.java:236-239` | Ett ogiltigt datum ger 500 i stället för 400. | Låg |
| V5 | `CommandProcessorService.java:463` | Sluttid före starttid accepteras. | Låg |
| L1 | `TranscriptionService.java:82` | Whispers svarskropp loggas vid fel och kan innehålla transkriberat tal. | Medel |
| L2 | `GoogleCalendarService.java:44-45` | Titeln och länken för varje kalenderevent loggas. | Medel |
| L3 | `GoogleTasksService.java:196` | Googles felsvar hamnar i undantaget och loggas med stacktrace. | Låg |
| L4 | `application.yaml:33` | `show-sql: true` även i drift. | Låg |
| D2 | `application.yaml:32` | `ddl-auto: update` i basprofilen, som även gäller i produktion. | Medel |
| D3 | `application-prod.yaml` | Filen är tom, så inga inställningar skärps i produktion. | Låg |

**Injection:** inga verifierade fynd. JPQL använder parametrar, Tesseract startas med
`ProcessBuilder(List)` utan skal, och temporära filnamn kommer inte från användaren.

**Bra:** inga tokens eller nycklar i koden eller i git-historiken, `secure` och `http-only`
på sessionscookien, CSRF aktiverat, CORS låst och Actuator begränsat till `health` och `info`.

### Åtgärdat: `c124cbc` (L1 och L2)
Loggarna innehåller nu bara status och längd på svarskroppen respektive eventets id.
Verifiering: ett nytt test fångar loggen och misslyckades på den gamla koden.

## 3. Kodkvalitet

Prompt: *Identifiera funktioner som gör mer än en sak, otydlig namngivning, saknad felhantering
för externa anrop och kod som är svår att läsa utan kommentarer. Lista de fem allvarligaste.*

1. **`TextAnalysisService.approve` (`:131-170`)** validerar, sparar och synkar till Google i en
   transaktion. Om en senare post är ogiltig rullas databasen tillbaka, men events som redan
   skapats i Google ligger kvar. En ny godkännande-request skapar dem igen.
2. **Fel från externa anrop sväljs utan loggning** (`TextAnalysisService.java:292`,
   `GoogleTasksService.java:114`, `CommandProcessorService.java:416`). `RestTemplate` saknar
   timeout (`RestTemplateConfig.java:11`), så anrop till Whisper och Google kan hänga.
3. **`cleanEmailAddress` (`TranscriptionService.java:121-147`)** ändrar hela transkriptionen, inte
   bara e-postadressen: allt blir gemener, och "at" blir "@". Kommentaren "Återställ originaltext"
   stämmer inte. `translatedText` och `AudioTranslationException` beskriver transkribering felaktigt
   som översättning.
4. **`CommandProcessorService`** har fyra ansvarsområden: regex-tolkning på två språk, det gamla
   flödet, preview/godkännande och HTTP-svar. Regex och specialfall som `"тонировка машины"`
   (`:343`) saknar kommentarer.
5. **Missvisande namn:** `OpenAIService` pratar med Ollama, `Participants` är en enda deltagare och
   `isAuthoritativeSchoolEmailExtraction` hoppar över LLM:en för ett specifikt mejl utan förklaring.

### Åtgärdat: `ea96cea` (punkt 2, loggning)
De tre `catch`-blocken loggar nu en varning med id och undantagstyp eller HTTP-status, men inte
meddelandet, eftersom Googles felmeddelanden kan innehålla eventdata.
Verifiering: ett test kontrollerar att varningen loggas och inte innehåller deltagarens namn.

## Vad agenten missade eller inte kunde verifiera

- **Trolig IDOR, ej verifierad.** `MeetingServiceImpl.java:66` mappar deltagare inklusive `id`
  från requesten, och `Meeting.participants` har `cascade = ALL`. En användare kan troligen skriva
  över en annan användares deltagare genom att skicka ett främmande id. Det behöver ett test.
- **Interna tjänster.** Whisper-tjänsten saknar autentisering. Det är bara acceptabelt om nätverket
  i Kubernetes är stängt, vilket gitops-repot inte granskades för.
- **S1 kräver gitops.** Innan reservlösenordet tas bort behöver vi kontrollera att produktion
  sätter `POSTGRES_PASSWORD`. Annars startar inte appen.
- **Arkitekturgranskningen såg inte felet med `approve` och transaktionen** (kodkvalitet punkt 1).
  Det hittades först när prompten frågade specifikt efter felhantering runt externa anrop.

## Resultat

- `./mvnw test`: 63 tester, 0 fel (61 före och 2 nya; ett befintligt test skärptes).
- `./mvnw -DskipTests package`: bygger.
- Inga API-kontrakt som frontend läser har ändrats.
