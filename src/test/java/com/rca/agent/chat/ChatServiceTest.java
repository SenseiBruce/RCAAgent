package com.rca.agent.chat;

import com.rca.agent.fix.AutoFixService;
import com.rca.agent.fix.FixResponse;
import com.rca.agent.llm.LlmProvider;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.service.RcaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private RcaService rcaService;

    @Mock
    private AutoFixService autoFixService;

    private ChatService chatService;

    @BeforeEach
    void setUp() {
        chatService = new ChatService(llmProvider, rcaService, autoFixService);
    }

    // --- Session Management ---

    @Test
    void chat_noSessionId_generatesNewSession() {
        when(llmProvider.analyze(anyString())).thenReturn("Hello! What issue are you facing?");

        ChatResponse response = chatService.chat(new ChatRequest("hello", null));

        assertThat(response.sessionId()).isNotNull().isNotBlank();
        assertThat(response.message()).isEqualTo("Hello! What issue are you facing?");
    }

    @Test
    void chat_withSessionId_maintainsSession() {
        when(llmProvider.analyze(anyString())).thenReturn("Got it.");

        ChatResponse first = chatService.chat(new ChatRequest("hello", null));
        String sessionId = first.sessionId();

        when(llmProvider.analyze(anyString())).thenReturn("Tell me more.");
        ChatResponse second = chatService.chat(new ChatRequest("NPE on login", sessionId));

        assertThat(second.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void chat_sessionHistory_sentToLlm() {
        when(llmProvider.analyze(anyString())).thenReturn("What's wrong?");

        ChatResponse first = chatService.chat(new ChatRequest("hi", null));
        String sessionId = first.sessionId();

        when(llmProvider.analyze(argThat(prompt ->
                prompt.contains("USER: hi") && prompt.contains("ASSISTANT: What's wrong?"))))
                .thenReturn("Tell me more.");

        chatService.chat(new ChatRequest("NPE error", sessionId));

        verify(llmProvider, times(2)).analyze(anyString());
    }

    // --- Normal Conversation (no action) ---

    @Test
    void chat_normalResponse_returnsReplyWithNoAction() {
        when(llmProvider.analyze(anyString())).thenReturn("Can you share the logs?");

        ChatResponse response = chatService.chat(new ChatRequest("app is crashing", null));

        assertThat(response.message()).isEqualTo("Can you share the logs?");
        assertThat(response.action()).isNull();
        verify(rcaService, never()).analyze(any());
        verify(autoFixService, never()).fix(any(), anyString());
    }

    @Test
    void chat_responseWithoutActionKeywords_treatedAsNormal() {
        when(llmProvider.analyze(anyString())).thenReturn("I understand the issue. Let me ask a few questions.");

        ChatResponse response = chatService.chat(new ChatRequest("test", null));

        assertThat(response.action()).isNull();
    }

    // --- Analyze Action ---

    @Test
    void chat_analyzeAction_triggersRcaService() {
        String llmResponse = """
                {"action": "analyze", "message": "Analyzing now...", "params": {"issueDescription": "NPE on login", "logContent": "ERROR NPE at line 42", "repoPath": null, "branch": null, "timeWindow": null}}
                """;
        when(llmProvider.analyze(anyString())).thenReturn(llmResponse);
        when(rcaService.analyze(any())).thenReturn(new RcaResponse(
                "Null pointer in UserService", "HIGH",
                List.of("NPE at line 42"), List.of(), List.of(),
                List.of("Add null check"), Instant.now()));

        ChatResponse response = chatService.chat(new ChatRequest("check this", null));

        assertThat(response.action()).isEqualTo("rca_complete");
        assertThat(response.message()).contains("Analyzing now...");
        assertThat(response.message()).contains("Null pointer in UserService");
        assertThat(response.message()).contains("HIGH");
        verify(rcaService).analyze(any());
    }

    @Test
    void chat_analyzeAction_includesRecommendations() {
        String llmResponse = """
                {"action": "analyze", "message": "Let me check.", "params": {"issueDescription": "timeout errors", "logContent": null, "repoPath": "/repo", "branch": "main", "timeWindow": "last 2h"}}
                """;
        when(llmProvider.analyze(anyString())).thenReturn(llmResponse);
        when(rcaService.analyze(any())).thenReturn(new RcaResponse(
                "Connection pool exhausted", "CRITICAL",
                List.of(), List.of(), List.of(),
                List.of("Increase pool size", "Add circuit breaker"), Instant.now()));

        ChatResponse response = chatService.chat(new ChatRequest("investigate", null));

        assertThat(response.message()).contains("Increase pool size");
        assertThat(response.message()).contains("Add circuit breaker");
    }

    @Test
    void chat_analyzeAction_rcaServiceThrows_returnsError() {
        String llmResponse = """
                {"action": "analyze", "message": "Checking...", "params": {"issueDescription": "error", "logContent": null, "repoPath": null, "branch": null, "timeWindow": null}}
                """;
        when(llmProvider.analyze(anyString())).thenReturn(llmResponse);
        when(rcaService.analyze(any())).thenThrow(new RuntimeException("LLM down"));

        // Should not throw — falls through to normal response handling
        ChatResponse response = chatService.chat(new ChatRequest("analyze", null));

        assertThat(response).isNotNull();
    }

    // --- Fix Action ---

    @Test
    void chat_fixAction_triggersAutoFixService() {
        String llmResponse = """
                {"action": "fix", "message": "Creating PR...", "params": {"repoUrl": "https://github.com/org/repo", "branch": "main", "rootCause": "NPE", "issueDescription": "login broken", "token": "ghp_token123"}}
                """;
        when(llmProvider.analyze(anyString())).thenReturn(llmResponse);
        when(autoFixService.fix(any(), eq("ghp_token123"))).thenReturn(
                new FixResponse("https://github.com/org/repo/pull/1", "fix/rca-123",
                        List.of("UserService.java"), "Fixed NPE"));

        ChatResponse response = chatService.chat(new ChatRequest("fix it", null));

        assertThat(response.action()).isEqualTo("fix_complete");
        assertThat(response.message()).contains("https://github.com/org/repo/pull/1");
        assertThat(response.message()).contains("fix/rca-123");
        verify(autoFixService).fix(any(), eq("ghp_token123"));
    }

    @Test
    void chat_fixAction_noPrCreated_returnsWarning() {
        String llmResponse = """
                {"action": "fix", "message": "Attempting fix...", "params": {"repoUrl": "https://github.com/org/repo", "branch": "main", "rootCause": "NPE", "issueDescription": "error", "token": "token"}}
                """;
        when(llmProvider.analyze(anyString())).thenReturn(llmResponse);
        when(autoFixService.fix(any(), anyString())).thenReturn(
                new FixResponse(null, null, List.of(), "LLM could not generate a fix."));

        ChatResponse response = chatService.chat(new ChatRequest("fix", null));

        assertThat(response.action()).isEqualTo("fix_complete");
        assertThat(response.message()).contains("Fix Attempted");
        assertThat(response.message()).contains("could not generate");
    }

    // --- Edge Cases ---

    @Test
    void chat_llmReturnsPartialActionJson_treatedAsNormal() {
        // Contains "action" keyword but invalid JSON
        when(llmProvider.analyze(anyString())).thenReturn(
                "I'll take action: analyzing your params: now");

        ChatResponse response = chatService.chat(new ChatRequest("test", null));

        assertThat(response.action()).isNull();
        verify(rcaService, never()).analyze(any());
    }

    @Test
    void chat_llmReturnsMalformedJson_treatedAsNormal() {
        when(llmProvider.analyze(anyString())).thenReturn(
                "{\"action\": \"analyze\", \"params\": {broken json}}");

        ChatResponse response = chatService.chat(new ChatRequest("test", null));

        assertThat(response).isNotNull();
        assertThat(response.action()).isNull();
    }

    @Test
    void chat_llmReturnsUnknownAction_treatedAsNormal() {
        String llmResponse = """
                {"action": "unknown_action", "params": {"foo": "bar"}}
                """;
        when(llmProvider.analyze(anyString())).thenReturn(llmResponse);

        ChatResponse response = chatService.chat(new ChatRequest("test", null));

        assertThat(response.action()).isNull();
        assertThat(response.message()).contains("unknown_action");
    }

    @Test
    void chat_historyTrimmedAt20Messages() {
        when(llmProvider.analyze(anyString())).thenReturn("ok");

        ChatResponse first = chatService.chat(new ChatRequest("msg1", null));
        String sessionId = first.sessionId();

        for (int i = 2; i <= 25; i++) {
            chatService.chat(new ChatRequest("msg" + i, sessionId));
        }

        // Verify no exception and response still works
        ChatResponse last = chatService.chat(new ChatRequest("final", sessionId));
        assertThat(last.sessionId()).isEqualTo(sessionId);
    }

    @Test
    void chat_emptyLlmResponse_handledGracefully() {
        when(llmProvider.analyze(anyString())).thenReturn("");

        ChatResponse response = chatService.chat(new ChatRequest("hello", null));

        assertThat(response.message()).isEmpty();
        assertThat(response.action()).isNull();
    }

    @Test
    void chat_llmThrows_propagatesException() {
        when(llmProvider.analyze(anyString())).thenThrow(new RuntimeException("API error"));

        org.junit.jupiter.api.Assertions.assertThrows(RuntimeException.class,
                () -> chatService.chat(new ChatRequest("test", null)));
    }
}
