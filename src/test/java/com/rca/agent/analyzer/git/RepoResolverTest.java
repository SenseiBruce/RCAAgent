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

    private TestableRepoResolver resolver;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        resolver = new TestableRepoResolver();
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
    void isRemoteUrl_httpsDetected() {
        assertThat(resolver.isRemoteUrl("https://github.com/user/repo.git")).isTrue();
    }

    @Test
    void isRemoteUrl_httpDetected() {
        assertThat(resolver.isRemoteUrl("http://github.com/user/repo.git")).isTrue();
    }

    @Test
    void isRemoteUrl_gitAtDetected() {
        assertThat(resolver.isRemoteUrl("git@github.com:user/repo.git")).isTrue();
    }

    @Test
    void isRemoteUrl_sshDetected() {
        assertThat(resolver.isRemoteUrl("ssh://git@github.com/user/repo.git")).isTrue();
    }

    @Test
    void isRemoteUrl_localPathNotDetected() {
        assertThat(resolver.isRemoteUrl("/Users/dev/my-repo")).isFalse();
    }

    @Test
    void isRemoteUrl_fileProtocolNotDetected() {
        // Test against the base class directly (TestableRepoResolver treats file:// as remote for testing)
        RepoResolver baseResolver = new RepoResolver();
        assertThat(baseResolver.isRemoteUrl("file:///tmp/repo")).isFalse();
    }

    @Test
    void resolve_remoteUrl_clonesAndCaches() throws Exception {
        Path bareRepo = createBareRepoWithCommit();
        String url = "https://fake-" + bareRepo.toAbsolutePath();

        // TestableRepoResolver treats "https://fake-" prefix as remote and clones from the path
        RepoResolver.ResolvedRepo repo = resolver.resolve(url, null);

        assertThat(repo.isTemporary()).isTrue();
        assertThat(Files.exists(Path.of(repo.localPath()))).isTrue();
        assertThat(Files.exists(Path.of(repo.localPath(), ".git"))).isTrue();
    }

    @Test
    void resolve_sameUrlTwice_returnsCachedPath() throws Exception {
        Path bareRepo = createBareRepoWithCommit();
        String url = "https://fake-" + bareRepo.toAbsolutePath();

        RepoResolver.ResolvedRepo first = resolver.resolve(url, null);
        RepoResolver.ResolvedRepo second = resolver.resolve(url, null);

        assertThat(second.localPath()).isEqualTo(first.localPath());
    }

    @Test
    void cleanup_doesNotDeleteCachedRepo() throws Exception {
        Path bareRepo = createBareRepoWithCommit();
        String url = "https://fake-" + bareRepo.toAbsolutePath();

        RepoResolver.ResolvedRepo repo = resolver.resolve(url, null);
        resolver.cleanup(repo);

        assertThat(Files.exists(Path.of(repo.localPath()))).isTrue();
    }

    @Test
    void shutdownCleanup_deletesAllCachedRepos() throws Exception {
        Path bareRepo = createBareRepoWithCommit();
        String url = "https://fake-" + bareRepo.toAbsolutePath();

        RepoResolver.ResolvedRepo repo = resolver.resolve(url, null);
        String cachedPath = repo.localPath();

        assertThat(Files.exists(Path.of(cachedPath))).isTrue();
        resolver.shutdownCleanup();
        assertThat(Files.exists(Path.of(cachedPath))).isFalse();
    }

    @Test
    void cleanup_nullRepo_noOp() {
        resolver.cleanup(null);
    }

    @Test
    void resolve_invalidRemoteUrl_throwsException() {
        assertThatThrownBy(() -> resolver.resolve("https://invalid-nonexistent-host-xyz.com/repo.git", null))
                .isInstanceOf(Exception.class);
    }

    private Path createBareRepoWithCommit() throws Exception {
        Path bareRepo = tempDir.resolve("bare-" + System.nanoTime() + ".git");
        Path workDir = tempDir.resolve("work-" + System.nanoTime());
        Git.init().setDirectory(bareRepo.toFile()).setBare(true).call().close();
        Git git = Git.cloneRepository().setURI(bareRepo.toUri().toString()).setDirectory(workDir.toFile()).call();
        Files.writeString(workDir.resolve("file.txt"), "content");
        git.add().addFilepattern(".").call();
        git.commit().setMessage("init").call();
        git.push().call();
        git.close();
        return bareRepo;
    }

    /**
     * Test subclass that intercepts "https://fake-" URLs and clones from local bare repos.
     */
    static class TestableRepoResolver extends RepoResolver {
        @Override
        public ResolvedRepo resolve(String repoPathOrUrl, String branch) throws Exception {
            if (repoPathOrUrl.startsWith("https://fake-")) {
                String localBare = repoPathOrUrl.replace("https://fake-", "");
                return super.resolve("file://" + localBare, branch);
            }
            return super.resolve(repoPathOrUrl, branch);
        }

        // Allow file:// for testing only
        @Override
        boolean isRemoteUrl(String path) {
            if (path.startsWith("file://")) return true;
            return super.isRemoteUrl(path);
        }
    }
}
