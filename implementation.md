# Hemuppgift 2, del A: Implementation med plan

Refaktorering av teknisk skuld i `voice-assistant-backend` med Claude Code.
Arbetssätt: först analys utan lösning, sedan en plan i planeringsläget ("implementera ingenting"),
granskning av planen och till sist implementation ett steg i taget, med tester efter varje steg.

Gren: `refactor/tech-debt-del-a`. Utgångsläge: `./mvnw test` grönt med 58 tester.

## Vad identifierade agenten?

Agenten läste CLAUDE.md, controllers, services och tester och jämförde koden mot
arkitekturprinciperna. Fynden, sorterade efter allvar:

1. **Ett gammalt flöde hoppar över "granska före skapa".** `POST /text` och `POST /confirm-email`
   sparar direkt i databasen och Google Calendar utan godkännande. Frontend använder dem inte i praktiken:
   `EmailVerificationModal` kan aldrig öppnas.
2. **Tidszonsbugg.** `Mapper` räknade om mötestider med serverns tidszon men märkte eventet med
   `Europe/Stockholm`. I en UTC-container hamnade röstmöten 1–2 timmar fel. Tidszonen var
   definierad på tre ställen.
3. **JPA-entiteter returnerades från API:t**, trots att CLAUDE.md säger "DTO:er vid API-gränsen".
4. **Spridd felhantering.** `IllegalArgumentException` från services blev 500 i stället för 400.
5. **Duplicerad logik i Google-integrationen**, och Google-tjänsterna låg inte bakom interface.
6. **Synkstatus som lösa strängar** (`"LOCAL"`, `"GOOGLE_TASKS_CREATED"` …) på flera ställen.
7. **Stora klasser med blandat ansvar** (`CommandProcessorService`, 469 rader) och heuristik
   som är skräddarsydd för ett enda skolmejl.
8. Mindre saker: död kod, kopierade hjälpmetoder och e-poststädning som skrev om hela transkriptionen.

## Vad som implementerades

| Steg | Commit | Ändring | Verifiering |
|---|---|---|---|
| 1 | `ead85fd` | Ny `AppTimeZone`; `Mapper` använder Stockholmszonen; `LocalDate.now()` får tidszon | Nytt `MapperTest` misslyckade först (2 h fel i UTC), grönt efter fixen |
| 2 | `b6f0f60` | Enumen `SyncStatus` ersätter strängarna; DB-kolumn och DTO förblir `String` | Nytt `SyncStatusTest` låser namnen som frontend jämför mot |
| 3 | `d923cae` | `GlobalExceptionHandler` ger 400 för `IllegalArgumentException` | Nytt `TodosControllerTest` gav först 500, nu 400 |

## Vad ville agenten göra som jag stoppade, och varför?

- **Ändringar i alla tre repon.** Punkt 1 krävde egentligen att även frontend ändrades (modal och `api.js`).
  Jag begränsade arbetet till backend för att hålla ändringen liten och granskningsbar.
- **Steg 4: ta bort det gamla flödet (`/text`, `/confirm-email`, `analyzeCommand`).** Jag stoppade
  kodändringarna efter steg 3. Steget var störst, ändrade API:t och tog bort åtta tester.
  Det ligger kvar som känd skuld.
- **Avgränsningar i planen:** ingen sammanslagning av de två funktionerna som skapar Google-event, ingen flytt av
  all `try/catch` från controllers (svarsformatet till frontend hade ändrats) och inget byte av
  `syncStatus` till `@Enumerated` i databasen (Hibernate kan lägga på CHECK-constraints).

## Vad missade agenten?

- **Planen var inte komplett.** `TextAnalysisHeuristicExtractor` och `CommandProcessorServiceTest`
  använde också den gamla tidszonskonstanten. De upptäcktes med grep under implementationen.
- **Beroendet till frontend.** Att `App.jsx` och `TaskListItem.jsx` jämför mot `'GOOGLE_TASKS_IMPORTED'`
  upptäcktes först i steg 2. Därför lades ett extra test till som inte fanns i planen.
- **En frontend-bugg som analysen inte såg.** `TaskListItem.jsx` visar "Lokal uppgift" för
  `GOOGLE_TASKS_CREATED`, så uppgifter som har skickats till Google märks som lokala.
- **En risk i steg 3.** Handlern skickar `exception.getMessage()` till klienten. Om ett bibliotek kastar
  `IllegalArgumentException` kan interna detaljer läcka. Ett eget valideringsundantag vore säkrare.

## Är projektet körbart efter implementationen?

Ja, verifierat med tester och bygge:

- `./mvnw test`: 61 tester, 0 fel (58 före och 3 nya).
- `./mvnw -DskipTests package`: bygger.

Inga endpoints som frontend anropar har ändrat kontrakt. Det enda synliga beteendet som ändrats är
att mötestider via röstflödet blir rätt i UTC-miljöer och att valideringsfel ger 400 i stället för 500.
Appen är inte manuellt testad i webbläsaren. Det görs enligt `LOCAL_DEVELOPMENT.md`: röstkommando,
textanalys och att skapa en todo, och sedan kontrollera mötestiden i Google Calendar.

## Kvarvarande skuld

Punkterna 1, 3, 5, 7 och 8 från analysen, samt frontend-buggen ovan.
