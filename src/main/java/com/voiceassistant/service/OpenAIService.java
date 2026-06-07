package com.voiceassistant.service;

import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Locale;
import java.util.Objects;

@Slf4j
@Service
public class OpenAIService {

    private final ChatClient chatClient;

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
