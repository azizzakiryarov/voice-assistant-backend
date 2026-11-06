package com.voiceassistant.config;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.net.ServerSocket;
import java.net.Socket;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RestTemplateConfigTest {

    @Test
    void readTimeoutStopsCallToServiceThatNeverAnswers() throws Exception {
        RestTemplate restTemplate = new RestTemplateConfig().restTemplate(1, 1);

        try (ServerSocket silentServer = new ServerSocket(0)) {
            Thread acceptor = new Thread(() -> {
                try (Socket ignored = silentServer.accept()) {
                    Thread.sleep(5_000);
                } catch (Exception ignored) {
                    // The test only needs a connection that never gets a response.
                }
            });
            acceptor.setDaemon(true);
            acceptor.start();

            assertThatThrownBy(() -> restTemplate.getForObject(
                    "http://localhost:" + silentServer.getLocalPort(), String.class))
                    .isInstanceOf(ResourceAccessException.class);
        }
    }
}
