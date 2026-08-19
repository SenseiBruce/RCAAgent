package com.rca.agent.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.rca.agent.chat.ChatController;
import com.rca.agent.chat.ChatService;
import com.rca.agent.fix.AutoFixService;
import com.rca.agent.service.RcaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API-level evidence that {@code @Valid} / {@code @NotBlank} reject malformed and missing-field
 * bodies on analyze, chat, and fix with HTTP 400.
 */
@WebMvcTest({RcaController.class, ChatController.class})
class ValidationTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private RcaService rcaService;

  @MockitoBean private AutoFixService autoFixService;

  @MockitoBean private ChatService chatService;

  @Test
  void analyze_missingIssueDescription_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"logContent\":\"x\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ValidationFailed"));
  }

  @Test
  void analyze_blankIssueDescription_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"issueDescription\":\"   \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ValidationFailed"));
  }

  @Test
  void analyze_malformedJson_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/analyze")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{not-json"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MalformedJson"));
  }

  @Test
  void chat_missingMessage_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"sessionId\":\"s1\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ValidationFailed"));
  }

  @Test
  void chat_emptyBody_returns400() throws Exception {
    mockMvc
        .perform(post("/api/v1/rca/chat").contentType(MediaType.APPLICATION_JSON).content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ValidationFailed"));
  }

  @Test
  void chat_malformedJson_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/chat")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MalformedJson"));
  }

  @Test
  void fix_missingRepoUrl_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/fix")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Token", "ghp_test")
                .content("{\"rootCause\":\"NPE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ValidationFailed"));
  }

  @Test
  void fix_missingRootCause_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/fix")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Token", "ghp_test")
                .content("{\"repoUrl\":\"https://github.com/org/repo.git\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("ValidationFailed"));
  }

  @Test
  void fix_malformedJson_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/fix")
                .contentType(MediaType.APPLICATION_JSON)
                .header("X-GitHub-Token", "ghp_test")
                .content("["))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MalformedJson"));
  }

  @Test
  void fix_missingGithubTokenHeader_returns400() throws Exception {
    mockMvc
        .perform(
            post("/api/v1/rca/fix")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"repoUrl\":\"https://github.com/org/repo.git\",\"rootCause\":\"NPE\"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("MissingHeader"));
  }
}
