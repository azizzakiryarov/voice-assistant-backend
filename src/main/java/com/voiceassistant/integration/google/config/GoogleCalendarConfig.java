package com.voiceassistant.integration.google.config;

import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.calendar.Calendar;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.io.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import com.google.gson.Gson;

@Configuration
public class GoogleCalendarConfig {

    private static final JsonFactory JSON_FACTORY = GsonFactory.getDefaultInstance();
    private static final String TOKEN_FILE_NAME = "device_tokens.json";
    private static final List<String> SCOPES = List.of(
            "https://www.googleapis.com/auth/calendar"
    );
    private static final String DEVICE_CODE_ENDPOINT = "https://oauth2.googleapis.com/device/code";
    private static final String TOKEN_ENDPOINT = "https://oauth2.googleapis.com/token";

    @Value("${google.calendar.application-name}")
    private String applicationName;

    @Value("${google.calendar.tokens-directory}")
    private String tokensDirectoryPath;

    @Value("${GOOGLE_CALENDAR_CLIENT_ID}")
    private String clientId;

    @Value("${GOOGLE_CALENDAR_CLIENT_SECRET}")
    private String clientSecret;

    @Bean
    public Calendar googleCalendarClient() throws GeneralSecurityException, IOException, InterruptedException {
        final NetHttpTransport httpTransport = GoogleNetHttpTransport.newTrustedTransport();
        Credential credential = loadOrRequestCredential(httpTransport);
        return new Calendar.Builder(httpTransport, JSON_FACTORY, credential)
                .setApplicationName(applicationName)
                .build();
    }

    private Credential loadOrRequestCredential(NetHttpTransport httpTransport) throws IOException, InterruptedException {
        if (tokensDirectoryPath == null || tokensDirectoryPath.trim().isEmpty()) {
            throw new IllegalStateException("❌ Property 'google.calendar.tokens-directory' is not set.");
        }

        File tokensDir = new File(tokensDirectoryPath);
        if (!tokensDir.exists() && !tokensDir.mkdirs()) {
            throw new IOException("❌ Could not create tokens directory at: " + tokensDirectoryPath);
        }

        File tokenFile = new File(tokensDir, TOKEN_FILE_NAME);
        if (tokenFile.exists()) {
            try (Reader reader = new FileReader(tokenFile)) {
                Map<?, ?> tokenMap = new Gson().fromJson(reader, Map.class);
                return buildCredentialFromMap(tokenMap, httpTransport);
            }
        }

        // Step 1: Request device and user codes
        HttpClient client = HttpClient.newHttpClient();

        if (clientId == null || clientSecret == null) {
            throw new IllegalStateException("❌ clientId or clientSecret is missing. Make sure they are set via environment variables.");
        }

        String scope = String.join(" ", SCOPES);
        String requestBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                + "&scope=" + URLEncoder.encode(scope, StandardCharsets.UTF_8);

        HttpRequest deviceRequest = HttpRequest.newBuilder()
                .uri(URI.create(DEVICE_CODE_ENDPOINT))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                .build();

        HttpResponse<String> deviceResponse = client.send(deviceRequest, HttpResponse.BodyHandlers.ofString());
        Map<String, Object> deviceCodeResponse = new Gson().fromJson(deviceResponse.body(), Map.class);

        String userCode = (String) deviceCodeResponse.get("user_code");
        String verificationUrl = (String) deviceCodeResponse.get("verification_url");
        String deviceCode = (String) deviceCodeResponse.get("device_code");
        Double interval = deviceCodeResponse.get("interval") != null
                ? ((Number) deviceCodeResponse.get("interval")).doubleValue()
                : 5.0;

        System.out.println("\n🔐 Please go to: " + verificationUrl);
        System.out.println("📝 Enter the code: " + userCode);
        System.out.println("⏳ Waiting for authorization...");

        // Step 2: Poll token endpoint
        while (true) {
            Thread.sleep(interval.longValue() * 1000);

            String tokenRequestBody = "client_id=" + URLEncoder.encode(clientId, StandardCharsets.UTF_8)
                    + "&client_secret=" + URLEncoder.encode(clientSecret, StandardCharsets.UTF_8)
                    + "&device_code=" + URLEncoder.encode(deviceCode, StandardCharsets.UTF_8)
                    + "&grant_type=urn:ietf:params:oauth:grant-type:device_code";

            HttpRequest tokenRequest = HttpRequest.newBuilder()
                    .uri(URI.create(TOKEN_ENDPOINT))
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(tokenRequestBody))
                    .build();

            HttpResponse<String> tokenResponse = client.send(tokenRequest, HttpResponse.BodyHandlers.ofString());
            Map<String, Object> tokenMap = new Gson().fromJson(tokenResponse.body(), Map.class);

            if (tokenMap.containsKey("access_token")) {
                try (Writer writer = new FileWriter(tokenFile)) {
                    new Gson().toJson(tokenMap, writer);
                }
                return buildCredentialFromMap(tokenMap, httpTransport);
            }

            String error = (String) tokenMap.get("error");
            if (!"authorization_pending".equals(error)) {
                throw new RuntimeException("❌ Device flow failed: " + tokenResponse.body());
            }
        }
    }


    private Credential buildCredentialFromMap(Map<?, ?> tokenMap, NetHttpTransport httpTransport) {
        Credential credential = new Credential.Builder(BearerToken.authorizationHeaderAccessMethod())
                .setTransport(httpTransport)
                .setJsonFactory(JSON_FACTORY)
                .setClientAuthentication(
                        new com.google.api.client.auth.oauth2.ClientParametersAuthentication(clientId, clientSecret)
                )
                .setTokenServerEncodedUrl(TOKEN_ENDPOINT)
                .build();

        credential.setAccessToken((String) tokenMap.get("access_token"));

        if (tokenMap.get("refresh_token") != null) {
            credential.setRefreshToken((String) tokenMap.get("refresh_token"));
        }

        if (tokenMap.get("expires_in") != null) {
            credential.setExpirationTimeMilliseconds(
                    System.currentTimeMillis() + ((Double) tokenMap.get("expires_in")).longValue() * 1000
            );
        }

        return credential;
    }

}
