package com.rca.agent.fix;

import com.rca.agent.analyzer.git.RepoResolver;
import com.rca.agent.analyzer.git.RepoResolver.ResolvedRepo;
import com.rca.agent.fix.platform.GitPlatform;
import com.rca.agent.llm.LlmProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class AutoFixServiceTest {

    @Mock
    private LlmProvider llmProvider;

    @Mock
    private RepoResolver repoResolver;

    @Mock
    private GitPlatform gitPlatform;

    private AutoFixService autoFixService;

    @BeforeEach
    void setUp() {
        autoFixService = new AutoFixService(llmProvider, repoResolver, List.of(gitPlatform));
    }

    @Test
    void fix_llmReturnsEmptyChanges_returnsFailureResponse() throws Exception {
        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", "main",
                "NPE in UserService", List.of("add null check"), null, "login fails");

        when(repoResolver.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedRepo("/tmp/fake", true));
        when(llmProvider.analyze(anyString())).thenReturn("{\"changes\": [], \"commitMessage\": \"fix\"}");

        FixResponse response = autoFixService.fix(request, "token");

        assertThat(response.pullRequestUrl()).isNull();
        assertThat(response.summary()).contains("could not generate");
    }

    @Test
    void fix_llmReturnsInvalidJson_returnsFailureResponse() throws Exception {
        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", "main",
                "NPE", null, null, null);

        when(repoResolver.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedRepo("/tmp/fake", true));
        when(llmProvider.analyze(anyString())).thenReturn("not valid json at all");

        FixResponse response = autoFixService.fix(request, "token");

        assertThat(response.pullRequestUrl()).isNull();
        assertThat(response.summary()).contains("could not generate");
    }

    @Test
    void fix_repoResolveFails_returnsError() throws Exception {
        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", "main",
                "NPE", null, null, null);

        when(repoResolver.resolve(anyString(), anyString()))
                .thenThrow(new RuntimeException("clone failed"));

        FixResponse response = autoFixService.fix(request, "token");

        assertThat(response.pullRequestUrl()).isNull();
        assertThat(response.summary()).contains("Auto-fix failed");
    }

    @Test
    void fix_withCodeSnippets_includesInPrompt() throws Exception {
        var snippets = List.of(new FixRequest.CodeSnippetRef("App.java", 42, "return null;"));
        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", "main",
                "NPE", List.of("add null check"), snippets, "issue");

        when(repoResolver.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedRepo("/tmp/fake", true));
        when(llmProvider.analyze(anyString())).thenReturn("{\"changes\": [], \"commitMessage\": \"fix\"}");

        autoFixService.fix(request, "token");

        verify(llmProvider).analyze(argThat(prompt ->
                prompt.contains("App.java") && prompt.contains("return null;")));
    }

    @Test
    void fix_nullRecommendations_handledGracefully() throws Exception {
        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", "main",
                "NPE", null, null, null);

        when(repoResolver.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedRepo("/tmp/fake", true));
        when(llmProvider.analyze(anyString())).thenReturn("{\"changes\": [], \"commitMessage\": \"fix\"}");

        FixResponse response = autoFixService.fix(request, "token");

        assertThat(response.summary()).contains("could not generate");
    }

    @Test
    void fix_llmReturnsChangesWithBlankPaths_treatsAsEmpty() throws Exception {
        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", "main",
                "NPE", null, null, null);

        when(repoResolver.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedRepo("/tmp/fake", true));
        when(llmProvider.analyze(anyString())).thenReturn(
                "{\"changes\": [{\"filePath\": \"\", \"content\": \"code\"}], \"commitMessage\": \"fix\"}");

        FixResponse response = autoFixService.fix(request, "token");

        assertThat(response.pullRequestUrl()).isNull();
        assertThat(response.summary()).contains("could not generate");
    }

    @Test
    void fix_llmResponseWrappedInMarkdown_extractsJson() throws Exception {
        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", "main",
                "NPE", null, null, null);

        when(repoResolver.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedRepo("/tmp/fake", true));
        when(llmProvider.analyze(anyString())).thenReturn(
                "```json\n{\"changes\": [], \"commitMessage\": \"fix\"}\n```");

        FixResponse response = autoFixService.fix(request, "token");

        assertThat(response.summary()).contains("could not generate");
    }

    @Test
    void fix_successfulFixWithPush_returnsPrUrl(@TempDir Path tempDir) throws Exception {
        initGitRepo(tempDir);

        FixRequest request = new FixRequest(
                "https://github.com/org/repo.git", null,
                "NPE", null, null, null);

        when(repoResolver.resolve(anyString(), any()))
                .thenReturn(new ResolvedRepo(tempDir.toString(), true));
        when(llmProvider.analyze(anyString())).thenReturn(
                "{\"changes\": [{\"filePath\": \"src/Fix.java\", \"content\": \"class Fix {}\"}], \"commitMessage\": \"fix: NPE\"}");

        FixResponse response = autoFixService.fix(request, "token");

        // Push fails (no remote) — returns error but doesn't crash
        assertThat(response).isNotNull();
        assertThat(response.summary()).contains("Auto-fix failed");
    }

    @Test
    void fix_unsupportedPlatform_returnsError(@TempDir Path tempDir) throws Exception {
        initGitRepo(tempDir);

        FixRequest request = new FixRequest(
                "https://bitbucket.org/org/repo.git", "main",
                "NPE", null, null, null);

        when(repoResolver.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedRepo(tempDir.toString(), true));
        when(llmProvider.analyze(anyString())).thenReturn(
                "{\"changes\": [{\"filePath\": \"src/Fix.java\", \"content\": \"class Fix {}\"}], \"commitMessage\": \"fix\"}");
        lenient().when(gitPlatform.supports("https://bitbucket.org/org/repo.git")).thenReturn(false);

        FixResponse response = autoFixService.fix(request, "token");

        assertThat(response.summary()).contains("Auto-fix failed");
    }

    private void initGitRepo(Path dir) throws Exception {
        org.eclipse.jgit.api.Git.init().setDirectory(dir.toFile()).call().close();
        Files.writeString(dir.resolve("README.md"), "init");
        try (var git = org.eclipse.jgit.api.Git.open(dir.toFile())) {
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").call();
        }
    }
}
