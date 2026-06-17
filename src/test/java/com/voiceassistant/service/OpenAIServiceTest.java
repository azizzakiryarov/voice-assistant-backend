package com.voiceassistant.service;

import com.voiceassistant.model.Meeting;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.dto.SourceType;
import com.voiceassistant.dto.TextAnalysisRequestDTO;
import com.voiceassistant.dto.TextAnalysisResponseDTO;
import com.voiceassistant.exception.TextAnalysisException;
import com.voiceassistant.dto.VoiceCommandPreviewDTO;
import com.voiceassistant.dto.VoiceCommandType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OpenAIServiceTest {

    private ChatClient.Builder chatClientBuilder;
    private ChatClient chatClient;
    private ChatClient.ChatClientRequestSpec typeRequest;
    private ChatClient.ChatClientRequestSpec detailsRequest;
    private ChatClient.CallResponseSpec typeResponse;
    private ChatClient.CallResponseSpec detailsResponse;
    private OpenAIService openAIService;

    @BeforeEach
    void setUp() {
        chatClientBuilder = mock(ChatClient.Builder.class);
        chatClient = mock(ChatClient.class);
        typeRequest = mock(ChatClient.ChatClientRequestSpec.class);
        detailsRequest = mock(ChatClient.ChatClientRequestSpec.class);
        typeResponse = mock(ChatClient.CallResponseSpec.class);
        detailsResponse = mock(ChatClient.CallResponseSpec.class);

        when(chatClientBuilder.build()).thenReturn(chatClient);
        openAIService = new OpenAIService(chatClientBuilder);
    }

    @Test
    void constructorBuildsChatClientFromBuilder() {
        verify(chatClientBuilder).build();
    }

    @Test
    void analyzeCommandReturnsTodoItemWhenAiClassifiesCommandAsTodo() {
        String command = "Lägg till att köpa mjölk imorgon";
        TodoItem todoItem = new TodoItem();
        todoItem.setDescription("köpa mjölk");

        when(chatClient.prompt()).thenReturn(typeRequest, detailsRequest);
        when(typeRequest.user(anyString())).thenReturn(typeRequest);
        when(typeRequest.call()).thenReturn(typeResponse);
        when(typeResponse.content()).thenReturn("  \"TODO\".  ");
        when(detailsRequest.user(anyString())).thenReturn(detailsRequest);
        when(detailsRequest.call()).thenReturn(detailsResponse);
        when(detailsResponse.entity(TodoItem.class)).thenReturn(todoItem);

        Object result = openAIService.analyzeCommand(command);

        assertThat(result).isSameAs(todoItem);
        verify(detailsResponse).entity(TodoItem.class);
        verify(detailsResponse, never()).entity(Meeting.class);

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(typeRequest).user(userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue())
                .contains("Returnera ENDAST orden \"TODO\" eller \"MEETING\"")
                .endsWith(command);

        verify(detailsRequest).user("Analysera detta kommando och returnera JSON med detaljer: " + command);
    }

    @Test
    void analyzeCommandReturnsMeetingWhenAiDoesNotClassifyCommandAsTodo() {
        String command = "Boka ett möte med Anna på måndag klockan 10";
        Meeting meeting = new Meeting();
        meeting.setTitle("möte med Anna");

        when(chatClient.prompt()).thenReturn(typeRequest, detailsRequest);
        when(typeRequest.user(anyString())).thenReturn(typeRequest);
        when(typeRequest.call()).thenReturn(typeResponse);
        when(typeResponse.content()).thenReturn("MEETING");
        when(detailsRequest.user(anyString())).thenReturn(detailsRequest);
        when(detailsRequest.call()).thenReturn(detailsResponse);
        when(detailsResponse.entity(Meeting.class)).thenReturn(meeting);

        Object result = openAIService.analyzeCommand(command);

        assertThat(result).isSameAs(meeting);
        verify(detailsResponse).entity(Meeting.class);
        verify(detailsResponse, never()).entity(TodoItem.class);
        verify(detailsRequest).user("Analysera detta kommando och returnera JSON med detaljer: " + command);
    }

    @Test
    void analyzeCommandReturnsNullWhenAiResponseContentIsNull() {
        when(chatClient.prompt()).thenReturn(typeRequest);
        when(typeRequest.user(anyString())).thenReturn(typeRequest);
        when(typeRequest.call()).thenReturn(typeResponse);
        when(typeResponse.content()).thenReturn(null);

        Object result = openAIService.analyzeCommand("Lägg till att köpa mjölk");

        assertThat(result).isNull();
        verify(chatClient, times(1)).prompt();
        verify(detailsResponse, never()).entity(TodoItem.class);
        verify(detailsResponse, never()).entity(Meeting.class);
    }

    @Test
    void analyzeCommandReturnsNullWhenAiResponseIsAmbiguous() {
        when(chatClient.prompt()).thenReturn(typeRequest);
        when(typeRequest.user(anyString())).thenReturn(typeRequest);
        when(typeRequest.call()).thenReturn(typeResponse);
        when(typeResponse.content()).thenReturn("TODO eller MEETING");

        Object result = openAIService.analyzeCommand("Planera något");

        assertThat(result).isNull();
        verify(chatClient, times(1)).prompt();
        verify(detailsResponse, never()).entity(TodoItem.class);
        verify(detailsResponse, never()).entity(Meeting.class);
    }

    @Test
    void analyzeCommandReturnsNullWhenChatClientThrowsException() {
        when(chatClient.prompt()).thenThrow(new RuntimeException("AI unavailable"));

        Object result = openAIService.analyzeCommand("Boka ett möte");

        assertThat(result).isNull();
    }

    @Test
    void analyzeVoiceCommandPromptSupportsRussianTodoCommands() {
        String command = "добавь купить молоко завтра";
        String aiResponse = """
                {
                  "type": "TODO",
                  "todo": {
                    "description": "купить молоко",
                    "dueDate": "2026-06-14",
                    "completed": false
                  },
                  "meeting": null,
                  "message": null
                }
                """;

        when(chatClient.prompt()).thenReturn(typeRequest);
        when(typeRequest.user(anyString())).thenReturn(typeRequest);
        when(typeRequest.call()).thenReturn(typeResponse);
        when(typeResponse.content()).thenReturn(aiResponse);

        VoiceCommandPreviewDTO result = openAIService.analyzeVoiceCommand(command);

        assertThat(result.getType()).isEqualTo(VoiceCommandType.TODO);
        assertThat(result.getTodo().getDescription()).isEqualTo("купить молоко");

        ArgumentCaptor<String> userPromptCaptor = ArgumentCaptor.forClass(String.class);
        verify(typeRequest).user(userPromptCaptor.capture());
        assertThat(userPromptCaptor.getValue())
                .contains("svenska, engelska eller ryska")
                .contains("добавь купить молоко завтра")
                .endsWith(command);
    }

    @Test
    void analyzeTextRetriesOnceWhenFirstResponseIsInvalidJson() {
        ChatClient.ChatClientRequestSpec retryRequest = mock(ChatClient.ChatClientRequestSpec.class);
        ChatClient.CallResponseSpec retryResponse = mock(ChatClient.CallResponseSpec.class);

        when(chatClient.prompt()).thenReturn(typeRequest, retryRequest);
        when(typeRequest.options(any())).thenReturn(typeRequest);
        when(typeRequest.user(anyString())).thenReturn(typeRequest);
        when(typeRequest.call()).thenReturn(typeResponse);
        when(typeResponse.content()).thenReturn("not-json");
        when(retryRequest.options(any())).thenReturn(retryRequest);
        when(retryRequest.user(anyString())).thenReturn(retryRequest);
        when(retryRequest.call()).thenReturn(retryResponse);
        when(retryResponse.content()).thenReturn("""
                {
                  "summary": "Information om skolstarten.",
                  "language": "sv",
                  "events": [],
                  "todos": [],
                  "informationalItems": [],
                  "warnings": []
                }
                """);

        TextAnalysisResponseDTO result = openAIService.analyzeText(textAnalysisRequest());

        assertThat(result.summary()).isEqualTo("Information om skolstarten.");
        verify(chatClient, times(2)).prompt();
    }

    @Test
    void analyzeTextThrowsWhenModelNeverReturnsValidJson() {
        when(chatClient.prompt()).thenReturn(typeRequest, detailsRequest);
        when(typeRequest.options(any())).thenReturn(typeRequest);
        when(typeRequest.user(anyString())).thenReturn(typeRequest);
        when(typeRequest.call()).thenReturn(typeResponse);
        when(typeResponse.content()).thenReturn("not-json");
        when(detailsRequest.options(any())).thenReturn(detailsRequest);
        when(detailsRequest.user(anyString())).thenReturn(detailsRequest);
        when(detailsRequest.call()).thenReturn(detailsResponse);
        when(detailsResponse.content()).thenReturn("still-not-json");

        assertThatThrownBy(() -> openAIService.analyzeText(textAnalysisRequest()))
                .isInstanceOf(TextAnalysisException.class)
                .hasMessageContaining("LLM returned invalid text analysis JSON");
    }

    private TextAnalysisRequestDTO textAnalysisRequest() {
        return new TextAnalysisRequestDTO(
                "Mejl från skolan",
                "Första skoldagen är den 17 augusti.",
                SourceType.EMAIL,
                OffsetDateTime.parse("2026-06-17T15:00:00+02:00"),
                "Europe/Stockholm");
    }
}
