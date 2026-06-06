package com.rca.agent;

import com.rca.agent.llm.LlmProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test — boots the entire Spring context with a mocked LLM provider.
 */
@SpringBootTest
@AutoConfigureMockMvc
class RcaIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private LlmProvider llmProvider;

    @Test
    void healthEndpoint_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/rca/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("RCA Agent is running"));
    }

    @Test
    void analyze_withLogContent_returnsStructuredResponse() throws Exception {
        when(llmProvider.analyze(anyString())).thenReturn("""
                {
                    "rootCause": "NullPointerException in UserService.getUser() due to missing null check",
                    "severity": "HIGH",
                    "evidenceFromLogs": ["NPE at UserService.java:42"],
                    "recommendations": ["Add null check before accessing user object"]
                }
                """);
        when(llmProvider.name()).thenReturn("mock");

        String requestJson = """
                {
                    "issueDescription": "Login page returns 500 error",
                    "logContent": "2024-01-15T10:30:00Z ERROR [http-nio-8080-exec-1] o.s.UserService - NullPointerException\\n    at com.app.UserService.getUser(UserService.java:42)"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("NullPointerException in UserService.getUser() due to missing null check"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.analyzedAt").exists());
    }

    @Test
    void analyze_minimalRequest_returnsResponse() throws Exception {
        when(llmProvider.analyze(anyString())).thenReturn(
                "{\"rootCause\":\"unknown\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("mock");

        String requestJson = """
                {
                    "issueDescription": "Something is broken"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("unknown"));
    }

    @Test
    void analyze_llmReturnsGarbage_stillReturnsResponse() throws Exception {
        when(llmProvider.analyze(anyString())).thenReturn("I'm not sure what happened here.");
        when(llmProvider.name()).thenReturn("mock");

        String requestJson = """
                {
                    "issueDescription": "Test issue"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("I'm not sure what happened here."))
                .andExpect(jsonPath("$.severity").value("MEDIUM"));
    }

    @Test
    void analyze_invalidJson_returnsError() throws Exception {
        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("not json"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void analyze_emptyBody_returnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void fix_missingGithubTokenHeader_returnsError() throws Exception {
        String requestJson = """
                {
                    "repoUrl": "https://github.com/org/repo",
                    "rootCause": "NPE"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/fix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void fix_missingRequiredFields_returnsBadRequest() throws Exception {
        String requestJson = """
                {
                    "repoUrl": "https://github.com/org/repo"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/fix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("X-GitHub-Token", "token"))
                .andExpect(status().isBadRequest());
    }
}
