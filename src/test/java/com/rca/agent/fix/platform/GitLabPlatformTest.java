package com.rca.agent.fix.platform;

import static org.assertj.core.api.Assertions.assertThat;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

class GitLabPlatformTest {

  private GitLabPlatform platform;
  private MockWebServer mockServer;

  @BeforeEach
  void setUp() throws Exception {
    mockServer = new MockWebServer();
    mockServer.start();
    WebClient testClient = WebClient.builder().baseUrl(mockServer.url("/").toString()).build();
    platform = new GitLabPlatform(testClient);
  }

  @AfterEach
  void tearDown() throws Exception {
    mockServer.shutdown();
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
  void createPullRequest_success_returnsMrUrl() throws Exception {
    mockServer.enqueue(
        new MockResponse()
            .setBody("{\"web_url\": \"https://gitlab.com/org/repo/-/merge_requests/7\"}")
            .setHeader("Content-Type", "application/json"));

    PrRequest request =
        new PrRequest(
            "https://gitlab.com/org/repo.git",
            "fix/rca-123",
            "main",
            "fix: NPE",
            "MR body",
            "glpat-token");

    String url = platform.createPullRequest(request);

    assertThat(url).isEqualTo("https://gitlab.com/org/repo/-/merge_requests/7");

    RecordedRequest recorded = mockServer.takeRequest();
    assertThat(recorded.getMethod()).isEqualTo("POST");
    assertThat(recorded.getPath()).contains("merge_requests");
    assertThat(recorded.getPath()).contains("org");
    assertThat(recorded.getHeader("PRIVATE-TOKEN")).isEqualTo("glpat-token");
    assertThat(recorded.getBody().readUtf8()).contains("\"source_branch\":\"fix/rca-123\"");
  }

  @Test
  void createPullRequest_apiError_returnsNull() {
    mockServer.enqueue(new MockResponse().setResponseCode(401).setBody("{}"));

    PrRequest request =
        new PrRequest(
            "https://gitlab.com/org/repo.git", "fix/branch", "main", "title", "body", "bad-token");

    assertThat(platform.createPullRequest(request)).isNull();
  }
}
