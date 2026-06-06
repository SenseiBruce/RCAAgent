package com.rca.agent.service;

import com.rca.agent.analyzer.code.CodeContextService;
import com.rca.agent.analyzer.code.FileReference;
import com.rca.agent.analyzer.git.GitAnalyzerService;
import com.rca.agent.analyzer.log.LogAnalyzerService;
import com.rca.agent.analyzer.log.LogEntry;
import com.rca.agent.llm.LlmProvider;
import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.model.RcaResponse.GitChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Map;

/**
 * Orchestrates the complete root cause analysis workflow.
 * <p>
 * Coordinates log analysis, source code extraction, git history analysis,
 * and LLM-based reasoning to produce a structured root cause determination.
 */
@Service
public class RcaService {

    private static final Logger log = LoggerFactory.getLogger(RcaService.class);
    private final LogAnalyzerService logAnalyzer;
    private final GitAnalyzerService gitAnalyzer;
    private final CodeContextService codeContext;
    private final PromptService promptService;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RcaService(LogAnalyzerService logAnalyzer, GitAnalyzerService gitAnalyzer,
                      CodeContextService codeContext, PromptService promptService, LlmProvider llmProvider) {
        this.logAnalyzer = logAnalyzer;
        this.gitAnalyzer = Objects.requireNonNull(gitAnalyzer, "gitAnalyzer must not be null");
        this.codeContext = codeContext;
        this.promptService = promptService;
        this.llmProvider = llmProvider;
    }

    /**
     * Performs a full root cause analysis for the given request.
     * <p>
     * Steps:
     * <ol>
     *   <li>Parse and summarize log data (from file or content)</li>
     *   <li>Extract file:line references from error logs</li>
     *   <li>Read actual source code around those error locations</li>
     *   <li>Get git blame for referenced files</li>
     *   <li>Analyze recent git commits</li>
     *   <li>Send all context to LLM for root cause determination</li>
     *   <li>Parse and return structured response</li>
     * </ol>
     *
     * @param request the analysis request containing issue details, logs, and repo info
     * @return structured response with root cause, severity, evidence, and recommendations
     */
    public RcaResponse analyze(RcaRequest request) {
        log.info("Starting RCA analysis for issue: {}", request.issueDescription());

        // Step 1: Parse logs
        List<LogEntry> logEntries = parseLogEntries(request);
        String logSummary = logEntries.isEmpty() ? "No log data provided." : logAnalyzer.summarizeForLlm(logEntries);

        // Step 2: Extract file references and source code context
        String codeContextSummary = analyzeCode(request, logEntries);

        // Step 3: Git analysis (recent commits + blame for referenced files)
        String gitSummary = analyzeGit(request, logEntries);
        List<GitChange> recentCommits = getCommits(request);

        // Step 4: Send to LLM
        String prompt = promptService.renderRcaPrompt(Map.of(
                "issueDescription", request.issueDescription(),
                "logSummary", logSummary,
                "codeContext", codeContextSummary,
                "gitSummary", gitSummary
        ));
        String llmResponse = llmProvider.analyze(prompt);

        log.info("RCA analysis complete using provider: {}", llmProvider.name());
        return parseResponse(llmResponse, recentCommits);
    }

    private List<LogEntry> parseLogEntries(RcaRequest request) {
        try {
            if (request.logFilePath() != null && !request.logFilePath().isBlank()) {
                return logAnalyzer.analyzeFromFile(request.logFilePath());
            } else if (request.logContent() != null && !request.logContent().isBlank()) {
                return logAnalyzer.analyze(request.logContent());
            }
        } catch (Exception e) {
            log.error("Log parsing failed", e);
        }
        return List.of();
    }

    private String analyzeCode(RcaRequest request, List<LogEntry> logEntries) {
        if (request.repoPath() == null || request.repoPath().isBlank()) return "";
        if (logEntries.isEmpty()) return "";

        try {
            List<FileReference> refs = codeContext.extractFileReferences(logEntries);
            if (refs.isEmpty()) return "";

            Map<FileReference, String> context = codeContext.getCodeContext(request.repoPath(), refs);
            return codeContext.summarizeForLlm(context);
        } catch (Exception e) {
            log.error("Code context extraction failed", e);
            return "Code context extraction failed: " + e.getMessage();
        }
    }

    private String analyzeGit(RcaRequest request, List<LogEntry> logEntries) {
        if (request.repoPath() == null || request.repoPath().isBlank()) return "No git repo provided.";
        try {
            StringBuilder sb = new StringBuilder();

            // Recent commits
            List<GitChange> commits = gitAnalyzer.getRecentCommits(request.repoPath(), request.branch());
            sb.append(gitAnalyzer.summarizeForLlm(commits));

            // Blame for files referenced in errors
            List<FileReference> refs = codeContext.extractFileReferences(logEntries);
            if (!refs.isEmpty()) {
                sb.append("\nBLAME ANALYSIS (who last changed the error locations):\n\n");
                for (FileReference ref : refs.stream().limit(5).toList()) {
                    try {
                        String blame = gitAnalyzer.blameFile(request.repoPath(), ref.filePath());
                        if (!blame.startsWith("File not found")) {
                            sb.append("--- ").append(ref.filePath()).append(" ---\n");
                            sb.append(extractBlameAround(blame, ref.lineNumber())).append("\n");
                        }
                    } catch (Exception e) {
                        log.debug("Blame failed for {}: {}", ref.filePath(), e.getMessage());
                    }
                }
            }
            return sb.toString();
        } catch (Exception e) {
            log.error("Git analysis failed", e);
            return "Git analysis failed: " + e.getMessage();
        }
    }

    private String extractBlameAround(String fullBlame, int targetLine) {
        String[] lines = fullBlame.split("\n");
        int start = Math.max(0, targetLine - 4);
        int end = Math.min(lines.length, targetLine + 3);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < end; i++) {
            sb.append(lines[i]).append("\n");
        }
        return sb.toString();
    }

    private List<GitChange> getCommits(RcaRequest request) {
        if (request.repoPath() == null || request.repoPath().isBlank()) return List.of();
        try {
            return gitAnalyzer.getRecentCommits(request.repoPath(), request.branch());
        } catch (Exception e) {
            return List.of();
        }
    }


    private RcaResponse parseResponse(String llmResponse, List<GitChange> commits) {
        try {
            String json = extractJson(llmResponse);
            var node = objectMapper.readTree(json);
            return new RcaResponse(
                    node.path("rootCause").asText("Unable to determine root cause"),
                    node.path("severity").asText("MEDIUM"),
                    node.path("evidenceFromLogs").findValuesAsText("evidenceFromLogs").isEmpty()
                            ? extractArray(node, "evidenceFromLogs") : List.of(),
                    commits,
                    extractArray(node, "recommendations"),
                    Instant.now()
            );
        } catch (Exception e) {
            log.warn("Could not parse LLM response as JSON, returning raw", e);
            return new RcaResponse(llmResponse, "MEDIUM", List.of(), commits, List.of(), Instant.now());
        }
    }

    private String extractJson(String response) {
        int start = response.indexOf("{");
        int end = response.lastIndexOf("}");
        if (start >= 0 && end > start) return response.substring(start, end + 1);
        return response;
    }

    private List<String> extractArray(com.fasterxml.jackson.databind.JsonNode node, String field) {
        var arr = node.path(field);
        if (!arr.isArray()) return List.of();
        List<String> result = new java.util.ArrayList<>();
        arr.forEach(n -> result.add(n.asText()));
        return result;
    }
}
