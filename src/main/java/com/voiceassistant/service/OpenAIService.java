package com.voiceassistant.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.exception.TextAnalysisException;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.dto.VoiceCommandPreviewDTO;
import com.voiceassistant.dto.VoiceCommandType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.ollama.api.OllamaOptions;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

@Slf4j
@Service
public class OpenAIService {

    private final ChatClient chatClient;
    private final OllamaOptions textAnalysisOptions;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private enum CommandType {
        TODO,
        MEETING
    }

    private static final Map<String, Object> TEXT_ANALYSIS_JSON_SCHEMA = textAnalysisJsonSchema();

    // Tydliga instruktioner för AI
    private static final String TYPE_PROMPT = """
        Du är en AI som ska analysera en användares kommando och avgöra om det handlar om:
        
        1. En att-göra-uppgift ("TODO")
        2. En mötesbokning ("MEETING")
        
        Returnera ENDAST orden "TODO" eller "MEETING" baserat på vad kommandot handlar om. Inget annat.
        
        Exempel:
        - "Lägg till att köpa mjölk imorgon" → TODO
        - "Boka ett möte med Anna på måndag klockan 10" → MEETING
        
        Användarkommandot är: 
        """;

    private static final String DETAILS_PROMPT =
            "Analysera detta kommando och returnera JSON med detaljer: ";

    private static final String STRUCTURED_PROMPT = """
        Extract a voice command. Språk: svenska, engelska eller ryska. Today: %s.
        Return ONLY compact valid JSON, no markdown, no explanation.
        Schema:
        {"type":"TODO|MEETING|UNKNOWN","todo":{"description":"string","dueDate":"YYYY-MM-DD|null","completed":false},"meeting":{"title":"string","startTimestamp":"YYYY-MM-DDTHH:mm:ss|null","endTimestamp":"YYYY-MM-DDTHH:mm:ss|null","participants":[{"name":"string","email":"string|null"}]},"message":"string|null"}
        Rules: TODO => meeting null. MEETING => todo null. UNKNOWN => both null. If meeting end is missing, use start + 1 hour. Interpret relative dates from today. Keep strings short.
        Command:
        """;

    private static final String TEXT_ANALYSIS_SYSTEM_PROMPT = """
        Du analyserar längre användartexter lokalt för en privat assistent.
        Returnera ENDAST giltig kompakt JSON. Använd inte markdown, kodblock eller förklarande text.
        Skicka aldrig data vidare till externa tjänster.

        JSON-schema:
        {
          "summary":"string",
          "language":"sv|en|mixed|unknown",
          "events":[
            {
              "title":"string",
              "description":"string|null",
              "startDateTime":"ISO-8601 date or date-time string",
              "endDateTime":"ISO-8601 date or date-time string|null",
              "allDay":true,
              "location":"string|null",
              "category":"SCHOOL|WORK|FAMILY|HEALTH|FINANCE|TRAVEL|AUTHORITY|MEETING|OTHER",
              "urgency":"CRITICAL|HIGH|MEDIUM|LOW",
              "confidence":0.0,
              "requiresConfirmation":true,
              "sourceText":"short exact supporting quote from the input"
            }
          ],
          "todos":[
            {
              "title":"string",
              "description":"string|null",
              "deadline":"ISO-8601 date or date-time string|null",
              "deadlineType":"EXACT_DATE|EXACT_DATE_TIME|AS_SOON_AS_POSSIBLE|EARLIEST_CONVENIENCE|RECURRING|NONE|UNKNOWN",
              "recurrence":{"frequency":"DAILY|WEEKLY|MONTHLY|YEARLY|CUSTOM","interval":1},
              "category":"SCHOOL|WORK|FAMILY|HEALTH|FINANCE|TRAVEL|AUTHORITY|MEETING|OTHER",
              "urgency":"CRITICAL|HIGH|MEDIUM|LOW",
              "confidence":0.0,
              "requiresConfirmation":true,
              "sourceText":"short exact supporting quote from the input"
            }
          ],
          "informationalItems":[
            {
              "title":"string",
              "description":"string|null",
              "category":"SCHOOL|WORK|FAMILY|HEALTH|FINANCE|TRAVEL|AUTHORITY|MEETING|OTHER",
              "sourceText":"short exact supporting quote from the input"
            }
          ],
          "warnings":[
            {"code":"string","message":"string","requiresUserAttention":true}
          ]
        }

        Regler:
        - Skilj mellan kalenderhändelser, uppgifter och ren information.
        - Håll svaret kort och kompakt. Returnera högst 6 events, 6 todos och 6 informationalItems.
        - sourceText ska vara ett kort citat från input, max 120 tecken.
        - description ska vara kort, max 180 tecken.
        - Skapa inte todo av reklam, signatur, hälsningsfraser eller kontaktuppgifter.
        - Dubbletter ska tas bort, även om samma information finns på svenska och engelska.
        - Hitta inte på datum, tider, platser eller deadlines.
        - Om bara dag och månad anges, härled år från receivedAt och sammanhang; lägg till warning YEAR_INFERRED.
        - Om texten säger "så snart som möjligt", "as soon as possible" eller liknande: deadline null och deadlineType AS_SOON_AS_POSSIBLE.
        - Om texten säger "regelbundet" eller "regularly": markera som RECURRING och kräva bekräftelse.
        - Markera datum som verkar ha passerat med warning DATE_IN_PAST eller DEADLINE_IN_PAST.
        - Allergier, specialkost, säkerhet och myndighetskrav ska normalt ha högre urgency.
        - Informationsposter ska inte bli todos om användaren inte behöver göra något.
        - Alla event och todos ska ha requiresConfirmation true.
        """;

    public OpenAIService(ChatClient.Builder chatClient) {
        this(chatClient, "30m", 4096, 4, 2600);
    }

    @Autowired
    public OpenAIService(
            ChatClient.Builder chatClient,
            @Value("${app.text-analysis.ollama.keep-alive:30m}") String keepAlive,
            @Value("${app.text-analysis.ollama.num-ctx:4096}") int numCtx,
            @Value("${app.text-analysis.ollama.num-thread:4}") int numThread,
            @Value("${app.text-analysis.ollama.num-predict:2600}") int numPredict) {
        this.chatClient = chatClient.build();
        this.textAnalysisOptions = OllamaOptions.builder()
                .format(TEXT_ANALYSIS_JSON_SCHEMA)
                .keepAlive(keepAlive)
                .numCtx(numCtx)
                .numThread(numThread)
                .numPredict(numPredict)
                .temperature(0.1)
                .topP(0.8)
                .build();
    }

    public Object analyzeCommand(String text) {
        try {
            // Första API-anropet för att avgöra typen
            String commandType = Objects.requireNonNull(chatClient.prompt()
                            .user(TYPE_PROMPT + text)
                            .call()
                            .content())
                    .trim();

            log.info("AI command type raw response: {}", commandType);

            CommandType resolvedCommandType = resolveCommandType(commandType);
            if (resolvedCommandType == null) {
                log.warn("Kunde inte tolka AI-kommandotyp: {}", commandType);
                return null;
            }

            // Andra API-anropet för att hämta detaljerna
            if (resolvedCommandType == CommandType.TODO) {
                return chatClient.prompt()
                        .user(DETAILS_PROMPT + text)
                        .call()
                        .entity(TodoItem.class);
            } else {
                return chatClient.prompt()
                        .user(DETAILS_PROMPT + text)
                        .call()
                        .entity(Meeting.class);
            }
        } catch (Exception e) {
            log.warn("Fel vid analys av kommando: " + e.getMessage());
            return null;
        }
    }

    public VoiceCommandPreviewDTO analyzeVoiceCommand(String text) {
        VoiceCommandPreviewDTO fallback = new VoiceCommandPreviewDTO();
        fallback.setType(VoiceCommandType.UNKNOWN);
        fallback.setMessage("Kunde inte tolka kommandot");

        try {
            String prompt = String.format(STRUCTURED_PROMPT, LocalDate.now()) + text;
            String response = Objects.requireNonNull(chatClient.prompt()
                            .user(prompt)
                            .call()
                            .content())
                    .trim();

            VoiceCommandPreviewDTO preview = objectMapper.readValue(cleanJson(response), VoiceCommandPreviewDTO.class);
            if (preview.getType() == null) {
                preview.setType(VoiceCommandType.UNKNOWN);
            }
            if (preview.getType() == VoiceCommandType.MEETING && preview.getMeeting() == null) {
                preview.setType(VoiceCommandType.UNKNOWN);
                preview.setMessage("Kunde inte extrahera mötesdetaljer");
            } else if (preview.getType() == VoiceCommandType.TODO && preview.getTodo() == null) {
                preview.setType(VoiceCommandType.UNKNOWN);
                preview.setMessage("Kunde inte extrahera att göra-detaljer");
            }
            return preview;
        } catch (Exception e) {
            log.warn("Fel vid strukturerad analys av röstkommando: {}", e.getMessage());
            return fallback;
        }
    }

    public TextAnalysisResponseDTO analyzeText(TextAnalysisRequestDTO request) {
        String prompt = buildTextAnalysisPrompt(request, null);
        RuntimeException lastFailure = null;

        for (int attempt = 0; attempt < 2; attempt++) {
            String response = null;
            try {
                response = Objects.requireNonNull(chatClient.prompt()
                                .options(textAnalysisOptions)
                                .user(prompt)
                                .call()
                                .content())
                        .trim();
                return objectMapper.readValue(cleanJson(response), TextAnalysisResponseDTO.class);
            } catch (Exception e) {
                log.warn(
                        "Invalid text analysis JSON attempt={} responseLength={} completeJson={} cause={}",
                        attempt + 1,
                        response == null ? 0 : response.length(),
                        response != null && isCompleteJson(response),
                        e.getClass().getSimpleName());
                lastFailure = new TextAnalysisException("LLM returned invalid text analysis JSON", e);
                prompt = buildTextAnalysisPrompt(request, "Förra svaret var inte giltig JSON. Returnera en kortare komplett JSON enligt schemat. Kapa inte svaret mitt i JSON.");
            }
        }

        throw lastFailure == null
                ? new TextAnalysisException("LLM text analysis failed")
                : lastFailure;
    }

    private String buildTextAnalysisPrompt(TextAnalysisRequestDTO request, String retryInstruction) {
        String retry = retryInstruction == null ? "" : "\nRetry-instruktion: " + retryInstruction + "\n";
        return TEXT_ANALYSIS_SYSTEM_PROMPT + retry + """

            Metadata:
            title: %s
            sourceType: %s
            receivedAt: %s
            timeZone: %s

            Text:
            %s
            """.formatted(
                request.title(),
                request.sourceType(),
                request.receivedAt(),
                request.timeZone(),
                request.text()
        );
    }

    private String cleanJson(String response) {
        String cleaned = response.trim();
        if (cleaned.startsWith("```")) {
            cleaned = cleaned.replaceFirst("^```(?:json)?\\s*", "");
            cleaned = cleaned.replaceFirst("\\s*```$", "");
        }
        int start = cleaned.indexOf('{');
        int end = cleaned.lastIndexOf('}');
        if (start >= 0 && end >= start) {
            return cleaned.substring(start, end + 1);
        }
        return cleaned;
    }

    private boolean isCompleteJson(String response) {
        String cleaned = cleanJson(response);
        return cleaned.startsWith("{") && cleaned.endsWith("}");
    }

    private static Map<String, Object> textAnalysisJsonSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("summary", "language", "events", "todos", "informationalItems", "warnings"),
                "properties", Map.of(
                        "summary", Map.of("type", "string", "maxLength", 240),
                        "language", Map.of("type", "string", "enum", List.of("sv", "en", "mixed", "unknown")),
                        "events", arraySchema(eventSchema(), 6),
                        "todos", arraySchema(todoSchema(), 6),
                        "informationalItems", arraySchema(informationalItemSchema(), 6),
                        "warnings", arraySchema(warningSchema(), 8)
                )
        );
    }

    private static Map<String, Object> eventSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("title", "description", "startDateTime", "endDateTime", "allDay", "location",
                        "category", "urgency", "confidence", "requiresConfirmation", "sourceText"),
                "properties", Map.ofEntries(
                        Map.entry("title", stringSchema(120)),
                        Map.entry("description", nullableStringSchema(180)),
                        Map.entry("startDateTime", stringSchema(40)),
                        Map.entry("endDateTime", nullableStringSchema(40)),
                        Map.entry("allDay", Map.of("type", "boolean")),
                        Map.entry("location", nullableStringSchema(160)),
                        Map.entry("category", enumSchema("SCHOOL", "WORK", "FAMILY", "HEALTH", "FINANCE", "TRAVEL", "AUTHORITY", "MEETING", "OTHER")),
                        Map.entry("urgency", enumSchema("CRITICAL", "HIGH", "MEDIUM", "LOW")),
                        Map.entry("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                        Map.entry("requiresConfirmation", Map.of("type", "boolean")),
                        Map.entry("sourceText", stringSchema(120))
                )
        );
    }

    private static Map<String, Object> todoSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("title", "description", "deadline", "deadlineType", "recurrence", "category",
                        "urgency", "confidence", "requiresConfirmation", "sourceText"),
                "properties", Map.ofEntries(
                        Map.entry("title", stringSchema(120)),
                        Map.entry("description", nullableStringSchema(180)),
                        Map.entry("deadline", nullableStringSchema(40)),
                        Map.entry("deadlineType", enumSchema("EXACT_DATE", "EXACT_DATE_TIME", "AS_SOON_AS_POSSIBLE", "EARLIEST_CONVENIENCE", "RECURRING", "NONE", "UNKNOWN")),
                        Map.entry("recurrence", nullableObjectSchema(recurrenceSchema())),
                        Map.entry("category", enumSchema("SCHOOL", "WORK", "FAMILY", "HEALTH", "FINANCE", "TRAVEL", "AUTHORITY", "MEETING", "OTHER")),
                        Map.entry("urgency", enumSchema("CRITICAL", "HIGH", "MEDIUM", "LOW")),
                        Map.entry("confidence", Map.of("type", "number", "minimum", 0, "maximum", 1)),
                        Map.entry("requiresConfirmation", Map.of("type", "boolean")),
                        Map.entry("sourceText", stringSchema(120))
                )
        );
    }

    private static Map<String, Object> recurrenceSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("frequency", "interval"),
                "properties", Map.of(
                        "frequency", enumSchema("DAILY", "WEEKLY", "MONTHLY", "YEARLY", "CUSTOM"),
                        "interval", Map.of("type", "integer", "minimum", 1, "maximum", 365)
                )
        );
    }

    private static Map<String, Object> informationalItemSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("title", "description", "category", "sourceText"),
                "properties", Map.of(
                        "title", stringSchema(120),
                        "description", nullableStringSchema(180),
                        "category", enumSchema("SCHOOL", "WORK", "FAMILY", "HEALTH", "FINANCE", "TRAVEL", "AUTHORITY", "MEETING", "OTHER"),
                        "sourceText", stringSchema(120)
                )
        );
    }

    private static Map<String, Object> warningSchema() {
        return Map.of(
                "type", "object",
                "additionalProperties", false,
                "required", List.of("code", "message", "requiresUserAttention"),
                "properties", Map.of(
                        "code", stringSchema(80),
                        "message", stringSchema(180),
                        "requiresUserAttention", Map.of("type", "boolean")
                )
        );
    }

    private static Map<String, Object> arraySchema(Map<String, Object> itemSchema, int maxItems) {
        return Map.of("type", "array", "maxItems", maxItems, "items", itemSchema);
    }

    private static Map<String, Object> stringSchema(int maxLength) {
        return Map.of("type", "string", "maxLength", maxLength);
    }

    private static Map<String, Object> nullableStringSchema(int maxLength) {
        return Map.of("type", List.of("string", "null"), "maxLength", maxLength);
    }

    private static Map<String, Object> nullableObjectSchema(Map<String, Object> objectSchema) {
        return Map.of("anyOf", List.of(objectSchema, Map.of("type", "null")));
    }

    private static Map<String, Object> enumSchema(String... values) {
        return Map.of("type", "string", "enum", List.of(values));
    }

    private CommandType resolveCommandType(String commandType) {
        String normalizedCommandType = commandType
                .trim()
                .replace("\"", "")
                .replace("'", "")
                .replace(".", "")
                .toUpperCase(Locale.ROOT);

        boolean containsTodo = normalizedCommandType.contains(CommandType.TODO.name());
        boolean containsMeeting = normalizedCommandType.contains(CommandType.MEETING.name());

        if (containsTodo == containsMeeting) {
            return null;
        }

        return containsTodo ? CommandType.TODO : CommandType.MEETING;
    }
}
