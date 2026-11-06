# Hemuppgift 4, steg 3: Polering

Genomgång av backend och frontend efter kursens polerings-prompt. Radnumren gäller `main` vid
`cc0d676`, före ändringarna. Hemligheter kontrollerades separat (se längst ner).

## Fynd

| # | Kategori | Fil:rad | Fynd | Åtgärd | Status |
|---|---|---|---|---|---|
| 1 | Felhantering | `config/RestTemplateConfig.java:11` | `RestTemplate` saknar timeout, så ett hängande anrop till Whisper eller Google blockerar tråden för alltid. | Timeout, konfigurerbar via `HTTP_CONNECT_TIMEOUT_SECONDS` och `HTTP_READ_TIMEOUT_SECONDS`. | Åtgärdat |
| 2 | Felmeddelande | `exception/GlobalExceptionHandler.java:79` | Ett ogiltigt datum i ett godkännande (`TextAnalysisDateResolver.java:52`, `:70`) ger 500 och "An unexpected error occurred". | `DateTimeException` ger 400 och "Ogiltigt datum eller tid …". | Åtgärdat |
| 3 | Edge case | `service/CommandProcessorService.java:258` | Ett datum som inte finns, som "31 februari", kraschar röstförhandsvisningen. | Datumet ignoreras och kommandot visas som okänt. | Åtgärdat |
| 4 | Edge case | `service/CommandProcessorService.java:297` | Bara "idag" känns igen, inte standardformen "i dag". | Mönster för båda formerna. | Åtgärdat |
| 5 | Felmeddelande | `TaskList.jsx:39` (frontend) | Uppgifter som appen skapat i Google visas som "Lokal uppgift". | Alla `GOOGLE_TASKS_*` visas som "Synkad med Google Tasks". | Åtgärdat |
| 6 | Konfiguration | `application.yaml:5-6` | Reservvärden för databasens användare och lösenord, så appen startar utan hemligheter. | Reservvärdena borttagna. Produktion sätter dem via `postgres-secret` (kontrollerat). | Åtgärdat |
| 7 | Felmeddelande | `service/TodoItemServiceImpl.java:43` | "Title are required fields" är fel både i grammatik och i fältnamn. | "Beskrivning krävs för en uppgift". | Åtgärdat |
| 8 | Felhantering | `GoogleTasksService.java:297`, `GoogleCalendarService.java:68` | Access-token förnyas aldrig, så synken misslyckas tyst efter ungefär en timme. | Använd `OAuth2AuthorizedClientManager` med refresh-token. Kräver `access_type=offline` och nytt samtycke. | Kvar, ändrar OAuth-flödet |
| 9 | Edge case | `src/api.js:98`, `:149` (frontend) | Godkännanden görs om automatiskt vid 502/503, vilket kan skapa dubbletter i Google. | Ta bort omförsök för POST som skapar data. | Kvar |
| 10 | Felmeddelande | `GlobalExceptionHandler.java:79`, `TextAnalysisJobService.java:64` | Engelska felmeddelanden visas i ett svenskt gränssnitt. | Översätt meddelanden som når användaren. | Kvar |
| 11 | Dokumentation | `service/TextAnalysisService.java:194` | `isAuthoritativeSchoolEmailExtraction` hoppar över LLM:en för ett specifikt mejl utan förklaring. | Kommentar om varför, eller ta bort specialfallet. | Kvar |
| 12 | Hårdkodat | `application.yaml:4`, `:42` | Adresserna till databasen och Ollama är fasta i basprofilen (den lokala profilen läser från miljön). | Läs från `POSTGRES_URL` och `OLLAMA_BASE_URL` med samma standardvärden. | Kvar, låg |

## Verifiering

- **Före ändringen:** de nya testerna för fynd 3 och 4 misslyckades på den gamla koden
  (`DateTimeException: Invalid date 'FEBRUARY 31'`, respektive att "i dag" inte kändes igen).
- **Fynd 1:** ett test öppnar en server som aldrig svarar och kontrollerar att anropet avbryts.
- **Fynd 2:** ett test kontrollerar status 400 och meddelandet.
- **Resultat:** `./mvnw test` gav 82 tester och 0 fel, och `npm run lint` gick igenom i frontend.

## Hemligheter

Alla tre repon och deras hela git-historik söktes igenom efter Google-, GitHub- och AWS-nycklar,
privata nycklar och OAuth-tokens. Det gav inga träffar. Två lösenordsliknande värden hittades, men
båda är avsiktliga:

- `scripts/local/setup-mac.sh:12` innehåller lösenordet till den lokala utvecklingsdatabasen.
- `SecurityConfigTest.java:30` innehåller ett testvärde.

Produktionens hemligheter ligger i Kubernetes Secrets. Inga `.env`-filer, nycklar eller tokens finns
i något repo. Hemligheten `openai-api-secret` sätts fortfarande i backendens deployment, men koden
använder den inte och den kan tas bort.
