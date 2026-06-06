package com.rca.agent.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

/**
 * Request payload for root cause analysis.
 *
 * @param issueDescription description of the issue to analyze (required)
 * @param logFilePath      absolute path to a log file on disk (optional, alternative to logContent)
 * @param logContent       raw log content pasted directly (optional, alternative to logFilePath)
 * @param repoPath         absolute path to a local git repository for commit analysis (optional)
 * @param branch           git branch to analyze (defaults to configured default branch)
 * @param timeWindow       time window filter for analysis (e.g., "last 2h") (optional)
 */
public record RcaRequest(
        @NotBlank(message = "Issue description is required")
        String issueDescription,
        String logFilePath,
        String logContent,
        String repoPath,
        String branch,
        String timeWindow
) {}
