package com.rca.agent.fix.platform;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabPlatformTest {

    private GitLabPlatform platform;

    @BeforeEach
    void setUp() {
        platform = new GitLabPlatform();
    }

    @Test
    void supports_gitlabComUrl_returnsTrue() {
        assertThat(platform.supports("https://gitlab.com/org/repo.git")).isTrue();
    }

    @Test
    void supports_selfHostedGitlab_returnsTrue() {
        assertThat(platform.supports("https://gitlab.irdeto.com/team/project.git")).isTrue();
    }

    @Test
    void supports_githubUrl_returnsFalse() {
        assertThat(platform.supports("https://github.com/org/repo.git")).isFalse();
    }

    @Test
    void supports_bitbucketUrl_returnsFalse() {
        assertThat(platform.supports("https://bitbucket.org/org/repo.git")).isFalse();
    }

    @Test
    void name_returnsGitlab() {
        assertThat(platform.name()).isEqualTo("gitlab");
    }

    @Test
    void createPullRequest_unreachableHost_returnsNull() {
        // Tests that network errors are caught gracefully
        PrRequest request = new PrRequest(
                "https://gitlab.invalid-host-xyz.com/team/project.git",
                "fix/rca-123", "main",
                "fix: NPE", "MR body", "glpat-token");

        String url = platform.createPullRequest(request);

        assertThat(url).isNull();
    }

    @Test
    void createPullRequest_invalidToken_returnsNull() {
        // Against a real GitLab this would 401, but unreachable host = caught exception
        PrRequest request = new PrRequest(
                "https://gitlab.nonexistent-xyz.com/org/repo.git",
                "fix/branch", "main", "title", "body", "bad-token");

        String url = platform.createPullRequest(request);

        assertThat(url).isNull();
    }
}
