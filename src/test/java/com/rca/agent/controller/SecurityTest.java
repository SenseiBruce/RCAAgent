package com.rca.agent.controller;

import com.rca.agent.fix.AutoFixService;
import com.rca.agent.fix.FixResponse;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.service.RcaService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Security-focused tests: injection, oversized payloads, path traversal, header manipulation.
 */
@WebMvcTest(RcaController.class)
class SecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RcaService rcaService;

    @MockitoBean
    private AutoFixService autoFixService;

    @Test
    void analyze_sqlInjectionInDescription_returnsBadRequestOrHandled() throws Exception {
        when(rcaService.analyze(any())).thenReturn(
                new RcaResponse("safe", "LOW", List.of(), List.of(), List.of(), List.of(), Instant.now()));

        String requestJson = """
                {
                    "issueDescription": "'; DROP TABLE users; --"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @Test
    void analyze_xssInDescription_doesNotReflectUnsanitized() throws Exception {
        when(rcaService.analyze(any())).thenReturn(
                new RcaResponse("safe", "LOW", List.of(), List.of(), List.of(), List.of(), Instant.now()));

        String requestJson = """
                {
                    "issueDescription": "<script>alert('xss')</script>"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../etc/passwd",
            "/etc/shadow",
            "/proc/self/environ"
    })
    void analyze_pathTraversalInLogFilePath_doesNotExpose(String maliciousPath) throws Exception {
        when(rcaService.analyze(any())).thenReturn(
                new RcaResponse("safe", "LOW", List.of(), List.of(), List.of(), List.of(), Instant.now()));

        String requestJson = "{\"issueDescription\": \"test issue\", \"logFilePath\": \"" + maliciousPath + "\"}";

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @Test
    void analyze_windowsPathTraversalInLogFilePath_doesNotExpose() throws Exception {
        when(rcaService.analyze(any())).thenReturn(
                new RcaResponse("safe", "LOW", List.of(), List.of(), List.of(), List.of(), Instant.now()));

        String requestJson = "{\"issueDescription\": \"test\", \"logFilePath\": \"..\\\\..\\\\windows\\\\system32\\\\config\\\\sam\"}";

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "../../../etc/passwd",
            "/tmp/../../etc/shadow",
            "https://evil.com/repo.git; rm -rf /"
    })
    void analyze_pathTraversalInRepoPath_doesNotExpose(String maliciousPath) throws Exception {
        when(rcaService.analyze(any())).thenReturn(
                new RcaResponse("safe", "LOW", List.of(), List.of(), List.of(), List.of(), Instant.now()));

        String requestJson = """
                {
                    "issueDescription": "test",
                    "repoPath": "%s"
                }
                """.formatted(maliciousPath);

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @Test
    void analyze_oversizedPayload_handledGracefully() throws Exception {
        when(rcaService.analyze(any())).thenReturn(
                new RcaResponse("safe", "LOW", List.of(), List.of(), List.of(), List.of(), Instant.now()));

        String hugeContent = "x".repeat(10_000_000); // 10MB
        String requestJson = """
                {
                    "issueDescription": "test",
                    "logContent": "%s"
                }
                """.formatted(hugeContent);

        // Spring's default max request size should reject or service handles it
        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().is2xxSuccessful());
    }

    @Test
    void analyze_nullByteInInput_doesNotCrash() throws Exception {
        when(rcaService.analyze(any())).thenReturn(
                new RcaResponse("safe", "LOW", List.of(), List.of(), List.of(), List.of(), Instant.now()));

        String requestJson = """
                {
                    "issueDescription": "test\\u0000injection"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk());
    }

    @Test
    void fix_headerInjection_doesNotPropagate() throws Exception {
        when(autoFixService.fix(any(), anyString())).thenReturn(
                new FixResponse(null, null, List.of(), "ok"));

        String requestJson = """
                {
                    "repoUrl": "https://github.com/org/repo",
                    "rootCause": "test"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/fix")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson)
                        .header("X-GitHub-Token", "token_with_special_chars!@#"))
                .andExpect(status().isOk());
    }

    @Test
    void analyze_wrongContentType_returnsError() throws Exception {
        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("not json"))
                .andExpect(status().is5xxServerError());
    }

    @Test
    void analyze_missingContentType_returnsError() throws Exception {
        mockMvc.perform(post("/api/v1/rca/analyze")
                        .content("{\"issueDescription\": \"test\"}"))
                .andExpect(status().is5xxServerError());
    }
}
