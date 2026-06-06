package com.example.rca;

import com.example.analyzer.GitAnalyzerService;
import com.example.analyzer.LogAnalyzerService;
import com.example.context.CodeContextService;
import com.example.llm.LlmProvider;
import com.example.prompt.PromptService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;
import static org.junit.jupiter.api.Assertions.*;

class RcaServiceTest {

    @Test
    void constructorShouldThrowWhenGitAnalyzerIsNull() {
        LogAnalyzerService logAnalyzer = mock(LogAnalyzerService.class);
        GitAnalyzerService gitAnalyzer = null;
        CodeContextService codeContext = mock(CodeContextService.class);
        PromptService promptService = mock(PromptService.class);
        LlmProvider llmProvider = mock(LlmProvider.class);

        Executable ctor = () -> new RcaService(logAnalyzer, gitAnalyzer, codeContext, promptService, llmProvider);
        NullPointerException ex = assertThrows(NullPointerException.class, ctor);
        assertTrue(ex.getMessage().contains("gitAnalyzer"));
    }

    @Test
    void constructorShouldSucceedWhenAllDependenciesProvided() {
        LogAnalyzerService logAnalyzer = mock(LogAnalyzerService.class);
        GitAnalyzerService gitAnalyzer = mock(GitAnalyzerService.class);
        CodeContextService codeContext = mock(CodeContextService.class);
        PromptService promptService = mock(PromptService.class);
        LlmProvider llmProvider = mock(LlmProvider.class);

        assertDoesNotThrow(() -> new RcaService(logAnalyzer, gitAnalyzer, codeContext, promptService, llmProvider));
    }

    // Simple Mockito mock helper to avoid adding an extra import line per test
    private <T> T mock(Class<T> clazz) {
        return org.mockito.Mockito.mock(clazz);
    }
}
