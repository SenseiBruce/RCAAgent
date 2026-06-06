package com.rca.agent.service;

public class RcaService {
    private final LogAnalyzerService logAnalyzer;
    private final GitAnalyzerService gitAnalyzer;
    private final CodeContextService codeContext;
    private final PromptService promptService;
    private final LlmProvider llmProvider;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public RcaService(LogAnalyzerService logAnalyzer, GitAnalyzerService gitAnalyzer,
                     CodeContextService codeContext, PromptService promptService, LlmProvider llmProvider) {
        if (logAnalyzer == null) {
            throw new IllegalArgumentException("logAnalyzer cannot be null");
        }
        this.logAnalyzer = logAnalyzer;
        this.gitAnalyzer = gitAnalyzer;
        this.codeContext = codeContext;
        this.promptService = promptService;
        this.llmProvider = llmProvider;
    }

    /**
     * Performs a full root cause analysis for the given request.
     * <p>
     * Steps:
     * <ol>
     */
    // ... (rest of the class unchanged)
}
