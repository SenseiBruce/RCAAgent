package com.rca.agent.service;

import com.rca.agent.analyzer.code.CodeContextService;
import com.rca.agent.analyzer.code.FileReference;
import com.rca.agent.analyzer.git.GitAnalyzerService;
import com.rca.agent.analyzer.git.RepoResolver;
import com.rca.agent.analyzer.git.RepoResolver.ResolvedRepo;
import com.rca.agent.analyzer.git.TimeWindowParser;
import com.rca.agent.analyzer.git.TimeWindowParser.TimeRange;
import com.rca.agent.analyzer.log.LogAnalyzerService;
import com.rca.agent.analyzer.log.LogEntry;
import com.rca.agent.config.GuardrailService;
import com.rca.agent.llm.LlmProvider;
import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.model.RcaResponse.CodeSnippet;
import com.rca.agent.model.RcaResponse.GitChange;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * Orchestrates the complete root cause analysis workflow.
 * <p>
 * Coordinates log analysis, source code extraction, git history analysis,
 * and LLM-based reasoning to produce a structured root cause determination.
 * Supports both local repo paths and remote git URLs.
 */
@Service
public class RcaService {

    private static final Logger log = LoggerFactory.getLogger(RcaService.class);
    private final LogAnalyzerService logAnalyzer;
    private final GitAnalyzerService gitAnalyzer;
    private final CodeContextService codeContext;
    private final RepoResolver repoResolver;
    private final PromptService promptService;
    private final LlmProvider llmProvider;
    private final GuardrailService guardrails;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Timer analysisTimer;
    private final Counter analysisCounter;
    private final Counter analysisErrorCounter;

    public RcaService(LogAnalyzerService logAnalyzer, GitAnalyzerService gitAnalyzer,
                      CodeContextService codeContext, RepoResolver repoResolver,
                      PromptService promptService, LlmProvider llmProvider,
                      GuardrailService guardrails, MeterRegistry meterRegistry) {
        this.logAnalyzer = logAnalyzer;
        this.gitAnalyzer = gitAnalyzer;
        this.codeContext = codeContext;
        this.repoResolver = repoResolver;
        this.promptService = promptService;
        this.llmProvider = llmProvider;
        this.guardrails = guardrails;
        this.analysisTimer = Timer.builder("rca.analysis.duration")
                .description("Time taken for RCA analysis")
                .register(meterRegistry);
        this.analysisCounter = Counter.builder("rca.analysis.total")
                .description("Total RCA analyses performed")
                .register(meterRegistry);
        this.analysisErrorCounter = Counter.builder("rca.analysis.errors")
                .description("Total RCA analysis errors")
                .register(meterRegistry);
    }

    /**
     * Performs a full root cause analysis for the given request.
     *
     * @param request the analysis request containing issue details, logs, and repo info
     * @return structured response with root cause, severity, evidence, and recommendations
     */
    public RcaResponse analyze(RcaRequest request) {
        // Guardrails: validate input and enforce concurrency limit
        guardrails.validateInput(request.issueDescription());
        guardrails.validateRepoAccess(request.repoPath());

        if (!guardrails.acquireAnalysisSlot()) {
            throw new IllegalStateException("Max concurrent analyses reached. Try again later.");
        }
        try {
            return analysisTimer.record(() -> doAnalyze(request));
        } finally {
            guardrails.releaseAnalysisSlot();
        }
    }

    private RcaResponse doAnalyze(RcaRequest request) {
        log.info("Starting RCA analysis for issue: {}", request.issueDescription());
        analysisCounter.increment();

        // Step 0: Resolve repo (clone if remote URL)
        ResolvedRepo resolvedRepo = resolveRepo(request);
        String repoPath = resolvedRepo != null ? resolvedRepo.localPath() : null;

        try {
            // Step 1: Parse logs
            List<LogEntry> logEntries = parseLogEntries(request);
            String logSummary = logEntries.isEmpty() ? "No log data provided." : logAnalyzer.summarizeForLlm(logEntries);

            // Step 2: Extract file references once, reuse for code + git
            List<FileReference> fileRefs = logEntries.isEmpty() ? List.of() : codeContext.extractFileReferences(logEntries);

            // Step 3: Get source code context
            List<CodeSnippet> codeSnippets = getCodeSnippets(repoPath, fileRefs);
            String codeContextSummary = analyzeCode(repoPath, fileRefs);

            // Step 4: Git analysis (recent commits + blame) with time-window correlation
            TimeRange timeWindow = resolveTimeWindow(request.timeWindow(), logEntries);
            String gitSummary = analyzeGit(repoPath, request.branch(), fileRefs, timeWindow);
            List<GitChange> recentCommits = getCommits(repoPath, request.branch(), timeWindow);

            // Step 4: Send to LLM
            String prompt = promptService.renderRcaPrompt(Map.of(
                    "issueDescription", request.issueDescription(),
                    "logSummary", logSummary,
                    "codeContext", codeContextSummary,
                    "gitSummary", gitSummary
            ));
            String llmResponse = llmProvider.analyze(prompt);

            log.info("RCA analysis complete using provider: {}", llmProvider.name());
            return parseResponse(llmResponse, codeSnippets, recentCommits);
        } finally {
            repoResolver.cleanup(resolvedRepo);
        }
    }

    private ResolvedRepo resolveRepo(RcaRequest request) {
        if (request.repoPath() == null || request.repoPath().isBlank()) return null;
        try {
            return repoResolver.resolve(request.repoPath(), request.branch());
        } catch (Exception e) {
            log.error("Failed to resolve repo: {}", request.repoPath(), e);
            return null;
        }
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

    private List<CodeSnippet> getCodeSnippets(String repoPath, List<FileReference> refs) {
        if (repoPath == null || refs.isEmpty()) return List.of();
        try {
            Map<FileReference, String> context = codeContext.getCodeContext(repoPath, refs);
            return context.entrySet().stream()
                    .map(e -> new CodeSnippet(e.getKey().filePath(), e.getKey().lineNumber(), e.getValue()))
                    .toList();
        } catch (Exception e) {
            log.debug("Failed to extract code snippets", e);
            return List.of();
        }
    }

    private String analyzeCode(String repoPath, List<FileReference> refs) {
        if (repoPath == null || refs.isEmpty()) return "";
        try {
            Map<FileReference, String> context = codeContext.getCodeContext(repoPath, refs);
            return codeContext.summarizeForLlm(context);
        } catch (Exception e) {
            log.error("Code context extraction failed", e);
            return "Code context extraction failed: " + e.getMessage();
        }
    }

    private String analyzeGit(String repoPath, String branch, List<FileReference> refs, TimeRange timeWindow) {
        if (repoPath == null) return "No git repo provided.";
        try {
            StringBuilder sb = new StringBuilder();
            List<GitChange> commits = gitAnalyzer.getRecentCommits(repoPath, branch, timeWindow);
            sb.append(gitAnalyzer.summarizeForLlm(commits));
            if (timeWindow != null) {
                sb.append("\nTIME WINDOW FILTER: ").append(timeWindow.start())
                        .append(" to ").append(timeWindow.end()).append("\n");
            }

            if (!refs.isEmpty()) {
                sb.append("\nBLAME ANALYSIS (who last changed the error locations):\n\n");
                for (FileReference ref : refs.stream().limit(5).toList()) {
                    try {
                        String blame = gitAnalyzer.blameFile(repoPath, ref.filePath());
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

    private List<GitChange> getCommits(String repoPath, String branch, TimeRange timeWindow) {
        if (repoPath == null) return List.of();
        try {
            return gitAnalyzer.getRecentCommits(repoPath, branch, timeWindow);
        } catch (Exception e) {
            return List.of();
        }
    }

    private TimeRange resolveTimeWindow(String timeWindowSpec, List<LogEntry> logEntries) {
        // Explicit time window from request takes priority
        TimeRange explicit = TimeWindowParser.parse(timeWindowSpec);
        if (explicit != null) return explicit;

        // Auto-derive from log entry timestamps if available
        if (logEntries.size() >= 2) {
            List<Instant> timestamps = logEntries.stream()
                    .map(LogEntry::timestamp)
                    .sorted()
                    .toList();
            Instant earliest = timestamps.getFirst();
            Instant latest = timestamps.getLast();
            // Expand window by 1 hour on each side to catch related commits
            return new TimeRange(earliest.minusSeconds(3600), latest.plusSeconds(3600));
        }
        return null;
    }

    private RcaResponse parseResponse(String llmResponse, List<CodeSnippet> codeSnippets, List<GitChange> commits) {
        try {
            String json = extractJson(llmResponse);
            var node = objectMapper.readTree(json);
            return new RcaResponse(
                    node.path("rootCause").asText("Unable to determine root cause"),
                    node.path("severity").asText("MEDIUM"),
                    node.path("evidenceFromLogs").findValuesAsText("evidenceFromLogs").isEmpty()
                            ? extractArray(node, "evidenceFromLogs") : List.of(),
                    codeSnippets,
                    commits,
                    extractArray(node, "recommendations"),
                    Instant.now()
            );
        } catch (Exception e) {
            log.warn("Could not parse LLM response as JSON, returning raw", e);
            return new RcaResponse(llmResponse, "MEDIUM", List.of(), codeSnippets, commits, List.of(), Instant.now());
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
