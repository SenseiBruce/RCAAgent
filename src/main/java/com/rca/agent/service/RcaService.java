package com.rca.agent.service;

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

@Service
public class RcaService {

    private static final Logger log = LoggerFactory.getLogger(RcaService.class);
    private final LogAnalyzerService logAnalyzer;
    private final GitAnalyzerService gitAnalyzer;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RcaService(LogAnalyzerService logAnalyzer, GitAnalyzerService gitAnalyzer, LlmProvider llmProvider) {
        this.logAnalyzer = logAnalyzer;
        this.gitAnalyzer = gitAnalyzer;
        this.llmProvider = llmProvider;
    }

    public RcaResponse analyze(RcaRequest request) {
        log.info("Starting RCA analysis for issue: {}", request.issueDescription());

        String logSummary = analyzeLog(request);
        String gitSummary = analyzeGit(request);
        List<GitChange> recentCommits = getCommits(request);

        String prompt = buildPrompt(request.issueDescription(), logSummary, gitSummary);
        String llmResponse = llmProvider.analyze(prompt);

        log.info("RCA analysis complete using provider: {}", llmProvider.name());
        return parseResponse(llmResponse, recentCommits);
    }

    private String analyzeLog(RcaRequest request) {
        try {
            List<LogEntry> entries;
            if (request.logFilePath() != null && !request.logFilePath().isBlank()) {
                entries = logAnalyzer.analyzeFromFile(request.logFilePath());
            } else if (request.logContent() != null && !request.logContent().isBlank()) {
                entries = logAnalyzer.analyze(request.logContent());
            } else {
                return "No log data provided.";
            }
            return logAnalyzer.summarizeForLlm(entries);
        } catch (Exception e) {
            log.error("Log analysis failed", e);
            return "Log analysis failed: " + e.getMessage();
        }
    }

    private String analyzeGit(RcaRequest request) {
        if (request.repoPath() == null || request.repoPath().isBlank()) return "No git repo provided.";
        try {
            List<GitChange> commits = gitAnalyzer.getRecentCommits(request.repoPath(), request.branch());
            return gitAnalyzer.summarizeForLlm(commits);
        } catch (Exception e) {
            log.error("Git analysis failed", e);
            return "Git analysis failed: " + e.getMessage();
        }
    }

    private List<GitChange> getCommits(RcaRequest request) {
        if (request.repoPath() == null || request.repoPath().isBlank()) return List.of();
        try {
            return gitAnalyzer.getRecentCommits(request.repoPath(), request.branch());
        } catch (Exception e) {
            return List.of();
        }
    }

    private String buildPrompt(String issue, String logSummary, String gitSummary) {
        return """
                You are a Root Cause Analysis expert. Analyze the following information and identify the root cause.
                
                ISSUE DESCRIPTION:
                %s
                
                %s
                
                %s
                
                Respond in this exact JSON format:
                {
                  "rootCause": "Clear explanation of the root cause",
                  "severity": "CRITICAL|HIGH|MEDIUM|LOW",
                  "evidenceFromLogs": ["relevant log line 1", "relevant log line 2"],
                  "recommendations": ["fix recommendation 1", "fix recommendation 2"]
                }
                """.formatted(issue, logSummary, gitSummary);
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
