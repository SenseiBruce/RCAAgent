package com.rca.agent.model;

import java.time.Instant;
import java.util.List;

public record RcaResponse(
        String rootCause,
        String severity,
        List<String> evidenceFromLogs,
        List<GitChange> relatedCommits,
        List<String> recommendations,
        Instant analyzedAt
) {
    public record GitChange(
            String commitId,
            String author,
            String message,
            Instant timestamp,
            List<String> filesChanged
    ) {}
}
