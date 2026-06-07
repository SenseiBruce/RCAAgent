package com.rca.agent.chat;

import com.rca.agent.config.RcaProperties;
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
    private final RcaProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JsonNode> pendingFixes = new ConcurrentHashMap<>();

    public ChatService(LlmProvider llmProvider, RcaService rcaService, AutoFixService autoFixService,
                       RcaProperties properties) {
        this.llmProvider = llmProvider;
        this.rcaService = rcaService;
        this.autoFixService = autoFixService;
        this.properties = properties;
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        List<ChatMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        history.add(ChatMessage.user(request.message()));
        trimHistory(history);

        // Check if user is providing a token for a pending fix
        String userMsg = request.message().trim();
        if (userMsg.startsWith("ghp_") || userMsg.startsWith("github_pat_")) {
            sessionTokens.put(sessionId, userMsg);
            JsonNode pendingFix = pendingFixes.remove(sessionId);
            if (pendingFix != null) {
                history.add(ChatMessage.assistant("Token received. Creating the fix PR now..."));
                String repoUrl = pendingFix.path("repoUrl").asText("");
                if (repoUrl.isBlank() && properties.getGit().getRepoUrl() != null) {
                    repoUrl = properties.getGit().getRepoUrl();
                }
                String fixBranch = pendingFix.path("branch").asText("");
                if (fixBranch.isBlank()) {
                    fixBranch = properties.getGit().getDefaultBranch();
                }
                FixRequest fixRequest = new FixRequest(
                        repoUrl,
                        fixBranch,
                        pendingFix.path("rootCause").asText(""),
                        List.of(),
                        List.of(),
                        pendingFix.path("issueDescription").asText("")
                );
                FixResponse fixResponse = autoFixService.fix(fixRequest, userMsg);
                String resultMessage = formatFixResult(fixResponse);
                history.add(ChatMessage.assistant(resultMessage));
                return ChatResponse.withAction("Token received. Creating the fix PR now...\n\n" + resultMessage,
                        sessionId, "fix_complete", List.of("Investigate another issue", "Done"));
            }
            history.add(ChatMessage.assistant("Token saved. I'll use it when you ask me to fix something."));
            return ChatResponse.reply("Token saved. I'll use it when you ask me to fix something.", sessionId);
        }

        String systemPrompt = buildSystemPrompt();
        String conversationContext = buildConversationContext(history);
        String fullPrompt = systemPrompt + "\n\n" + conversationContext;

        String llmResponse = llmProvider.analyze(fullPrompt);

        // Check if LLM decided to execute an action
        ChatResponse response = checkForAction(llmResponse, sessionId, history);
        if (response != null) return response;

        history.add(ChatMessage.assistant(llmResponse));
        List<String> quickReplies = suggestQuickReplies(llmResponse, history);
        return ChatResponse.reply(llmResponse, sessionId, quickReplies);
    }

    private List<String> suggestQuickReplies(String llmResponse, List<ChatMessage> history) {
        String lower = llmResponse.toLowerCase();

        // After greeting / first message
        if (history.size() <= 2) {
            return List.of("🔍 Investigate an issue", "📋 Paste logs", "🔗 Analyze a repo");
        }

        // When bot asks for logs
        if (lower.contains("log") && (lower.contains("share") || lower.contains("paste") || lower.contains("provide"))) {
            return List.of("📋 I'll paste logs", "📁 I have a log file path", "⏭️ Skip logs");
        }

        // When bot asks for repo
        if (lower.contains("repo") && (lower.contains("url") || lower.contains("path") || lower.contains("provide"))) {
            return List.of("🔗 I'll provide a repo URL", "⏭️ Skip repo analysis");
        }

        // When asking about time window
        if (lower.contains("time") && (lower.contains("window") || lower.contains("when"))) {
            return List.of("Last 1h", "Last 6h", "Last 24h", "Last 7d");
        }

        // Generic fallback
        return List.of();
    }

    private ChatResponse checkForAction(String llmResponse, String sessionId, List<ChatMessage> history) {
        try {
            if (llmResponse.contains("\"action\":") && llmResponse.contains("\"params\":")) {
                String json = extractJson(llmResponse);
                JsonNode node = objectMapper.readTree(json);
                String action = node.path("action").asText("");

                if ("analyze".equals(action)) {
                    JsonNode params = node.path("params");
                    String repoPath = params.path("repoPath").asText(null);
                    if (repoPath == null || repoPath.isBlank()) {
                        repoPath = properties.getGit().getRepoUrl();
                    }
                    String branch = params.path("branch").asText(null);
                    if (branch == null || branch.isBlank()) {
                        branch = properties.getGit().getDefaultBranch();
                    }
                    RcaRequest rcaRequest = new RcaRequest(
                            params.path("issueDescription").asText(""),
                            params.path("logFilePath").asText(null),
                            params.path("logContent").asText(null),
                            repoPath,
                            branch,
                            params.path("timeWindow").asText(null)
                    );

                    String userMessage = node.path("message").asText("I'll analyze this now...");
                    history.add(ChatMessage.assistant(userMessage));

                    RcaResponse rcaResponse = rcaService.analyze(rcaRequest);
                    String resultMessage = formatRcaResult(rcaResponse);
                    history.add(ChatMessage.assistant(resultMessage));

                    return ChatResponse.withAction(userMessage + "\n\n" + resultMessage, sessionId, "rca_complete",
                            List.of("✅ Yes, create a fix PR", "❌ No thanks", "📝 More details"));
                }

                if ("fix".equals(action)) {
                    JsonNode params = node.path("params");
                    String token = params.path("token").asText("");

                    // Priority: session token > env config > LLM param
                    String sessionToken = sessionTokens.get(sessionId);
                    if (sessionToken != null && !sessionToken.isBlank()) {
                        token = sessionToken;
                    }
                    if (token.isBlank()) {
                        String configToken = properties.getGit().getGithubToken();
                        if (configToken != null && !configToken.isBlank()) {
                            token = configToken;
                        }
                    }

                    if (token.isBlank()) {
                        String msg = "I need a GitHub Personal Access Token to push the fix and create a PR. Please paste your token (starts with ghp_).";
                        history.add(ChatMessage.assistant(msg));
                        pendingFixes.put(sessionId, params);
                        return ChatResponse.reply(msg, sessionId, List.of("Skip auto-fix"));
                    }

                    String repoUrl = params.path("repoUrl").asText("");
                    if (repoUrl.isBlank()) {
                        repoUrl = properties.getGit().getRepoUrl() != null ? properties.getGit().getRepoUrl() : "";
                    }
                    String fixBranch = params.path("branch").asText("");
                    if (fixBranch.isBlank()) {
                        fixBranch = properties.getGit().getDefaultBranch();
                    }

                    FixRequest fixRequest = new FixRequest(
                            repoUrl,
                            fixBranch,
                            params.path("rootCause").asText(""),
                            List.of(),
                            List.of(),
                            params.path("issueDescription").asText("")
                    );

                    FixResponse fixResponse = autoFixService.fix(fixRequest, token);
                    String resultMessage = formatFixResult(fixResponse);
                    history.add(ChatMessage.assistant(resultMessage));

                    return ChatResponse.withAction(resultMessage, sessionId, "fix_complete",
                            List.of("Investigate another issue", "Done"));
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
