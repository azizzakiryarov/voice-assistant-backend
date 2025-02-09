package com.voiceassistant.service;

import com.theokanning.openai.completion.chat.ChatCompletionRequest;
import com.theokanning.openai.completion.chat.ChatMessage;
import com.theokanning.openai.service.OpenAiService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OpenAIService {

    private final OpenAiService openAiService;

    public String analyzeCommand(String text) {
        var request = ChatCompletionRequest.builder()
                .model("gpt-3.5-turbo")
                .messages(List.of(new ChatMessage("user",
                        "Analyze this command and return JSON with type (TODO or MEETING) and details: " + text)))
                .build();

        var response = openAiService.createChatCompletion(request);
        return response.getChoices().get(0).getMessage().getContent();
    }
}