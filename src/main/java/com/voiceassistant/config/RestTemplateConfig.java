package com.voiceassistant.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    /**
     * Shared client for Whisper and Google Tasks. Without timeouts a hung service would block
     * the request thread forever. The read timeout matches the frontend's 180 s wait for voice
     * commands, because local Whisper on the Raspberry Pi can need more than a minute.
     */
    @Bean
    public RestTemplate restTemplate(
            @Value("${app.http.connect-timeout-seconds:10}") int connectTimeoutSeconds,
            @Value("${app.http.read-timeout-seconds:180}") int readTimeoutSeconds) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(connectTimeoutSeconds));
        requestFactory.setReadTimeout(Duration.ofSeconds(readTimeoutSeconds));
        return new RestTemplate(requestFactory);
    }
}
