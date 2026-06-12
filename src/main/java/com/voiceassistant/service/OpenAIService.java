package com.voiceassistant.service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.dto.VoiceCommandPreviewDTO;
import com.voiceassistant.dto.VoiceCommandType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
public class OpenAIService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
            .build();

    private enum CommandType {
        TODO,
        MEETING
    }

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
        Du analyserar svenska röstkommandon för en röstassistent.
        Dagens datum är %s.

        Returnera ENDAST giltig JSON, utan markdown och utan extra text.
        Välj type: "MEETING", "TODO" eller "UNKNOWN".

        JSON-format:
        {
          "type": "MEETING|TODO|UNKNOWN",
          "todo": {
            "description": "string",
            "dueDate": "YYYY-MM-DD eller null",
            "completed": false
          },
          "meeting": {
            "title": "string",
            "startTimestamp": "YYYY-MM-DDTHH:mm:ss eller null",
            "endTimestamp": "YYYY-MM-DDTHH:mm:ss eller null",
            "participants": [{"name": "string", "email": "string eller null"}]
          },
          "message": "kort förklaring om UNKNOWN eller osäkerhet"
        }

        Regler:
        - Vid MEETING ska todo vara null.
        - Vid TODO ska meeting vara null.
        - Vid UNKNOWN ska både todo och meeting vara null.
        - Om mötets sluttid saknas, sätt endTimestamp till en timme efter startTimestamp.
        - För mötestitel, använd en kort rubrik som passar Google Calendar.
        - Tolka svenska datum och tider, exempel: "den 13 juni klockan tio noll noll".

        Kommando:
        """;

    public OpenAIService(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
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
            return preview;
        } catch (Exception e) {
            log.warn("Fel vid strukturerad analys av röstkommando: {}", e.getMessage());
            return fallback;
        }
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
