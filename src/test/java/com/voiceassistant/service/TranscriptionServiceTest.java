package com.voiceassistant.service;

import com.voiceassistant.dto.TranscriptionResponseDTO;
import com.voiceassistant.exception.AudioTranslationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TranscriptionServiceTest {

    private RestTemplate restTemplate;
    private MultipartFile audioFile;
    private TranscriptionService transcriptionService;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        audioFile = mock(MultipartFile.class);
        transcriptionService = new TranscriptionService(restTemplate, "http://localhost:9000", "sv");
    }

    @Test
    void transcribeAudioReturnsTranscriptionAndExtractedEmail() throws Exception {
        when(audioFile.isEmpty()).thenReturn(false);
        when(audioFile.getBytes()).thenReturn("audio".getBytes(StandardCharsets.UTF_8));
        when(audioFile.getOriginalFilename()).thenReturn("sample.wav");
        when(restTemplate.postForEntity(eq("http://localhost:9000/v1/audio/transcriptions"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"text\":\"Ring mig på test dot example at gmail dot com\"}", HttpStatus.OK));

        TranscriptionResponseDTO response = transcriptionService.transcribeAudio(audioFile);

        assertThat(response.getTranscription()).isEqualTo("ring mig på test.example@gmail.com");
        assertThat(response.getExtractedEmail()).isEqualTo("test.example@gmail.com");
    }

    @Test
    void transcribeAudioSendsConfiguredDefaultLanguage() throws Exception {
        when(audioFile.isEmpty()).thenReturn(false);
        when(audioFile.getBytes()).thenReturn("audio".getBytes(StandardCharsets.UTF_8));
        when(audioFile.getOriginalFilename()).thenReturn("sample.webm");
        when(restTemplate.postForEntity(eq("http://localhost:9000/v1/audio/transcriptions"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"text\":\"Lägg till att köpa mjölk imorgon\"}", HttpStatus.OK));

        transcriptionService.transcribeAudio(audioFile);

        ArgumentCaptor<HttpEntity<MultiValueMap<String, Object>>> requestCaptor = ArgumentCaptor.captor();
        verify(restTemplate).postForEntity(
                eq("http://localhost:9000/v1/audio/transcriptions"),
                requestCaptor.capture(),
                eq(String.class));

        assertThat(requestCaptor.getValue().getBody()).containsEntry("language", java.util.List.of("sv"));
    }

    @Test
    void transcribeAudioUsesRequestedLanguageWhenProvided() throws Exception {
        when(audioFile.isEmpty()).thenReturn(false);
        when(audioFile.getBytes()).thenReturn("audio".getBytes(StandardCharsets.UTF_8));
        when(audioFile.getOriginalFilename()).thenReturn("sample.webm");
        when(restTemplate.postForEntity(eq("http://localhost:9000/v1/audio/transcriptions"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"text\":\"добавь купить молоко завтра\"}", HttpStatus.OK));

        transcriptionService.transcribeAudio(audioFile, "ru");

        ArgumentCaptor<HttpEntity<MultiValueMap<String, Object>>> requestCaptor = ArgumentCaptor.captor();
        verify(restTemplate).postForEntity(
                eq("http://localhost:9000/v1/audio/transcriptions"),
                requestCaptor.capture(),
                eq(String.class));

        assertThat(requestCaptor.getValue().getBody()).containsEntry("language", java.util.List.of("ru"));
    }

    @Test
    void transcribeAudioCanLeaveLanguageOnWhisperAutodetect() throws Exception {
        when(audioFile.isEmpty()).thenReturn(false);
        when(audioFile.getBytes()).thenReturn("audio".getBytes(StandardCharsets.UTF_8));
        when(audioFile.getOriginalFilename()).thenReturn("sample.webm");
        when(restTemplate.postForEntity(eq("http://localhost:9000/v1/audio/transcriptions"), any(), eq(String.class)))
                .thenReturn(new ResponseEntity<>("{\"text\":\"hello\"}", HttpStatus.OK));

        transcriptionService.transcribeAudio(audioFile, "auto");

        ArgumentCaptor<HttpEntity<MultiValueMap<String, Object>>> requestCaptor = ArgumentCaptor.captor();
        verify(restTemplate).postForEntity(
                eq("http://localhost:9000/v1/audio/transcriptions"),
                requestCaptor.capture(),
                eq(String.class));

        assertThat(requestCaptor.getValue().getBody()).doesNotContainKey("language");
    }

    @Test
    void transcribeAudioRejectsEmptyFile() {
        when(audioFile.isEmpty()).thenReturn(true);

        assertThatThrownBy(() -> transcriptionService.transcribeAudio(audioFile))
                .isInstanceOf(AudioTranslationException.class)
                .hasMessageContaining("missing or empty");
    }
}
