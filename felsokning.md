# Hemuppgift 3, del A: Felsökning av Google Tasks-synken

Gren: `fix/google-tasks-mirror`.

## Problemet
När jag klickade på "Synka med Google" dök uppgifter upp i appen som redan var färdiga.
Målet är att appen och Google Tasks ska spegla varandra.

## Hur problemet beskrevs för agenten
Agenten fick först uppdraget att föreslå en bugg och läste kod, git-historik och `review.md`.
Sedan gav jag symptomet ovan och målbilden, och åtkomst till Pi. Jag kunde inte säga när felet
började. Från produktion hämtades bara aggregerade siffror i en skrivskyddad session, inte
innehållet i uppgifterna.

## Agentens hypoteser
Agentens första gissning, före mitt symptom, var dubbletter på grund av `@default`. Efter symptomet:

1. **Envägssynk.** Klarmarkeringar i appen når inte Google, så nästa synk öppnar uppgiften igen.
2. **Klara uppgifter importeras.** Anropet använder `showCompleted=true` och `showHidden=true`.
3. **Kopior.** Appen sparar list-id `@default`, men synken söker med det riktiga list-id:t.

## Vald hypotes och varför
Hypotes 2, utifrån produktionsdata: 21 av 22 importerade uppgifter var klara.
Agenten körde kontrollen innan jag hade valt hypotes, så valet var inte oberoende av resultatet.

## Vad som faktiskt var fel
- **Importen tog med gammal historik.** Klara uppgifter importerades som nya.
- **Synken speglade inte allt.** Manuella uppgifter och röstuppgifter skickades aldrig till Google,
  och ändringar och raderingar gick inte över åt något håll utöver importens uppdateringar.
  22 av 44 uppgifter saknade koppling till Google.
- **Två dolda fel.** Hypotes 1 och 3 följer av koden, men hade inte gett spår i datan:
  inga dubbletter fanns, eftersom ingen sparad rad hade skapats från appen. De hade blivit
  synliga så fort appen började skicka uppgifter.

## Rättningen
- Klara uppgifter som inte redan finns i appen hoppas över. Finns de uppdateras statusen.
- Synken känner igen uppgifter på uppgifts-id, inte på list-id.
- Skapa, ändra, klarmarkera och radera i appen skickas till Google.
- En lokal rad raderas bara när Google uttryckligen markerar uppgiften som borttagen.

## Vad agenten fick rätt och vad den missade
**Rätt:** den pekade ut rätt kodställen, och varnade för att `showCompleted=false` skulle ha
hindrat klarmarkeringar i Google från att nå appen. Den såg också att rättningarna hänger ihop.

**Fel eller vilseledande:**
- Dubbletter var fel startpunkt: 0 i datan och inte mitt symptom.
- Hypotes 1 rankades först utan belägg, och datan flyttade hypotes 2 till toppen.
- Agenten antog att raderingar inte skulle speglas. Jag ville ha full spegling, så det ändrades.
- Testerna skrevs först, men bara ett av de tre som föll bevisade felet direkt (klara uppgifter
  hoppas över). De andra två föll delvis för att den gamla koden anropade en annan
  repository-metod än den som mockades.

## Verifiering och begränsningar
`./mvnw test`: 78 tester, 0 fel. Google är mockat i testerna, så rättningen är **inte** provad
mot riktiga Google Tasks eller i produktion.

- Google → appen sker bara vid klick på synk, inte automatiskt.
- Google behåller troligen raderade uppgifter i svaret bara en begränsad tid. En radering kan
  missas om det går lång tid mellan synkningarna.
- Om Google inte kan nås loggas felet, och nästa synk kan skriva över den lokala ändringen.
- Det gamla flödet (`/text`, `/confirm-email`) skickar inte uppgifter till Google, eftersom det saknar
  godkännandesteg och `CLAUDE.md` förbjuder automatiska skrivningar utan godkännande.
- De 22 gamla lokala uppgifterna skickas inte upp, och de 21 importerade klara ligger kvar.
- Access-token förnyas inte, så speglingen misslyckas tyst när token har gått ut. Det är inte åtgärdat.
