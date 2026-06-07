package com.rca.agent.analyzer.git;

import com.rca.agent.analyzer.git.TimeWindowParser.TimeRange;
import com.rca.agent.config.RcaProperties;
import com.rca.agent.model.RcaResponse.GitChange;
import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GitAnalyzerServiceTest {

    private GitAnalyzerService service;
    private RcaProperties properties;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        properties = new RcaProperties();
        properties.getGit().setMaxCommits(10);
        properties.getGit().setDefaultBranch("main");
        service = new GitAnalyzerService(properties);
    }

    private Git initRepo() throws Exception {
        Git git = Git.init().setDirectory(tempDir.toFile()).setInitialBranch("main").call();
        git.getRepository().getConfig().setString("user", null, "name", "Test User");
        git.getRepository().getConfig().setString("user", null, "email", "test@example.com");
        return git;
    }

    @Test
    void getRecentCommits_returnsCommits() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "hello");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial commit").call();

            Files.writeString(tempDir.resolve("file.txt"), "world");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("second commit").call();

            List<GitChange> commits = service.getRecentCommits(tempDir.toString(), "main");

            assertThat(commits).hasSize(2);
            assertThat(commits.get(0).message()).isEqualTo("second commit");
            assertThat(commits.get(1).message()).isEqualTo("initial commit");
        }
    }

    @Test
    void getRecentCommits_usesDefaultBranch_whenBranchNull() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("test commit").call();

            List<GitChange> commits = service.getRecentCommits(tempDir.toString(), null);

            assertThat(commits).hasSize(1);
        }
    }

    @Test
    void getRecentCommits_respectsMaxCommits() throws Exception {
        properties.getGit().setMaxCommits(2);

        try (Git git = initRepo()) {
            for (int i = 0; i < 5; i++) {
                Files.writeString(tempDir.resolve("file.txt"), "content " + i);
                git.add().addFilepattern(".").call();
                git.commit().setMessage("commit " + i).call();
            }

            List<GitChange> commits = service.getRecentCommits(tempDir.toString(), "main");

            assertThat(commits).hasSize(2);
        }
    }

    @Test
    void getRecentCommits_includesChangedFiles() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("first.txt"), "a");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("first").call();

            Files.writeString(tempDir.resolve("second.txt"), "b");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("added second").call();

            List<GitChange> commits = service.getRecentCommits(tempDir.toString(), "main");

            assertThat(commits.get(0).filesChanged()).contains("second.txt");
        }
    }

    @Test
    void getRecentCommits_initialCommit_showsInitialCommitMarker() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("initial").call();

            List<GitChange> commits = service.getRecentCommits(tempDir.toString(), "main");

            assertThat(commits.get(0).filesChanged()).contains("(initial commit)");
        }
    }

    @Test
    void getRecentCommits_invalidRepo_throwsException() {
        assertThatThrownBy(() -> service.getRecentCommits("/nonexistent/path", "main"))
                .isInstanceOf(Exception.class);
    }

    @Test
    void blameFile_returnsBlameInfo() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "line1\nline2\n");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("add file").call();

            String blame = service.blameFile(tempDir.toString(), "file.txt");

            assertThat(blame).contains("L1:");
            assertThat(blame).contains("L2:");
            assertThat(blame).contains("Test User");
        }
    }

    @Test
    void blameFile_nonExistentFile_returnsNotFound() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("init").call();

            String blame = service.blameFile(tempDir.toString(), "nonexistent.txt");

            assertThat(blame).contains("File not found");
        }
    }

    @Test
    void getDiffForCommit_returnsChanges() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "original");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("first").call();

            Files.writeString(tempDir.resolve("file.txt"), "modified");
            git.add().addFilepattern(".").call();
            var secondCommit = git.commit().setMessage("second").call();

            String diff = service.getDiffForCommit(tempDir.toString(), secondCommit.getName());

            assertThat(diff).contains("file.txt");
        }
    }

    @Test
    void getDiffForCommit_initialCommit_returnsNoDiff() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            var initialCommit = git.commit().setMessage("initial").call();

            String diff = service.getDiffForCommit(tempDir.toString(), initialCommit.getName());

            assertThat(diff).contains("Initial commit");
        }
    }

    @Test
    void summarizeForLlm_formatsCorrectly() {
        List<GitChange> changes = List.of(
                new GitChange("abc12345", "dev", "fix bug", java.time.Instant.now(), List.of("App.java")),
                new GitChange("def67890", "dev2", "add feature", java.time.Instant.now(), List.of("Service.java"))
        );

        String summary = service.summarizeForLlm(changes);

        assertThat(summary).contains("GIT ANALYSIS:");
        assertThat(summary).contains("Recent commits (2)");
        assertThat(summary).contains("abc12345");
        assertThat(summary).contains("fix bug");
        assertThat(summary).contains("App.java");
    }

    @Test
    void getRecentCommits_includesAuthor() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("test").call();

            List<GitChange> commits = service.getRecentCommits(tempDir.toString(), "main");

            assertThat(commits.get(0).author()).isEqualTo("Test User");
        }
    }

    @Test
    void getRecentCommits_commitIdIs8Chars() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("test").call();

            List<GitChange> commits = service.getRecentCommits(tempDir.toString(), "main");

            assertThat(commits.get(0).commitId()).hasSize(8);
        }
    }

    @Test
    void getRecentCommits_withTimeWindow_filtersCommits() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "first");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("old commit").call();

            // Sleep to ensure timestamp difference
            Thread.sleep(1100);

            Files.writeString(tempDir.resolve("file.txt"), "second");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("recent commit").call();

            // Window that only covers "now" (last 1 second)
            Instant now = Instant.now();
            TimeRange narrowWindow = new TimeRange(now.minusSeconds(1), now);

            List<GitChange> filtered = service.getRecentCommits(tempDir.toString(), "main", narrowWindow);

            assertThat(filtered).hasSize(1);
            assertThat(filtered.get(0).message()).isEqualTo("recent commit");
        }
    }

    @Test
    void getRecentCommits_withNullTimeWindow_returnsAll() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("commit 1").call();

            Files.writeString(tempDir.resolve("file.txt"), "content2");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("commit 2").call();

            List<GitChange> all = service.getRecentCommits(tempDir.toString(), "main", null);

            assertThat(all).hasSize(2);
        }
    }

    @Test
    void getRecentCommits_withEmptyTimeWindow_returnsNone() throws Exception {
        try (Git git = initRepo()) {
            Files.writeString(tempDir.resolve("file.txt"), "content");
            git.add().addFilepattern(".").call();
            git.commit().setMessage("commit").call();

            // Window far in the future — no commits match
            Instant future = Instant.now().plusSeconds(86400);
            TimeRange futureWindow = new TimeRange(future, future.plusSeconds(3600));

            List<GitChange> filtered = service.getRecentCommits(tempDir.toString(), "main", futureWindow);

            assertThat(filtered).isEmpty();
        }
    }
}
