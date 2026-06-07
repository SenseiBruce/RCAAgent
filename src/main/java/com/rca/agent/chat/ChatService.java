package com.rca.agent.chat;

import com.rca.agent.fix.AutoFixService;
import com.rca.agent.fix.FixRequest;
import com.rca.agent.fix.FixResponse;
import com.rca.agent.llm.LlmProvider;
import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.service.RcaService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);
    private static final int MAX_HISTORY = 20;

    private final LlmProvider llmProvider;
    private final RcaService rcaService;
    private final AutoFixService autoFixService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();

    public ChatService(LlmProvider llmProvider, RcaService rcaService, AutoFixService autoFixService) {
        this.llmProvider = llmProvider;
        this.rcaService = rcaService;
        this.autoFixService = autoFixService;
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        List<ChatMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        history.add(ChatMessage.user(request.message()));
        trimHistory(history);

        String systemPrompt = buildSystemPrompt();
        String conversationContext = buildConversationContext(history);
        String fullPrompt = systemPrompt + "\n\n" + conversationContext;

        String llmResponse = llmProvider.analyze(fullPrompt);

        // Check if LLM decided to execute an action
        ChatResponse response = checkForAction(llmResponse, sessionId, history);
        if (response != null) return response;

        history.add(ChatMessage.assistant(llmResponse));
        return ChatResponse.reply(llmResponse, sessionId);
    }

    private ChatResponse checkForAction(String llmResponse, String sessionId, List<ChatMessage> history) {
        try {
            if (llmResponse.contains("\"action\":") && llmResponse.contains("\"params\":")) {
                String json = extractJson(llmResponse);
                JsonNode node = objectMapper.readTree(json);
                String action = node.path("action").asText("");

                if ("analyze".equals(action)) {
                    JsonNode params = node.path("params");
                    RcaRequest rcaRequest = new RcaRequest(
                            params.path("issueDescription").asText(""),
                            params.path("logFilePath").asText(null),
                            params.path("logContent").asText(null),
                            params.path("repoPath").asText(null),
                            params.path("branch").asText(null),
                            params.path("timeWindow").asText(null)
                    );

                    String userMessage = node.path("message").asText("I'll analyze this now...");
                    history.add(ChatMessage.assistant(userMessage));

                    RcaResponse rcaResponse = rcaService.analyze(rcaRequest);
                    String resultMessage = formatRcaResult(rcaResponse);
                    history.add(ChatMessage.assistant(resultMessage));

                    return ChatResponse.withAction(userMessage + "\n\n" + resultMessage, sessionId, "rca_complete");
                }

                if ("fix".equals(action)) {
                    JsonNode params = node.path("params");
                    String token = params.path("token").asText("");
                    FixRequest fixRequest = new FixRequest(
                            params.path("repoUrl").asText(""),
                            params.path("branch").asText("main"),
                            params.path("rootCause").asText(""),
                            List.of(),
                            List.of(),
                            params.path("issueDescription").asText("")
                    );

                    FixResponse fixResponse = autoFixService.fix(fixRequest, token);
                    String resultMessage = formatFixResult(fixResponse);
                    history.add(ChatMessage.assistant(resultMessage));

                    return ChatResponse.withAction(resultMessage, sessionId, "fix_complete");
                }
            }
        } catch (Exception e) {
            log.debug("Response is not an action: {}", e.getMessage());
        }
        return null;
    }

    private String buildSystemPrompt() {
        return """
                You are an RCA (Root Cause Analysis) assistant. You help users investigate production issues by analyzing logs and git history.

                Your capabilities:
                1. ANALYZE — Perform root cause analysis given: issue description, logs (content or file path), git repo path/URL, branch, time window
                2. FIX — Generate an auto-fix PR given: repo URL, branch, root cause, and a GitHub token

                CONVERSATION RULES:
                - Be concise and helpful
                - Ask for information you need to perform analysis (minimum: issue description)
                - When you have enough context to analyze, respond with a JSON action block
                - If the user wants to fix an issue, ask for the GitHub token if not provided

                WHEN READY TO ANALYZE, respond with ONLY this JSON (no other text):
                {"action": "analyze", "message": "your brief message to user", "params": {"issueDescription": "...", "logContent": "...", "repoPath": "...", "branch": "...", "timeWindow": "..."}}

                WHEN READY TO FIX, respond with ONLY this JSON (no other text):
                {"action": "fix", "message": "your brief message", "params": {"repoUrl": "...", "branch": "...", "rootCause": "...", "issueDescription": "...", "token": "..."}}

                For params you don't have, use null. Only trigger actions when you have at minimum the issue description.
                If the user is just chatting or asking questions, respond normally without JSON.
                """;
    }

    private String buildConversationContext(List<ChatMessage> history) {
        StringBuilder sb = new StringBuilder("CONVERSATION:\n");
        for (ChatMessage msg : history) {
            sb.append(msg.role().toUpperCase()).append(": ").append(msg.content()).append("\n\n");
        }
        return sb.toString();
    }

    private String formatRcaResult(RcaResponse response) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 🔍 Root Cause Analysis Complete\n\n");
        sb.append("**Root Cause:** ").append(response.rootCause()).append("\n\n");
        sb.append("**Severity:** ").append(response.severity()).append("\n\n");

        if (response.evidenceFromLogs() != null && !response.evidenceFromLogs().isEmpty()) {
            sb.append("**Evidence:**\n");
            response.evidenceFromLogs().forEach(e -> sb.append("- ").append(e).append("\n"));
            sb.append("\n");
        }

        if (response.recommendations() != null && !response.recommendations().isEmpty()) {
            sb.append("**Recommendations:**\n");
            response.recommendations().forEach(r -> sb.append("- ").append(r).append("\n"));
            sb.append("\n");
        }

        if (response.relatedCommits() != null && !response.relatedCommits().isEmpty()) {
            sb.append("**Related Commits:**\n");
            response.relatedCommits().stream().limit(5).forEach(c ->
                    sb.append("- `").append(c.commitId()).append("` ").append(c.message())
                            .append(" (").append(c.author()).append(")\n"));
        }

        sb.append("\n---\nWould you like me to generate an auto-fix PR for this issue?");
        return sb.toString();
    }

    private String formatFixResult(FixResponse response) {
        if (response.pullRequestUrl() != null) {
            return "## ✅ Auto-Fix PR Created!\n\n**PR:** " + response.pullRequestUrl() +
                    "\n**Branch:** " + response.branchName() +
                    "\n**Files:** " + String.join(", ", response.filesChanged()) +
                    "\n\n" + response.summary();
        }
        return "## ⚠️ Fix Attempted\n\n" + response.summary();
    }

    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return response;
    }

    private void trimHistory(List<ChatMessage> history) {
        while (history.size() > MAX_HISTORY) {
            history.remove(0);
        }
    }
}
