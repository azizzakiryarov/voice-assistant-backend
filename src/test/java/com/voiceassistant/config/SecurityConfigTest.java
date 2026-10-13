package com.voiceassistant.config;

import com.voiceassistant.controller.TodosController;
import com.voiceassistant.integration.google.service.GoogleTasksService;
import com.voiceassistant.service.AppUserService;
import com.voiceassistant.service.TodoItemServiceImpl;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TodosController.class)
@Import({SecurityConfig.class, WebConfig.class})
@TestPropertySource(properties = {
        "GOOGLE_CALENDAR_CLIENT_ID=test-client",
        "GOOGLE_CALENDAR_CLIENT_SECRET=test-secret",
        "CORS_ALLOWED_ORIGINS=https://voice-assistant.example"
})
class SecurityConfigTest {

    private static final String TODOS = "/api/voice-assistent/todos";
    private static final String BODY = "{\"description\":\"Köp mjölk\"}";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TodoItemServiceImpl todoItemService;

    @MockitoBean
    private GoogleTasksService googleTasksService;

    @MockitoBean
    private AppUserService appUserService;

    @Test
    void issuesReadableCsrfCookie() throws Exception {
        MvcResult result = mockMvc.perform(get(TODOS)).andReturn();

        Cookie cookie = result.getResponse().getCookie("XSRF-TOKEN");
        assertThat(cookie).isNotNull();
        assertThat(cookie.isHttpOnly()).isFalse();
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Lax");
    }

    @Test
    void rejectsStateChangingRequestWithoutCsrfToken() throws Exception {
        mockMvc.perform(post(TODOS).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden());
    }

    @Test
    void acceptsCsrfTokenFromCookieInHeader() throws Exception {
        Cookie csrfCookie = mockMvc.perform(get(TODOS)).andReturn().getResponse().getCookie("XSRF-TOKEN");

        // A valid token passes the CSRF filter; the anonymous request is then sent to login instead of 403.
        mockMvc.perform(post(TODOS)
                        .cookie(csrfCookie)
                        .header("X-XSRF-TOKEN", csrfCookie.getValue())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(BODY))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void rejectsCorsPreflightFromUnknownOrigin() throws Exception {
        mockMvc.perform(options(TODOS)
                        .header(HttpHeaders.ORIGIN, "https://evil.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    @Test
    void allowsCorsPreflightFromConfiguredOrigin() throws Exception {
        mockMvc.perform(options(TODOS)
                        .header(HttpHeaders.ORIGIN, "https://voice-assistant.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "https://voice-assistant.example"));
    }
}
