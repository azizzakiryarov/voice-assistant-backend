package com.voiceassistant.integration.google.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.voiceassistant.dto.GoogleTasksSyncResultDTO;
import com.voiceassistant.model.AppUser;
import com.voiceassistant.model.TodoItem;
import com.voiceassistant.repository.TodoRepository;
import com.voiceassistant.service.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClientService;
import org.springframework.security.oauth2.client.authentication.OAuth2AuthenticationToken;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpStatus.INTERNAL_SERVER_ERROR;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.NO_CONTENT;

class GoogleTasksServiceTest {

    private static final String BASE_URL = "https://tasks.googleapis.com/tasks/v1";
    private static final String LISTS_RESPONSE = "{\"items\":[{\"id\":\"real-list\"}]}";
    private static final String TASKS_URL = BASE_URL
            + "/lists/real-list/tasks?showCompleted=true&showDeleted=true&showHidden=true";

    private TodoRepository todoRepository;
    private MockRestServiceServer google;
    private GoogleTasksService service;
    private AppUser owner;

    @BeforeEach
    void setUp() {
        todoRepository = mock(TodoRepository.class);
        when(todoRepository.save(any(TodoItem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        owner = new AppUser();
        owner.setId(5L);
        AppUserService appUserService = mock(AppUserService.class);
        when(appUserService.getCurrentUser()).thenReturn(owner);

        OAuth2AuthenticationToken authentication = mock(OAuth2AuthenticationToken.class);
        when(authentication.getAuthorizedClientRegistrationId()).thenReturn("google");
        when(authentication.getName()).thenReturn("user");
        SecurityContextHolder.getContext().setAuthentication(authentication);

        OAuth2AuthorizedClient authorizedClient = mock(OAuth2AuthorizedClient.class);
        when(authorizedClient.getAccessToken()).thenReturn(new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, "token", Instant.now(), Instant.now().plusSeconds(3600)));
        OAuth2AuthorizedClientService authorizedClientService = mock(OAuth2AuthorizedClientService.class);
        when(authorizedClientService.loadAuthorizedClient("google", "user")).thenReturn(authorizedClient);

        RestTemplate restTemplate = new RestTemplate();
        google = MockRestServiceServer.createServer(restTemplate);
        service = new GoogleTasksService(
                authorizedClientService, appUserService, todoRepository, restTemplate, new ObjectMapper());
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void buildDescriptionIncludesGoogleTaskNotesBelowTitle() {
        String description = GoogleTasksService.buildDescription(
                "Book dentist",
                "Call before lunch and ask about next week"
        );

        assertThat(description).isEqualTo("Book dentist\nCall before lunch and ask about next week");
    }

    @Test
    void buildDescriptionUsesOnlyTitleWhenGoogleTaskNotesAreBlank() {
        String description = GoogleTasksService.buildDescription("Book dentist", "   ");

        assertThat(description).isEqualTo("Book dentist");
    }

    @Test
    void importSkipsCompletedTaskThatIsNotAlreadyInTheApp() {
        expectImportOf("{\"id\":\"old-task\",\"title\":\"Gammal\",\"status\":\"completed\"}");
        when(todoRepository.findByOwnerIdAndGoogleTaskId(5L, "old-task")).thenReturn(Optional.empty());

        GoogleTasksSyncResultDTO result = service.importCurrentUserTasks();

        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(todoRepository, never()).save(any(TodoItem.class));
    }

    @Test
    void importStillAddsOpenTaskCreatedInGoogle() {
        expectImportOf("{\"id\":\"new-task\",\"title\":\"Ny\",\"status\":\"needsAction\"}");
        when(todoRepository.findByOwnerIdAndGoogleTaskId(5L, "new-task")).thenReturn(Optional.empty());

        GoogleTasksSyncResultDTO result = service.importCurrentUserTasks();

        assertThat(result.getImportedCount()).isEqualTo(1);
        verify(todoRepository).save(any(TodoItem.class));
    }

    @Test
    void importMarksExistingTaskCompletedWhenItWasCompletedInGoogle() {
        TodoItem existing = todo(7L, "task-1", "real-list");
        expectImportOf("{\"id\":\"task-1\",\"title\":\"Klar\",\"status\":\"completed\"}");
        when(todoRepository.findByOwnerIdAndGoogleTaskId(5L, "task-1")).thenReturn(Optional.of(existing));

        GoogleTasksSyncResultDTO result = service.importCurrentUserTasks();

        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(existing.isCompleted()).isTrue();
    }

    @Test
    void importRecognisesTaskCreatedFromTheAppUnderDefaultList() {
        TodoItem createdFromApp = todo(7L, "task-1", "@default");
        expectImportOf("{\"id\":\"task-1\",\"title\":\"Från appen\",\"status\":\"needsAction\"}");
        when(todoRepository.findByOwnerIdAndGoogleTaskId(5L, "task-1")).thenReturn(Optional.of(createdFromApp));

        GoogleTasksSyncResultDTO result = service.importCurrentUserTasks();

        assertThat(result.getImportedCount()).isZero();
        assertThat(result.getUpdatedCount()).isEqualTo(1);
        assertThat(createdFromApp.getGoogleTaskListId()).isEqualTo("real-list");
    }

    @Test
    void importRemovesLocalTodoWhenTaskWasDeletedInGoogle() {
        TodoItem existing = todo(7L, "task-1", "real-list");
        expectImportOf("{\"id\":\"task-1\",\"title\":\"Borta\",\"deleted\":true}");
        when(todoRepository.findByOwnerIdAndGoogleTaskId(5L, "task-1")).thenReturn(Optional.of(existing));

        GoogleTasksSyncResultDTO result = service.importCurrentUserTasks();

        assertThat(result.getDeletedCount()).isEqualTo(1);
        verify(todoRepository).delete(existing);
    }

    @Test
    void importIgnoresDeletedTaskThatIsNotInTheApp() {
        expectImportOf("{\"id\":\"task-1\",\"title\":\"Borta\",\"deleted\":true}");
        when(todoRepository.findByOwnerIdAndGoogleTaskId(5L, "task-1")).thenReturn(Optional.empty());

        GoogleTasksSyncResultDTO result = service.importCurrentUserTasks();

        assertThat(result.getDeletedCount()).isZero();
        assertThat(result.getSkippedCount()).isEqualTo(1);
        verify(todoRepository, never()).save(any(TodoItem.class));
    }

    @Test
    void updateSendsCompletedStatusToTheLinkedTask() {
        TodoItem todo = todo(7L, "task-1", "real-list");
        todo.setCompleted(true);
        google.expect(requestTo(BASE_URL + "/lists/real-list/tasks/task-1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.id").value("task-1"))
                .andExpect(jsonPath("$.title").value("Uppgift"))
                .andExpect(jsonPath("$.status").value("completed"))
                .andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        assertThat(service.updateTaskIfLinked(todo)).isTrue();
        google.verify();
    }

    @Test
    void updateSkipsLocalTodoWithoutGoogleTask() {
        assertThat(service.updateTaskIfLinked(todo(7L, null, null))).isFalse();
        google.verify();
    }

    @Test
    void updateReturnsFalseWhenGoogleFails() {
        google.expect(requestTo(BASE_URL + "/lists/real-list/tasks/task-1"))
                .andRespond(withStatus(INTERNAL_SERVER_ERROR));

        assertThat(service.updateTaskIfLinked(todo(7L, "task-1", "real-list"))).isFalse();
    }

    @Test
    void deleteRemovesTheLinkedTask() {
        google.expect(requestTo(BASE_URL + "/lists/real-list/tasks/task-1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(NO_CONTENT));

        assertThat(service.deleteTaskIfLinked(todo(7L, "task-1", "real-list"))).isTrue();
        google.verify();
    }

    @Test
    void deleteTreatsAlreadyRemovedTaskAsDone() {
        google.expect(requestTo(BASE_URL + "/lists/real-list/tasks/task-1"))
                .andRespond(withStatus(NOT_FOUND));

        assertThat(service.deleteTaskIfLinked(todo(7L, "task-1", "real-list"))).isTrue();
    }

    private void expectImportOf(String task) {
        google.expect(requestTo(BASE_URL + "/users/@me/lists"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(LISTS_RESPONSE, MediaType.APPLICATION_JSON));
        google.expect(requestTo(TASKS_URL))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("{\"items\":[" + task + "]}", MediaType.APPLICATION_JSON));
    }

    private TodoItem todo(Long id, String googleTaskId, String googleTaskListId) {
        TodoItem todo = new TodoItem();
        todo.setId(id);
        todo.setOwner(owner);
        todo.setDescription("Uppgift");
        todo.setGoogleTaskId(googleTaskId);
        todo.setGoogleTaskListId(googleTaskListId);
        return todo;
    }
}
