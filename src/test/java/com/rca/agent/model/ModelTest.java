package com.rca.agent.model;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ModelTest {

    @Test
    void rcaRequest_recordAccessors() {
        RcaRequest request = new RcaRequest("issue", "/log/path", "log content", "/repo", "main", "2h");

        assertThat(request.issueDescription()).isEqualTo("issue");
        assertThat(request.logFilePath()).isEqualTo("/log/path");
        assertThat(request.logContent()).isEqualTo("log content");
        assertThat(request.repoPath()).isEqualTo("/repo");
        assertThat(request.branch()).isEqualTo("main");
        assertThat(request.timeWindow()).isEqualTo("2h");
    }

    @Test
    void rcaRequest_nullableFields() {
        RcaRequest request = new RcaRequest("issue", null, null, null, null, null);

        assertThat(request.issueDescription()).isEqualTo("issue");
        assertThat(request.logFilePath()).isNull();
        assertThat(request.logContent()).isNull();
        assertThat(request.repoPath()).isNull();
        assertThat(request.branch()).isNull();
        assertThat(request.timeWindow()).isNull();
    }

    @Test
    void rcaResponse_recordAccessors() {
        Instant now = Instant.now();
        var gitChange = new RcaResponse.GitChange("abc123", "dev", "fix bug", now, List.of("File.java"));
        RcaResponse response = new RcaResponse("root cause", "HIGH", List.of("log1"), List.of(), List.of(gitChange), List.of("fix"), now);

        assertThat(response.rootCause()).isEqualTo("root cause");
        assertThat(response.severity()).isEqualTo("HIGH");
        assertThat(response.evidenceFromLogs()).containsExactly("log1");
        assertThat(response.relatedCommits()).hasSize(1);
        assertThat(response.recommendations()).containsExactly("fix");
        assertThat(response.analyzedAt()).isEqualTo(now);
    }

    @Test
    void gitChange_recordAccessors() {
        Instant now = Instant.now();
        var change = new RcaResponse.GitChange("abc123", "dev", "fix", now, List.of("A.java", "B.java"));

        assertThat(change.commitId()).isEqualTo("abc123");
        assertThat(change.author()).isEqualTo("dev");
        assertThat(change.message()).isEqualTo("fix");
        assertThat(change.timestamp()).isEqualTo(now);
        assertThat(change.filesChanged()).containsExactly("A.java", "B.java");
    }

    @Test
    void rcaRequest_equality() {
        RcaRequest r1 = new RcaRequest("issue", null, null, null, null, null);
        RcaRequest r2 = new RcaRequest("issue", null, null, null, null, null);

        assertThat(r1).isEqualTo(r2);
        assertThat(r1.hashCode()).isEqualTo(r2.hashCode());
    }

    @Test
    void rcaRequest_toString() {
        RcaRequest request = new RcaRequest("test", null, null, null, null, null);
        assertThat(request.toString()).contains("test");
    }

    @Test
    void rcaResponse_equality() {
        Instant now = Instant.now();
        RcaResponse r1 = new RcaResponse("cause", "HIGH", List.of(), List.of(), List.of(), List.of(), now);
        RcaResponse r2 = new RcaResponse("cause", "HIGH", List.of(), List.of(), List.of(), List.of(), now);

        assertThat(r1).isEqualTo(r2);
    }
}
