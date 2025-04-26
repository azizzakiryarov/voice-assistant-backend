package com.voiceassistant.service;

import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

@Service
public class OpenAIService {

    private final ChatClient chatClient;

    public OpenAIService(ChatClient.Builder chatClient) {
        this.chatClient = chatClient.build();
    }

    public Object analyzeCommand(String text) {

        String answer = chatClient.prompt()
                .user("Analyze this text and return a String if it's looks like a TODO or MEETING command: " + text)
                .call()
                .entity(String.class);

        if (answer.contains("TODO")) {
            return chatClient.prompt()
                    .user("Analyze this command and return JSON with type (TODO or MEETING) and details: " + text)
                    .call()
                    .entity(TodoItem.class);
        } else {
            return chatClient.prompt()
                    .user("Analyze this command and return JSON with type (TODO or MEETING) and details: " + text)
                    .call()
                    .entity(Meeting.class);
        }
    }
}