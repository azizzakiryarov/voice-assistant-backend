package com.voiceassistant.service;

import com.voiceassistant.dto.TranscriptionResponseDTO;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TranscriptionServiceRealAudioIT {

    @Test
    void transcribesRealAudioThroughWhisperService() throws Exception {
        String whisperBaseUrl = System.getProperty("whisper.baseUrl");
        String audioPath = System.getProperty("audio.path");

        Assumptions.assumeTrue(whisperBaseUrl != null && !whisperBaseUrl.isBlank(),
                "Set -Dwhisper.baseUrl to run this integration test");
        Assumptions.assumeTrue(audioPath != null && !audioPath.isBlank(),
                "Set -Daudio.path to run this integration test");

        Path path = Path.of(audioPath);
        MultipartFile audioFile = new MockMultipartFile(
                "file",
                path.getFileName().toString(),
                "audio/aiff",
                Files.readAllBytes(path)
        );

        TranscriptionService service = new TranscriptionService(new RestTemplate(), whisperBaseUrl);

        TranscriptionResponseDTO response = service.transcribeAudio(audioFile);

        assertThat(response.getTranscription()).isNotBlank();
    }
}
