package com.rca.agent.fix.platform;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

class GitHubPlatformTest {

    private GitHubPlatform platform;
    private MockWebServer mockServer;

    @BeforeEach
    void setUp() throws Exception {
        mockServer = new MockWebServer();
        mockServer.start();

        platform = new GitHubPlatform();
        // Inject mock WebClient pointing to our mock server
        WebClient testClient = WebClient.builder()
                .baseUrl(mockServer.url("/").toString())
                .defaultHeader("Accept", "application/vnd.github+json")
                .build();
        Field clientField = GitHubPlatform.class.getDeclaredField("client");
        clientField.setAccessible(true);
        clientField.set(platform, testClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        mockServer.shutdown();
    }

    @Test
    void supports_githubUrl_returnsTrue() {
        assertThat(platform.supports("https://github.com/org/repo.git")).isTrue();
    }

    @Test
    void supports_nonGithubUrl_returnsFalse() {
        assertThat(platform.supports("https://gitlab.com/org/repo.git")).isFalse();
    }

    @Test
    void name_returnsGithub() {
        assertThat(platform.name()).isEqualTo("github");
    }

    @Test
    void createPullRequest_success_returnsPrUrl() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"html_url\": \"https://github.com/org/repo/pull/42\"}")
                .setHeader("Content-Type", "application/json"));

        PrRequest request = new PrRequest(
                "https://github.com/org/repo.git",
                "fix/rca-123", "main",
                "fix: NPE in UserService", "body text", "ghp_token123");

        String url = platform.createPullRequest(request);

        assertThat(url).isEqualTo("https://github.com/org/repo/pull/42");

        RecordedRequest recorded = mockServer.takeRequest();
        assertThat(recorded.getMethod()).isEqualTo("POST");
        assertThat(recorded.getPath()).isEqualTo("/repos/org/repo/pulls");
        assertThat(recorded.getHeader("Authorization")).isEqualTo("Bearer ghp_token123");
        assertThat(recorded.getBody().readUtf8()).contains("\"head\":\"fix/rca-123\"");
    }

    @Test
    void createPullRequest_apiError_returnsNull() {
        mockServer.enqueue(new MockResponse().setResponseCode(422).setBody("{}"));

        PrRequest request = new PrRequest(
                "https://github.com/org/repo.git",
                "fix/rca-123", "main", "title", "body", "token");

        String url = platform.createPullRequest(request);

        assertThat(url).isNull();
    }

    @Test
    void createPullRequest_repoUrlWithTrailingSlash_handledCorrectly() throws Exception {
        mockServer.enqueue(new MockResponse()
                .setBody("{\"html_url\": \"https://github.com/org/repo/pull/1\"}")
                .setHeader("Content-Type", "application/json"));

        PrRequest request = new PrRequest(
                "https://github.com/org/repo/",
                "fix/branch", "main", "title", "body", "token");

        platform.createPullRequest(request);

        RecordedRequest recorded = mockServer.takeRequest();
        assertThat(recorded.getPath()).isEqualTo("/repos/org/repo/pulls");
    }
}
