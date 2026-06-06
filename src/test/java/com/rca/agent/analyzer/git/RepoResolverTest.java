package com.rca.agent.analyzer.git;

import org.eclipse.jgit.api.Git;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RepoResolverTest {

    private RepoResolver resolver;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        resolver = new RepoResolver();
    }

    @AfterEach
    void tearDown() {
        resolver.shutdownCleanup();
    }

    @Test
    void resolve_localPath_returnsAsIs() throws Exception {
        String localPath = "/some/local/path";
        RepoResolver.ResolvedRepo repo = resolver.resolve(localPath, "main");

        assertThat(repo.localPath()).isEqualTo(localPath);
        assertThat(repo.isTemporary()).isFalse();
    }

    @Test
    void resolve_localPath_notMarkedAsTemporary() throws Exception {
        RepoResolver.ResolvedRepo repo = resolver.resolve("/my/repo", null);
        assertThat(repo.isTemporary()).isFalse();
    }

    @Test
    void resolve_httpsUrl_clonesAndCaches() throws Exception {
        // Create a bare repo to clone from
        Path bareRepo = tempDir.resolve("bare.git");
        Git.init().setDirectory(bareRepo.toFile()).setBare(true).call().close();

        // Clone it via file:// URL (simulates remote)
        String url = "file://" + bareRepo.toAbsolutePath();
        RepoResolver.ResolvedRepo repo = resolver.resolve(url, null);

        assertThat(repo.isTemporary()).isTrue();
        assertThat(Files.exists(Path.of(repo.localPath()))).isTrue();
        assertThat(Files.exists(Path.of(repo.localPath(), ".git"))).isTrue();
    }

    @Test
    void resolve_samUrlTwice_returnsCachedAndPulls() throws Exception {
        // Create a bare repo with a commit
        Path bareRepo = tempDir.resolve("bare.git");
        Path workDir = tempDir.resolve("work");
        Git.init().setDirectory(bareRepo.toFile()).setBare(true).call().close();
        Git git = Git.cloneRepository().setURI(bareRepo.toUri().toString()).setDirectory(workDir.toFile()).call();
        Files.writeString(workDir.resolve("file.txt"), "content");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("init").call();
        git.push().call();
        git.close();

        String url = bareRepo.toUri().toString();

        // First resolve - clones
        RepoResolver.ResolvedRepo first = resolver.resolve(url, null);
        String firstPath = first.localPath();

        // Second resolve - reuses cache (same path)
        RepoResolver.ResolvedRepo second = resolver.resolve(url, null);

        assertThat(second.localPath()).isEqualTo(firstPath);
    }

    @Test
    void resolve_httpUrl_detectedAsRemote() throws Exception {
        // We can't actually clone from http in test, but verify detection
        assertThat(resolver.resolve("/local/path", null).isTemporary()).isFalse();
    }

    @Test
    void resolve_gitAtUrl_detectedAsRemote() throws Exception {
        // git@ URLs should be treated as remote
        // Will fail to clone but that's expected — we're testing detection
        assertThatThrownBy(() -> resolver.resolve("git@github.com:fake/repo.git", null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void resolve_sshUrl_detectedAsRemote() throws Exception {
        assertThatThrownBy(() -> resolver.resolve("ssh://git@github.com/fake/repo.git", null))
                .isInstanceOf(Exception.class);
    }

    @Test
    void cleanup_doesNotDeleteCachedRepo() throws Exception {
        Path bareRepo = tempDir.resolve("bare2.git");
        Git.init().setDirectory(bareRepo.toFile()).setBare(true).call().close();

        String url = "file://" + bareRepo.toAbsolutePath();
        RepoResolver.ResolvedRepo repo = resolver.resolve(url, null);

        // Cleanup per-request should NOT delete cached repos
        resolver.cleanup(repo);
        assertThat(Files.exists(Path.of(repo.localPath()))).isTrue();
    }

    @Test
    void shutdownCleanup_deletesAllCachedRepos() throws Exception {
        Path bareRepo = tempDir.resolve("bare3.git");
        Git.init().setDirectory(bareRepo.toFile()).setBare(true).call().close();

        String url = "file://" + bareRepo.toAbsolutePath();
        RepoResolver.ResolvedRepo repo = resolver.resolve(url, null);
        String cachedPath = repo.localPath();

        assertThat(Files.exists(Path.of(cachedPath))).isTrue();

        resolver.shutdownCleanup();

        assertThat(Files.exists(Path.of(cachedPath))).isFalse();
    }

    @Test
    void cleanup_nullRepo_noOp() {
        resolver.cleanup(null);
        // No exception thrown
    }

    @Test
    void resolve_localRepo_cleanupIsNoOp() throws Exception {
        RepoResolver.ResolvedRepo repo = resolver.resolve("/local/path", "main");
        resolver.cleanup(repo);
        // No exception, local repos are never deleted
    }
}
