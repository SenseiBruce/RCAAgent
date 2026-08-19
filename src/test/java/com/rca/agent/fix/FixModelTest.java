package com.rca.agent.fix;

import static org.assertj.core.api.Assertions.assertThat;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import java.util.List;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class FixModelTest {

  private static Validator validator;

  @BeforeAll
  static void setUpValidator() {
    validator = Validation.buildDefaultValidatorFactory().getValidator();
  }

  @Test
  void fixRequest_sshGitUrl_isValidRemote() {
    FixRequest request =
        new FixRequest(
            "ssh://git@github.com/org/repo.git", "main", "NPE", List.of(), List.of(), "issue");

    assertThat(validator.validate(request)).isEmpty();
  }

  @Test
  void fixRequest_httpsAndGitAtUrls_areValid() {
    assertThat(
            validator.validate(
                new FixRequest("https://github.com/org/repo.git", "main", "NPE", null, null, null)))
        .isEmpty();
    assertThat(
            validator.validate(
                new FixRequest("git@github.com:org/repo.git", "main", "NPE", null, null, null)))
        .isEmpty();
  }

  @Test
  void fixRequest_localPath_failsRepoUrlPattern() {
    FixRequest request = new FixRequest("/tmp/local-repo", "main", "NPE", null, null, null);

    assertThat(validator.validate(request))
        .anyMatch(v -> v.getPropertyPath().toString().equals("repoUrl"));
  }

  @Test
  void fixRequest_recordAccessors() {
    var snippets = List.of(new FixRequest.CodeSnippetRef("App.java", 10, "code"));
    FixRequest request =
        new FixRequest(
            "https://github.com/org/repo",
            "main",
            "NPE",
            List.of("fix it"),
            snippets,
            "issue desc");

    assertThat(request.repoUrl()).isEqualTo("https://github.com/org/repo");
    assertThat(request.branch()).isEqualTo("main");
    assertThat(request.rootCause()).isEqualTo("NPE");
    assertThat(request.recommendations()).containsExactly("fix it");
    assertThat(request.codeSnippets()).hasSize(1);
    assertThat(request.codeSnippets().get(0).filePath()).isEqualTo("App.java");
    assertThat(request.codeSnippets().get(0).lineNumber()).isEqualTo(10);
    assertThat(request.codeSnippets().get(0).snippet()).isEqualTo("code");
    assertThat(request.issueDescription()).isEqualTo("issue desc");
  }

  @Test
  void fixResponse_recordAccessors() {
    FixResponse response =
        new FixResponse(
            "https://github.com/org/repo/pull/1",
            "fix/rca-123",
            List.of("File.java", "FileTest.java"),
            "Fixed NPE");

    assertThat(response.pullRequestUrl()).isEqualTo("https://github.com/org/repo/pull/1");
    assertThat(response.branchName()).isEqualTo("fix/rca-123");
    assertThat(response.filesChanged()).containsExactly("File.java", "FileTest.java");
    assertThat(response.summary()).isEqualTo("Fixed NPE");
  }

  @Test
  void fixResponse_nullFields() {
    FixResponse response = new FixResponse(null, null, List.of(), "failed");

    assertThat(response.pullRequestUrl()).isNull();
    assertThat(response.branchName()).isNull();
    assertThat(response.filesChanged()).isEmpty();
  }

  @Test
  void fixRequest_nullOptionalFields() {
    FixRequest request =
        new FixRequest("https://github.com/org/repo", null, "root cause", null, null, null);

    assertThat(request.branch()).isNull();
    assertThat(request.recommendations()).isNull();
    assertThat(request.codeSnippets()).isNull();
    assertThat(request.issueDescription()).isNull();
  }
}
