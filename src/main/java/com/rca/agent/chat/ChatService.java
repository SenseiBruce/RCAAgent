package com.rca.agent.chat;

import com.rca.agent.config.GuardrailService;
import com.rca.agent.config.RcaProperties;
import com.rca.agent.fix.AutoFixService;
import com.rca.agent.fix.FixRequest;
import com.rca.agent.fix.FixResponse;
import com.rca.agent.llm.LlmProvider;
import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.service.PromptService;
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

    private final LlmProvider llmProvider;
    private final RcaService rcaService;
    private final AutoFixService autoFixService;
    private final PromptService promptService;
    private final GuardrailService guardrails;
    private final RcaProperties properties;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final ConcurrentHashMap<String, List<ChatMessage>> sessions = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionTokens = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, JsonNode> pendingFixes = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, String> sessionPhases = new ConcurrentHashMap<>();

    // Conversation phases
    private static final String PHASE_GREETING = "greeting";
    private static final String PHASE_GATHERING = "gathering";
    private static final String PHASE_ANALYZING = "analyzing";
    private static final String PHASE_RCA_COMPLETE = "rca_complete";
    private static final String PHASE_FIX_COMPLETE = "fix_complete";

    public ChatService(LlmProvider llmProvider, RcaService rcaService, AutoFixService autoFixService,
                       PromptService promptService, GuardrailService guardrails, RcaProperties properties) {
        this.llmProvider = llmProvider;
        this.rcaService = rcaService;
        this.autoFixService = autoFixService;
        this.promptService = promptService;
        this.guardrails = guardrails;
        this.properties = properties;
    }

    public ChatResponse chat(ChatRequest request) {
        String sessionId = request.sessionId() != null ? request.sessionId() : UUID.randomUUID().toString();
        List<ChatMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());
        String currentPhase = sessionPhases.getOrDefault(sessionId, PHASE_GREETING);

        // Guardrail: validate input
        guardrails.validateInput(request.message());

        history.add(ChatMessage.user(request.message()));
        trimHistory(history);

        // Check if user is providing a token for a pending fix
        String userMsg = request.message().trim();
        if (userMsg.startsWith("ghp_") || userMsg.startsWith("github_pat_")) {
            return handleTokenInput(sessionId, userMsg, history);
        }

        // Transition to gathering phase after greeting
        if (PHASE_GREETING.equals(currentPhase) && history.size() > 1) {
            sessionPhases.put(sessionId, PHASE_GATHERING);
            currentPhase = PHASE_GATHERING;
        }

        // Reset from rca_complete/fix_complete when user sends a new conversational message
        if ((PHASE_RCA_COMPLETE.equals(currentPhase) || PHASE_FIX_COMPLETE.equals(currentPhase))
                && !userMsg.startsWith("ghp_") && !userMsg.startsWith("github_pat_")) {
            sessionPhases.put(sessionId, PHASE_GATHERING);
            currentPhase = PHASE_GATHERING;
        }

        String systemPrompt = buildSystemPrompt();
        String conversationContext = buildConversationContext(history);
        String fullPrompt = systemPrompt + "\n\n" + conversationContext;

        String llmResponse = llmProvider.analyze(fullPrompt);

        // Check if LLM decided to execute an action
        ChatResponse response = checkForAction(llmResponse, sessionId, history);
        if (response != null) return response;

        history.add(ChatMessage.assistant(llmResponse));
        List<String> quickReplies = suggestQuickReplies(llmResponse, currentPhase, history);
        return ChatResponse.reply(llmResponse, sessionId, quickReplies);
    }

    /**
     * Handles log file content submitted via upload endpoint.
     */
    public ChatResponse handleLogUpload(String sessionId, String filename, String logContent) {
        sessionId = sessionId != null ? sessionId : UUID.randomUUID().toString();
        List<ChatMessage> history = sessions.computeIfAbsent(sessionId, k -> new ArrayList<>());

        String userMsg = "📁 Uploaded log file: " + filename + "\n\n" + truncateForDisplay(logContent, 500);
        history.add(ChatMessage.user(userMsg));
        sessionPhases.put(sessionId, PHASE_GATHERING);

        String assistantMsg = "Got it — I've received the log file **" + filename + "** (" +
                logContent.length() + " chars). What issue are you investigating? Describe the problem and I'll analyze the logs.";
        history.add(ChatMessage.assistant(assistantMsg));

        return ChatResponse.reply(assistantMsg, sessionId,
                List.of("🔍 Analyze these logs for errors", "📝 Let me describe the issue first"));
    }

    private ChatResponse handleTokenInput(String sessionId, String userMsg, List<ChatMessage> history) {
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
                    repoUrl, fixBranch,
                    pendingFix.path("rootCause").asText(""),
                    List.of(), List.of(),
                    pendingFix.path("issueDescription").asText("")
            );
            FixResponse fixResponse = autoFixService.fix(fixRequest, userMsg);
            String resultMessage = formatFixResult(fixResponse);
            history.add(ChatMessage.assistant(resultMessage));
            sessionPhases.put(sessionId, PHASE_FIX_COMPLETE);
            if (fixResponse.pullRequestUrl() != null) {
                return ChatResponse.withAction("Token received. Creating the fix PR now...\n\n" + resultMessage,
                        sessionId, "fix_complete", List.of("🔍 Investigate another issue", "👋 Done"));
            }
            return ChatResponse.withAction("Token received. Attempting fix...\n\n" + resultMessage,
                    sessionId, "fix_failed", List.of("🔄 Retry with more context", "📝 Show me what to fix manually"));
        }
        history.add(ChatMessage.assistant("Token saved. I'll use it when you ask me to fix something."));
        return ChatResponse.reply("Token saved. I'll use it when you ask me to fix something.", sessionId);
    }

    private List<String> suggestQuickReplies(String llmResponse, String phase, List<ChatMessage> history) {
        String lower = llmResponse.toLowerCase();

        // After RCA complete — show fix options
        if (PHASE_RCA_COMPLETE.equals(phase)) {
            return List.of("✅ Yes, create a fix PR", "❌ No thanks", "📝 More details");
        }

        // After fix complete
        if (PHASE_FIX_COMPLETE.equals(phase)) {
            return List.of("🔍 Investigate another issue", "👋 Done");
        }

        // First interaction
        if (history.size() <= 2) {
            return List.of("🔍 Investigate an issue", "📋 Paste logs");
        }

        // When bot asks for logs
        if (lower.contains("log") && (lower.contains("share") || lower.contains("paste") || lower.contains("provide"))) {
            return List.of("📋 I'll paste logs", "📁 I have a log file path", "⏭️ Skip logs");
        }

        // When asking about time window
        if (lower.contains("time") && (lower.contains("window") || lower.contains("when"))) {
            return List.of("Last 1h", "Last 6h", "Last 24h", "Last 7d");
        }

        // During gathering — general helpful options
        if (PHASE_GATHERING.equals(phase)) {
            return List.of("📋 Paste logs", "⏭️ Analyze with what you have");
        }

        return List.of();
    }

    private ChatResponse checkForAction(String llmResponse, String sessionId, List<ChatMessage> history) {
        try {
            if (llmResponse.contains("\"action\":") && llmResponse.contains("\"params\":")) {
                String json = extractJson(llmResponse);
                JsonNode node = objectMapper.readTree(json);
                String action = node.path("action").asText("");

                if ("analyze".equals(action)) {
                    return handleAnalyzeAction(node, sessionId, history);
                }

                if ("fix".equals(action)) {
                    return handleFixAction(node, sessionId, history);
                }
            }
        } catch (Exception e) {
            log.debug("Response is not an action: {}", e.getMessage());
        }
        return null;
    }

    private ChatResponse handleAnalyzeAction(JsonNode node, String sessionId, List<ChatMessage> history) {
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
                repoPath, branch,
                params.path("timeWindow").asText(null)
        );

        String userMessage = node.path("message").asText("I'll analyze this now...");
        history.add(ChatMessage.assistant(userMessage));

        sessionPhases.put(sessionId, PHASE_ANALYZING);

        RcaResponse rcaResponse = rcaService.analyze(rcaRequest);
        String resultMessage = formatRcaResult(rcaResponse);
        history.add(ChatMessage.assistant(resultMessage));

        sessionPhases.put(sessionId, PHASE_RCA_COMPLETE);

        // Only show fix options after successful RCA
        return ChatResponse.withAction(userMessage + "\n\n" + resultMessage, sessionId, "rca_complete",
                List.of("✅ Yes, create a fix PR", "❌ No thanks", "📝 More details"));
    }

    private ChatResponse handleFixAction(JsonNode node, String sessionId, List<ChatMessage> history) {
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
            return ChatResponse.reply(msg, sessionId, List.of("⏭️ Skip auto-fix"));
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
                repoUrl, fixBranch,
                params.path("rootCause").asText(""),
                List.of(), List.of(),
                params.path("issueDescription").asText("")
        );

        FixResponse fixResponse = autoFixService.fix(fixRequest, token);
        String resultMessage = formatFixResult(fixResponse);
        history.add(ChatMessage.assistant(resultMessage));

        sessionPhases.put(sessionId, PHASE_FIX_COMPLETE);

        // Different quick replies for success vs failure
        if (fixResponse.pullRequestUrl() != null) {
            return ChatResponse.withAction(resultMessage, sessionId, "fix_complete",
                    List.of("🔍 Investigate another issue", "👋 Done"));
        }
        return ChatResponse.withAction(resultMessage, sessionId, "fix_failed",
                List.of("🔄 Retry with more context", "📝 Show me what to fix manually", "🔍 Investigate another issue"));
    }

    private String buildSystemPrompt() {
        return promptService.getChatSystemPrompt();
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
        // Helpful failure message with actionable guidance
        StringBuilder sb = new StringBuilder();
        sb.append("## ⚠️ Auto-Fix Could Not Be Generated\n\n");
        sb.append("**Reason:** ").append(response.summary()).append("\n\n");
        sb.append("This usually happens when:\n");
        sb.append("- The error location wasn't found in recent code (branch may have diverged)\n");
        sb.append("- The LLM needs more code context to generate a precise fix\n\n");
        sb.append("**What you can do:**\n");
        sb.append("- Try providing the exact file content around the error\n");
        sb.append("- Specify the branch with the issue more precisely\n");
        sb.append("- Apply the recommendations manually based on the RCA above");
        return sb.toString();
    }

    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return response;
    }

    private String truncateForDisplay(String text, int maxLen) {
        if (text.length() <= maxLen) return text;
        return text.substring(0, maxLen) + "\n... (" + text.length() + " chars total)";
    }

    private void trimHistory(List<ChatMessage> history) {
        int max = guardrails.getMaxHistoryMessages();
        while (history.size() > max) {
            history.remove(0);
        }
    }
}
