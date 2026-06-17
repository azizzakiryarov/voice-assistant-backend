package com.voiceassistant.integration.google.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceassistant.dto.GoogleTasksSyncResultDTO;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.TodoRepository;
import com.voiceassistant.service.AppUserService;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class GoogleTasksService {

    private static final String TASKS_BASE_URL = "https://tasks.googleapis.com/tasks/v1";

    private final OAuth2AuthorizedClientService authorizedClientService;
    private final AppUserService appUserService;
    private final TodoRepository todoRepository;
    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    public GoogleTasksService(
            OAuth2AuthorizedClientService authorizedClientService,
            AppUserService appUserService,
            TodoRepository todoRepository,
            RestTemplate restTemplate,
            ObjectMapper objectMapper) {
        this.authorizedClientService = authorizedClientService;
        this.appUserService = appUserService;
        this.todoRepository = todoRepository;
        this.restTemplate = restTemplate;
        this.objectMapper = objectMapper;
    }

    public GoogleTasksSyncResultDTO importCurrentUserTasks() {
        AppUser owner = appUserService.getCurrentUser();
        HttpEntity<Void> requestEntity = authorizedRequest();
        GoogleTasksSyncResultDTO result = new GoogleTasksSyncResultDTO();

        JsonNode taskLists = getJson(TASKS_BASE_URL + "/users/@me/lists", requestEntity);
        for (JsonNode taskList : taskLists.path("items")) {
            String taskListId = textOrNull(taskList, "id");
            if (taskListId == null) {
                continue;
            }
            importTaskList(owner, taskListId, requestEntity, result);
        }

        return result;
    }

    public boolean hasCurrentUserTasksToken() {
        OAuth2AuthorizedClient authorizedClient = currentAuthorizedClient();
        return authorizedClient != null && authorizedClient.getAccessToken() != null;
    }

    public boolean createTaskIfConnected(TodoItem todoItem) {
        if (!hasCurrentUserTasksToken()) {
            return false;
        }

        String[] descriptionParts = splitDescription(todoItem.getDescription());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("title", descriptionParts[0]);
        if (!descriptionParts[1].isBlank()) {
            body.put("notes", descriptionParts[1]);
        }
        if (todoItem.getDueDate() != null) {
            body.put("due", todoItem.getDueDate()
                    .atStartOfDay()
                    .atOffset(ZoneOffset.UTC)
                    .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME));
        }

        try {
            HttpHeaders headers = authorizedHeaders();
            HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
            ResponseEntity<String> response = restTemplate.exchange(
                    TASKS_BASE_URL + "/lists/@default/tasks",
                    HttpMethod.POST,
                    requestEntity,
                    String.class);
            JsonNode created = objectMapper.readTree(response.getBody());
            todoItem.setGoogleTaskListId("@default");
            todoItem.setGoogleTaskId(textOrNull(created, "id"));
            todoItem.setGooglePosition(textOrNull(created, "position"));
            todoItem.setGoogleUpdatedAt(parseOffsetDateTime(textOrNull(created, "updated")));
            todoItem.setSyncStatus("GOOGLE_TASKS_CREATED");
            todoRepository.save(todoItem);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private void importTaskList(
            AppUser owner,
            String taskListId,
            HttpEntity<Void> requestEntity,
            GoogleTasksSyncResultDTO result) {
        String pageToken = null;
        do {
            String url = UriComponentsBuilder
                    .fromHttpUrl(TASKS_BASE_URL + "/lists/{taskListId}/tasks")
                    .queryParam("showCompleted", true)
                    .queryParam("showDeleted", false)
                    .queryParam("showHidden", true)
                    .queryParamIfPresent("pageToken", java.util.Optional.ofNullable(pageToken))
                    .buildAndExpand(taskListId)
                    .toUriString();

            JsonNode tasks = getJson(url, requestEntity);
            Iterator<JsonNode> iterator = tasks.path("items").elements();
            while (iterator.hasNext()) {
                syncTask(owner, taskListId, iterator.next(), result);
            }
            pageToken = textOrNull(tasks, "nextPageToken");
        } while (pageToken != null);
    }

    private void syncTask(AppUser owner, String taskListId, JsonNode task, GoogleTasksSyncResultDTO result) {
        String taskId = textOrNull(task, "id");
        String title = textOrNull(task, "title");
        String notes = textOrNull(task, "notes");
        if (taskId == null || title == null || title.isBlank()) {
            result.setSkippedCount(result.getSkippedCount() + 1);
            return;
        }

        boolean existing = true;
        TodoItem todoItem = todoRepository
                .findByOwnerIdAndGoogleTaskListIdAndGoogleTaskId(owner.getId(), taskListId, taskId)
                .orElseGet(() -> {
                    TodoItem created = new TodoItem();
                    created.setOwner(owner);
                    created.setGoogleTaskListId(taskListId);
                    created.setGoogleTaskId(taskId);
                    return created;
                });
        if (todoItem.getId() == null) {
            existing = false;
        }

        todoItem.setDescription(buildDescription(title, notes));
        todoItem.setDueDate(parseDueDate(textOrNull(task, "due")));
        todoItem.setCompleted("completed".equals(textOrNull(task, "status")));
        todoItem.setGooglePosition(textOrNull(task, "position"));
        todoItem.setGoogleUpdatedAt(parseOffsetDateTime(textOrNull(task, "updated")));
        todoItem.setSyncStatus("GOOGLE_TASKS_IMPORTED");
        todoRepository.save(todoItem);

        if (existing) {
            result.setUpdatedCount(result.getUpdatedCount() + 1);
        } else {
            result.setImportedCount(result.getImportedCount() + 1);
        }
    }

    static String buildDescription(String title, String notes) {
        String trimmedTitle = title == null ? "" : title.trim();
        String trimmedNotes = notes == null ? "" : notes.trim();
        if (trimmedNotes.isBlank()) {
            return trimmedTitle;
        }
        return trimmedTitle + "\n" + trimmedNotes;
    }

    private JsonNode getJson(String url, HttpEntity<Void> requestEntity) {
        try {
            ResponseEntity<String> response = restTemplate.exchange(url, HttpMethod.GET, requestEntity, String.class);
            return objectMapper.readTree(response.getBody());
        } catch (HttpStatusCodeException e) {
            throw new IllegalStateException("Google Tasks API error: " + e.getStatusCode() + " " + e.getResponseBodyAsString(), e);
        } catch (Exception e) {
            throw new IllegalStateException("Could not import Google Tasks", e);
        }
    }

    private HttpEntity<Void> authorizedRequest() {
        return new HttpEntity<>(authorizedHeaders());
    }

    private HttpHeaders authorizedHeaders() {
        OAuth2AuthorizedClient authorizedClient = currentAuthorizedClient();
        if (authorizedClient == null || authorizedClient.getAccessToken() == null) {
            throw new IllegalStateException("Google Tasks is not connected for current user");
        }
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(authorizedClient.getAccessToken().getTokenValue());
        headers.setContentType(MediaType.APPLICATION_JSON);
        return headers;
    }

    private OAuth2AuthorizedClient currentAuthorizedClient() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof OAuth2AuthenticationToken oauthToken)) {
            return null;
        }
        return authorizedClientService.loadAuthorizedClient(
                oauthToken.getAuthorizedClientRegistrationId(),
                oauthToken.getName());
    }

    private String textOrNull(JsonNode node, String fieldName) {
        JsonNode value = node.path(fieldName);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        return value.asText();
    }

    private LocalDate parseDueDate(String due) {
        if (due == null || due.length() < 10) {
            return null;
        }
        return LocalDate.parse(due.substring(0, 10));
    }

    private OffsetDateTime parseOffsetDateTime(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return OffsetDateTime.parse(value);
    }

    private String[] splitDescription(String description) {
        String trimmed = description == null ? "" : description.trim();
        if (trimmed.isBlank()) {
            return new String[]{"Ny uppgift", ""};
        }
        String[] lines = trimmed.split("\\R", 2);
        return new String[]{lines[0], lines.length > 1 ? lines[1].trim() : ""};
    }
}
