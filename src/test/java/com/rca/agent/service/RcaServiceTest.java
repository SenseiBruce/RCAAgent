package com.rca.agent.service;

import com.rca.agent.analyzer.code.CodeContextService;
import com.rca.agent.analyzer.git.GitAnalyzerService;
import com.rca.agent.analyzer.git.RepoResolver;
import com.rca.agent.analyzer.git.RepoResolver.ResolvedRepo;
import com.rca.agent.analyzer.log.LogAnalyzerService;
import com.rca.agent.analyzer.log.LogEntry;
import com.rca.agent.llm.LlmProvider;
import com.rca.agent.model.RcaRequest;
import com.rca.agent.model.RcaResponse;
import com.rca.agent.model.RcaResponse.GitChange;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RcaServiceTest {

    @Mock
    private LogAnalyzerService logAnalyzer;

    @Mock
    private GitAnalyzerService gitAnalyzer;

    @Mock
    private CodeContextService codeContext;

    @Mock
    private RepoResolver repoResolver;

    @Mock
    private PromptService promptService;

    @Mock
    private LlmProvider llmProvider;

    private RcaService rcaService;

    @BeforeEach
    void setUp() {
        rcaService = new RcaService(logAnalyzer, gitAnalyzer, codeContext, repoResolver,
                promptService, llmProvider, new SimpleMeterRegistry());
        when(promptService.renderRcaPrompt(any())).thenReturn("rendered prompt");
    }

    @Test
    void analyze_withLogContent_callsLogAnalyzer() {
        RcaRequest request = new RcaRequest("NPE on login", null, "2024-01-15T10:30:00Z ERROR NPE", null, null, null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "NPE", "", java.util.Map.of()));

        when(logAnalyzer.analyze(anyString())).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG SUMMARY");
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"null pointer\",\"severity\":\"HIGH\",\"evidenceFromLogs\":[\"NPE\"],\"recommendations\":[\"add null check\"]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("null pointer");
        assertThat(response.severity()).isEqualTo("HIGH");
        verify(logAnalyzer).analyze("2024-01-15T10:30:00Z ERROR NPE");
    }

    @Test
    void analyze_withLogFilePath_callsAnalyzeFromFile() throws Exception {
        RcaRequest request = new RcaRequest("issue", "/path/to/log", null, null, null, null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "error", "", java.util.Map.of()));

        when(logAnalyzer.analyzeFromFile("/path/to/log")).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG SUMMARY");
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"file error\",\"severity\":\"MEDIUM\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("file error");
        verify(logAnalyzer).analyzeFromFile("/path/to/log");
    }

    @Test
    void analyze_noLogData_skipsLogAnalysis() {
        RcaRequest request = new RcaRequest("issue", null, null, null, null, null);

        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"unknown\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("unknown");
        verify(logAnalyzer, never()).analyze(anyString());
    }

    @Test
    void analyze_withRepoPath_callsGitAnalyzer() throws Exception {
        RcaRequest request = new RcaRequest("issue", null, "ERROR log", "/repo", "main", null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "err", "", java.util.Map.of()));
        List<GitChange> commits = List.of(new GitChange("abc123", "dev", "fix", Instant.now(), List.of("App.java")));

        when(repoResolver.resolve("/repo", "main")).thenReturn(new ResolvedRepo("/repo", false));
        when(logAnalyzer.analyze(anyString())).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG");
        when(gitAnalyzer.getRecentCommits(eq("/repo"), eq("main"), any())).thenReturn(commits);
        when(gitAnalyzer.summarizeForLlm(commits)).thenReturn("GIT SUMMARY");
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"bug\",\"severity\":\"HIGH\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.relatedCommits()).hasSize(1);
        verify(gitAnalyzer, times(2)).getRecentCommits(eq("/repo"), eq("main"), any());
    }

    @Test
    void analyze_noRepoPath_skipsGitAnalysis() throws Exception {
        RcaRequest request = new RcaRequest("issue", null, "ERROR", null, null, null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "err", "", java.util.Map.of()));

        when(logAnalyzer.analyze(anyString())).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG");
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"test\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.relatedCommits()).isEmpty();
        verify(gitAnalyzer, never()).getRecentCommits(anyString(), anyString());
    }

    @Test
    void analyze_blankRepoPath_skipsGitAnalysis() throws Exception {
        RcaRequest request = new RcaRequest("issue", null, "ERROR", "  ", null, null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "err", "", java.util.Map.of()));

        when(logAnalyzer.analyze(anyString())).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG");
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"test\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.relatedCommits()).isEmpty();
    }

    @Test
    void analyze_gitAnalyzerThrows_continuesGracefully() throws Exception {
        RcaRequest request = new RcaRequest("issue", null, "ERROR", "/repo", "main", null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "err", "", java.util.Map.of()));

        when(repoResolver.resolve("/repo", "main")).thenReturn(new ResolvedRepo("/repo", false));
        when(logAnalyzer.analyze(anyString())).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG");
        when(gitAnalyzer.getRecentCommits("/repo", "main")).thenThrow(new RuntimeException("repo not found"));
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"test\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("test");
        assertThat(response.relatedCommits()).isEmpty();
    }

    @Test
    void analyze_logAnalyzerThrows_continuesGracefully() throws Exception {
        RcaRequest request = new RcaRequest("issue", "/bad/path", null, null, null, null);

        when(logAnalyzer.analyzeFromFile("/bad/path")).thenThrow(new RuntimeException("file not found"));
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"test\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("test");
    }

    @Test
    void analyze_llmReturnsNonJson_returnsRawResponse() {
        RcaRequest request = new RcaRequest("issue", null, "ERROR", null, null, null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "err", "", java.util.Map.of()));

        when(logAnalyzer.analyze(anyString())).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG");
        when(llmProvider.analyze(anyString())).thenReturn("I cannot parse this as JSON");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("I cannot parse this as JSON");
        assertThat(response.severity()).isEqualTo("MEDIUM");
    }

    @Test
    void analyze_llmReturnsJsonWithMarkdown_extractsJson() {
        RcaRequest request = new RcaRequest("issue", null, "ERROR", null, null, null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "err", "", java.util.Map.of()));

        when(logAnalyzer.analyze(anyString())).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG");
        when(llmProvider.analyze(anyString())).thenReturn("Here's my analysis:\n{\"rootCause\":\"extracted\",\"severity\":\"CRITICAL\",\"evidenceFromLogs\":[\"log1\"],\"recommendations\":[\"fix1\"]}\nEnd.");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("extracted");
        assertThat(response.severity()).isEqualTo("CRITICAL");
        assertThat(response.evidenceFromLogs()).contains("log1");
        assertThat(response.recommendations()).contains("fix1");
    }

    @Test
    void analyze_emptyLogContent_treatedAsNoLog() {
        RcaRequest request = new RcaRequest("issue", null, "  ", null, null, null);

        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"no logs\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("no logs");
        verify(logAnalyzer, never()).analyze(anyString());
    }

    @Test
    void analyze_emptyLogFilePath_usesLogContent() {
        RcaRequest request = new RcaRequest("issue", "  ", "ERROR log content", null, null, null);
        List<LogEntry> entries = List.of(new LogEntry(Instant.now(), "ERROR", "err", "", java.util.Map.of()));

        when(logAnalyzer.analyze("ERROR log content")).thenReturn(entries);
        when(logAnalyzer.summarizeForLlm(any())).thenReturn("LOG");
        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"from content\",\"severity\":\"MEDIUM\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        RcaResponse response = rcaService.analyze(request);

        assertThat(response.rootCause()).isEqualTo("from content");
    }

    @Test
    void analyze_responseHasTimestamp() {
        RcaRequest request = new RcaRequest("issue", null, null, null, null, null);

        when(llmProvider.analyze(anyString())).thenReturn("{\"rootCause\":\"test\",\"severity\":\"LOW\",\"evidenceFromLogs\":[],\"recommendations\":[]}");
        when(llmProvider.name()).thenReturn("test");

        Instant before = Instant.now();
        RcaResponse response = rcaService.analyze(request);

        assertThat(response.analyzedAt()).isAfterOrEqualTo(before);
    }
}
