package com.voiceassistant.service;

import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import groovy.util.logging.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

import java.util.Objects;

@lombok.extern.slf4j.Slf4j
@Slf4j
@Service
public class OpenAIService {

    private final ChatClient chatClient;

    // Tydliga instruktioner för AI
    private static final String TYPE_PROMPT =
            "Analysera texten och returnera endast 'TODO' eller 'MEETING' baserat på vilken typ av kommando det är: ";

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
                            .entity(String.class))
                    .trim();

            // Bearbeta resultatet för att hantera olika svarsformat
            boolean isTodo = commandType.toUpperCase().contains("TODO");

            // Andra API-anropet för att hämta detaljerna
            if (isTodo) {
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
}