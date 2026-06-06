package com.rca.agent.model;

import java.time.Instant;
import java.util.List;

/**
 * Response payload containing root cause analysis results.
 *
 * @param rootCause        LLM-generated explanation of the root cause
 * @param severity         issue severity (CRITICAL, HIGH, MEDIUM, LOW)
 * @param evidenceFromLogs relevant log lines supporting the root cause determination
 * @param relatedCommits   recent git commits that may be related to the issue
 * @param recommendations  suggested actions to resolve the issue
 * @param analyzedAt       timestamp when the analysis was performed
 */
public record RcaResponse(
        String rootCause,
        String severity,
        List<String> evidenceFromLogs,
        List<GitChange> relatedCommits,
        List<String> recommendations,
        Instant analyzedAt
) {
    /**
     * Represents a git commit related to the analyzed issue.
     *
     * @param commitId     short commit hash (8 characters)
     * @param author       commit author name
     * @param message      commit message summary
     * @param timestamp    when the commit was made
     * @param filesChanged list of file paths modified in this commit
     */
    public record GitChange(
            String commitId,
            String author,
            String message,
            Instant timestamp,
            List<String> filesChanged
    ) {}
}
