package com.rca.agent.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rca.agent.fix.AutoFixService;
import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.model.RcaResponse.GitChange;
import com.rca.agent.service.RcaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(RcaController.class)
class RcaControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RcaService rcaService;

    @MockitoBean
    private AutoFixService autoFixService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void health_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/rca/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("RCA Agent is running"));
    }

    @Test
    void analyze_validRequest_returnsResponse() throws Exception {
        RcaResponse response = new RcaResponse(
                "Null pointer in UserService",
                "HIGH",
                List.of("NPE at line 42"),
                List.of(),
                List.of(new GitChange("abc123", "dev", "fix auth", Instant.now(), List.of("UserService.java"))),
                List.of("Add null check"),
                Instant.now()
        );

        when(rcaService.analyze(any(RcaRequest.class))).thenReturn(response);

        String requestJson = """
                {
                    "issueDescription": "NPE on login",
                    "logContent": "2024-01-15T10:30:00Z ERROR NPE"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("Null pointer in UserService"))
                .andExpect(jsonPath("$.severity").value("HIGH"))
                .andExpect(jsonPath("$.evidenceFromLogs[0]").value("NPE at line 42"))
                .andExpect(jsonPath("$.recommendations[0]").value("Add null check"))
                .andExpect(jsonPath("$.relatedCommits[0].commitId").value("abc123"));
    }

    @Test
    void analyze_missingIssueDescription_returnsBadRequest() throws Exception {
        String requestJson = """
                {
                    "logContent": "some logs"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyze_blankIssueDescription_returnsBadRequest() throws Exception {
        String requestJson = """
                {
                    "issueDescription": "   "
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void analyze_serviceThrows_returnsInternalError() throws Exception {
        when(rcaService.analyze(any(RcaRequest.class))).thenThrow(new RuntimeException("LLM down"));

        String requestJson = """
                {
                    "issueDescription": "test issue"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("Internal error: LLM down"));
    }

    @Test
    void analyze_withAllFields_returnsOk() throws Exception {
        RcaResponse response = new RcaResponse("root cause", "MEDIUM", List.of(), List.of(), List.of(), List.of(), Instant.now());
        when(rcaService.analyze(any(RcaRequest.class))).thenReturn(response);

        String requestJson = """
                {
                    "issueDescription": "test",
                    "logContent": "ERROR log",
                    "logFilePath": "/path/to/log",
                    "repoPath": "/path/to/repo",
                    "branch": "main",
                    "timeWindow": "last 2h"
                }
                """;

        mockMvc.perform(post("/api/v1/rca/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(requestJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rootCause").value("root cause"));
    }

    @Test
    void notFoundUrl_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/rca/nonexistent"))
                .andExpect(status().isNotFound());
    }
}
