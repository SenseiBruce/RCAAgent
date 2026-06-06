package com.example.rca;

import com.example.analyzer.GitAnalyzerService;
import com.example.analyzer.LogAnalyzerService;
import com.example.context.CodeContextService;
import com.example.llm.LlmProvider;
import com.example.prompt.PromptService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Objects;
import org.springframework.stereotype.Service;

/**
 * Service that performs Root Cause Analysis (RCA) using various analysis components.
 */
@Service
public class RcaService {

    private final LogAnalyzerService logAnalyzer;
    private final GitAnalyzerService gitAnalyzer;
    private final CodeContextService codeContext;
    private final PromptService promptService;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RcaService(LogAnalyzerService logAnalyzer,
                      GitAnalyzerService gitAnalyzer,
                      CodeContextService codeContext,
                      PromptService promptService,
                      LlmProvider llmProvider) {
        this.logAnalyzer = Objects.requireNonNull(logAnalyzer, "logAnalyzer must not be null");
        this.gitAnalyzer = Objects.requireNonNull(gitAnalyzer, "gitAnalyzer must not be null");
        this.codeContext = Objects.requireNonNull(codeContext, "codeContext must not be null");
        this.promptService = Objects.requireNonNull(promptService, "promptService must not be null");
        this.llmProvider = Objects.requireNonNull(llmProvider, "llmProvider must not be null");
    }

    /**
     * Performs a full root cause analysis for the given request.
     * <p>
     * Steps:
     * <ol>
     *   <li>Analyze logs</li>
     *   <li>Analyze git history</li>
     *   <li>Gather code context</li>
     *   <li>Generate prompt</li>
     *   <li>Call LLM</li>
     * </ol>
     */
    // ... existing methods remain unchanged ...
}
