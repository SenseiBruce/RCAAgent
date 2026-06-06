package com.rca.agent.model;

import jakarta.validation.constraints.NotBlank;
import java.util.List;

public record RcaRequest(
        @NotBlank(message = "Issue description is required")
        String issueDescription,
        String logFilePath,
        String logContent,
        String repoPath,
        String branch,
        String timeWindow
) {}
